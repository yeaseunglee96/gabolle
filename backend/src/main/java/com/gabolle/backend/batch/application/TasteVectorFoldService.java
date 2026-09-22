package com.gabolle.backend.batch.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.preference.application.PreferenceJson;
import com.gabolle.backend.preference.domain.TasteDimension;
import com.gabolle.backend.preference.domain.UserTasteVector;
import com.gabolle.backend.preference.domain.UserTasteWeight;
import com.gabolle.backend.preference.repository.UserTasteVectorRepository;
import com.gabolle.backend.preference.repository.UserTasteWeightRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * 설문 답과 행동을 취향 벡터로 접는다.
 *
 * <p>이 클래스는 "지금" 을 읽지 않는다. 시각은 부르는 쪽이 넘겨 주는 {@code asOf} 하나뿐이다.
 * 밀린 과거 구간을 거슬러 채울 때 안에서 "지금" 을 읽으면 그 구간 밖의 데이터를 먹고, 결과는
 * 그럴듯한데 틀린다. DB 제약은 그 틀림을 못 잡는다 — 값이 있기는 하기 때문이다.
 *
 * <p>표시는 {@code received_at} 으로 옮긴다. {@code occurred_at} 을 쓰면 늦게 도착한 이벤트는
 * 표시가 이미 그 시각을 지나 있어서 영원히 안 읽힌다. 도착 시각으로 세면 늦게 온 것도 도착한
 * 구간에서 정확히 한 번 읽힌다.
 *
 * <p>읽기는 JDBC 이고 쓰기는 엔티티다. 읽는 쪽은 사람 수만큼 훑는 배치 조회라 엔티티 신원도
 * 변경 추적도 필요 없고, 그 저장소들은 {@code trip.infra} 안에서 package-private 이라 밖에서
 * 못 쓴다. 쓰는 쪽은 반드시 엔티티를 지나야 한다 — {@link UserTasteWeight#fromSurvey} 같은
 * 정적 생성자가 {@code evidence} 와 {@code support} 의 짝을 맞추는 유일한 문이어야 하고,
 * SQL 로 직접 넣으면 DB 제약까지 내려가서야 막힌다.
 */
@Service
@Profile({ "db", "dev" })
public class TasteVectorFoldService {

	private static final Logger log = LoggerFactory.getLogger(TasteVectorFoldService.class);

	/** 앞 판이 없는 사람의 표시 시작점. 처음에는 도착한 것을 전부 본다. */
	private static final OffsetDateTime BEGINNING = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

	/**
	 * 점수형 답의 성분 이름. {@code user_taste_weight} 의 키가 {@code (판, 차원, 코드)} 인데
	 * 점수형 답에는 코드가 없고, 키의 일부라 {@code NULL} 로 둘 수 없어서 이름을 하나 정했다.
	 * 온톨로지가 점수형 차원에 진짜 코드를 주면 마이그레이션 없이 이 상수만 바뀐다.
	 */
	private static final String SCORE_CODE = "SCORE";

	/**
	 * 취향 신호로 세는 이벤트 종류.
	 *
	 * <p>이 목록은 <b>세는 데만</b> 쓴다 — 「이 구간에 행동이 왔나」를 물어 판을 다시 만들지
	 * 정하는 용도다. 그 이벤트를 {@code (차원, 코드)} 성분으로 바꾸는 것은
	 * {@link BehaviorTasteFolder} 가 하고, 그쪽은 payload 모양을 아는 이벤트만 다루므로
	 * <b>이 목록보다 좁다</b>(S15P21E201-1482).
	 *
	 * <p>🔴 두 목록이 다른 것은 실수가 아니다. 여기 있고 저기 없는 종류
	 * ({@code itinerary_replace}·{@code route_skip})는 <b>아직 아무도 안 만들어서</b> payload
	 * 모양이 안 정해졌다. 세는 것은 종류 이름만 알면 되지만 귀속은 장소를 꺼내야 한다.
	 *
	 * <p>목록도 대소문자도 여기서 정하지 않고 {@link EventType} 에 맡긴다. 수집을 막는 목록과
	 * 세는 목록이 같은 파일에 있어야 둘의 포함 관계를 검사가 지킬 수 있고, 손으로 적으면
	 * 표에 실제로 들어가는 소문자와 어긋나도 아무 데서도 안 드러난다.
	 */
	private static final Set<String> TASTE_SIGNAL_EVENTS = EventType.tasteSignalWireNames();

	private final JdbcTemplate jdbc;

	private final UserTasteVectorRepository vectors;

	private final UserTasteWeightRepository weights;

	private final TasteVectorProperties properties;

	private final ObjectMapper objectMapper;

	/** 행동을 성분으로 귀속시키는 자리. 설문 접기와 분리한 이유는 그 클래스 머리말에 있다. */
	private final BehaviorTasteFolder behaviorFolder;

	public TasteVectorFoldService(JdbcTemplate jdbc, UserTasteVectorRepository vectors,
			UserTasteWeightRepository weights, TasteVectorProperties properties, ObjectMapper objectMapper,
			BehaviorTasteFolder behaviorFolder) {
		this.jdbc = jdbc;
		this.vectors = vectors;
		this.weights = weights;
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.behaviorFolder = behaviorFolder;
	}

	/**
	 * 한 사람을 접는다. 트랜잭션 단위가 사람 하나다 — 배치 전체를 묶으면 한 사람이 실패할 때
	 * 앞서 성공한 전부가 되돌아가고, 재시도해도 같은 사람에서 또 죽어 배치가 영영 안 끝난다.
	 *
	 * @param asOf 이 시각까지 도착한 것만 본다. 부르는 쪽의 실행 구간 끝
	 */
	@Transactional
	public TasteVectorFoldOutcome fold(UUID userId, OffsetDateTime asOf) {
		requireVersions();

		Optional<UserTasteVector> currentOpt = this.vectors.findByUserIdAndSupersededAtIsNull(userId);
		UserTasteVector current = currentOpt.orElse(null);

		OffsetDateTime watermark = (current == null || current.getObservedUntil() == null) ? BEGINNING
				: current.getObservedUntil();

		// 이미 이 구간까지 본 판이 있으면 아무것도 안 한다. 같은 구간을 두 번 돌려도 결과가
		// 같아야 재시도가 안전하다.
		if (current != null && !watermark.isBefore(asOf)) {
			return new TasteVectorFoldOutcome(userId, TasteVectorFoldOutcome.Action.UNCHANGED,
					current.getTasteVectorId(), current.getVersion(), 0, 0);
		}

		SurveySource survey = latestUserScopeSnapshot(userId, asOf);

		// 행동을 볼지 말지만 여기서 가른다. 설문은 그대로 접는다 — 끈 것은 "행동으로 추측하지
		// 마라" 이지 "내가 고른 것도 잊으라" 가 아니다.
		boolean behaviorAllowed = allowsBehaviorPersonalization(userId);
		int newEvents = behaviorAllowed ? countTasteSignals(userId, watermark, asOf) : 0;

		// 성분이 하나도 없는 벡터를 만들지 않는다. "취향이 없는 사람" 과 "아직 안 물어본 사람"
		// 은 다르고, 한 번 섞으면 되돌릴 수 없다.
		if (current == null && survey == null && newEvents == 0) {
			return new TasteVectorFoldOutcome(userId, TasteVectorFoldOutcome.Action.NOTHING_TO_FOLD, null, 0, 0, 0);
		}

		boolean sourceChanged = !sameSnapshot(current, survey);
		if (current != null && !sourceChanged && newEvents == 0) {
			current.advanceWatermark(asOf, 0);
			return new TasteVectorFoldOutcome(userId, TasteVectorFoldOutcome.Action.WATERMARK_ADVANCED,
					current.getTasteVectorId(), current.getVersion(), 0, 0);
		}

		return rebuild(userId, asOf, current, survey, newEvents, behaviorAllowed);
	}

	private TasteVectorFoldOutcome rebuild(UUID userId, OffsetDateTime asOf, UserTasteVector current,
			SurveySource survey, int newEvents, boolean behaviorAllowed) {

		int nextVersion = (current == null) ? 1 : current.getVersion() + 1;
		int cumulativeEvents = ((current == null) ? 0 : current.getObservedEventCount()) + newEvents;

		// 순서가 중요하다. 옛 판을 먼저 내리고 DB 까지 밀어낸 뒤에 새 판을 넣는다. 조건부
		// UNIQUE 색인이 "현재는 하나" 를 강제하므로 뒤집히면 둘이 잠깐 현재가 되어 거부당한다.
		// flush 를 빼면 Hibernate 가 INSERT 를 먼저 보낼 수 있어 실패가 재현되다 말다 한다.
		if (current != null) {
			current.supersede(asOf);
			this.vectors.saveAndFlush(current);
		}

		UserTasteVector next = UserTasteVector.open(userId, nextVersion, (survey == null) ? null : survey.snapshotId(),
				cumulativeEvents, asOf, this.properties.getVectorVersion(), this.properties.getOntologyVersion(), asOf);
		this.vectors.save(next);

		List<UserTasteWeight> fromSurvey = foldSurvey(next.getTasteVectorId(), survey, asOf);
		List<BehaviorTasteFolder.Attribution> fromBehavior = behaviorAllowed
				? this.behaviorFolder.fold(userId, asOf)
				: List.of();

		List<UserTasteWeight> folded = merge(next.getTasteVectorId(), fromSurvey, fromBehavior, asOf);
		if (!folded.isEmpty()) {
			this.weights.saveAll(folded);
		}

		log.info("취향 벡터를 접었다 user={} version={} 성분={}(설문 {} · 행동 {}) 새 행동={} asOf={}", userId, nextVersion,
				folded.size(), fromSurvey.size(), fromBehavior.size(), newEvents, asOf);

		return new TasteVectorFoldOutcome(userId, TasteVectorFoldOutcome.Action.REBUILT, next.getTasteVectorId(),
				nextVersion, folded.size(), newEvents);
	}

	/**
	 * 설문 성분과 행동 성분을 한 벌로 합친다.
	 *
	 * <h2>🔴 같은 {@code (차원, 코드)} 는 «두 행이 될 수 없다»</h2>
	 *
	 * {@code user_taste_weight} 의 PK 가 {@code (판, 차원, 코드)} 다. 두 목록을 그냥 이어 붙여
	 * 저장하면 겹치는 자리에서 한쪽이 말없이 덮어쓰거나 DB 가 거부한다. 그래서 키로 모으는
	 * 맵을 지나게 한다 — 맵 자체가 그 제약을 코드에서 다시 말해 준다.
	 *
	 * <p>겹친 자리는 {@link TasteEvidence#BLENDED} 한 행이 된다. 그것이 그 값의 사실이기도
	 * 하다 — 설문도 행동도 같은 것을 가리키고 있다.
	 *
	 * <h2>무게를 더하고 자른다</h2>
	 *
	 * 「카페를 좋아한다」고 답했는데(+1) 카페를 계속 빼고 있으면(−0.6) 합은 +0.4 다. 평균이
	 * 아니라 합인 것은, 평균이면 행동이 아무 말도 안 할 때 설문의 세기가 절반으로 깎이기
	 * 때문이다. 자르는 것은 {@code ck_user_taste_weight_range} 때문이기도 하지만, 「좋아한다」
	 * 보다 더 좋아할 수는 없어서이기도 하다.
	 *
	 * @param fromSurvey {@link #foldSurvey} 가 만든 것. {@code evidence=SURVEY}
	 * @param fromBehavior {@link BehaviorTasteFolder} 가 귀속시킨 것
	 */
	private List<UserTasteWeight> merge(UUID tasteVectorId, List<UserTasteWeight> fromSurvey,
			List<BehaviorTasteFolder.Attribution> fromBehavior, OffsetDateTime asOf) {

		Map<String, UserTasteWeight> byKey = new LinkedHashMap<>();
		for (UserTasteWeight weight : fromSurvey) {
			byKey.put(weightKey(weight.getDimension(), weight.getCode()), weight);
		}

		for (BehaviorTasteFolder.Attribution attribution : fromBehavior) {
			String key = weightKey(attribution.dimension(), attribution.code());
			UserTasteWeight surveyWeight = byKey.get(key);
			if (surveyWeight == null) {
				byKey.put(key, UserTasteWeight.fromInteraction(tasteVectorId, attribution.dimension(),
						attribution.code(), attribution.weight(), attribution.support(), asOf));
				continue;
			}
			byKey.put(key, UserTasteWeight.blended(tasteVectorId, attribution.dimension(), attribution.code(),
					clampWeight(surveyWeight.getWeight() + attribution.weight()), attribution.support(), asOf));
		}
		return List.copyOf(byKey.values());
	}

	/** 열거형과 문자열을 한 키로 — {@code (차원, 코드)} 가 같으면 같은 성분이다. */
	private static String weightKey(TasteDimension dimension, String code) {
		return dimension.name() + ' ' + code;
	}

	private static double clampWeight(double value) {
		return Math.max(-1.0, Math.min(1.0, value));
	}

	/**
	 * 설문 답을 성분으로 바꾼다.
	 *
	 * <p>고른 답만 성분이 된다. 건너뜀·모름은 행을 만들지 않는다 — 0 은 "좋지도 싫지도 않다" 는
	 * 의견이고 없음은 "안 물어봤다" 는 무지다. 한 번 0 으로 적으면 둘을 영영 구분할 수 없고,
	 * 그때부터 추천은 물어본 적도 없이 그것을 근거로 후보를 뺀다.
	 *
	 * <p>차원 이름이 아니라 값의 모양으로 태그형과 점수형을 가른다. 어느 차원이 어느 쪽인지는
	 * 화면 계약이 아직 확정하지 않았고, 목록을 여기 박으면 그 목록이 틀린 날 조용히 성분이 사라진다.
	 *
	 * <p>파싱과 눈금은 {@link PreferenceJson} 에 맡긴다. 규칙이 한 벌이어야 채점기가 보는 취향과
	 * 벡터가 접는 취향이 안 갈린다 — 갈리면 오류 없이 모든 답이 같은 무게로 접힌다.
	 */
	private List<UserTasteWeight> foldSurvey(UUID tasteVectorId, SurveySource survey, OffsetDateTime asOf) {
		List<UserTasteWeight> result = new ArrayList<>();
		if (survey == null) {
			return result;
		}

		for (SurveyAnswer answer : survey.answers()) {
			TasteDimension dimension = parseDimension(answer.dimension());
			if (dimension == null) {
				// 모르는 차원은 조용히 버리지 않고 남긴다. DB CHECK 목록과 이 열거형이 어긋난
				// 상태이고, 그건 마이그레이션이 필요하다는 신호다.
				log.warn("모르는 취향 차원이라 성분으로 접지 못했다 dimension={} snapshot={}", answer.dimension(), survey.snapshotId());
				continue;
			}

			List<String> codes = PreferenceJson.parseCodes(answer.valueJson(), this.objectMapper);
			if (!codes.isEmpty()) {
				for (String code : codes) {
					// 고른 태그는 +1 이다. 사람이 직접 고른 것이라 뒷받침 수가 필요 없다.
					result.add(UserTasteWeight.fromSurvey(tasteVectorId, dimension, code, 1.0, asOf));
				}
				continue;
			}

			// 여기 오는 값은 PreferenceJson 이 이미 0~1 로 맞춰 준 것이다 — 1~5 슬라이더는
			// (raw-1)/4 로 옮겨진다. toWeight 가 0~1 을 받는다는 전제가 그래서 성립한다.
			Double score = PreferenceJson.parseScore(answer.valueJson(), this.objectMapper);
			if (score != null) {
				result.add(UserTasteWeight.fromSurvey(tasteVectorId, dimension, SCORE_CODE, toWeight(score), asOf));
			}
		}
		return result;
	}

	/**
	 * 0~1 점수를 -1~+1 무게로 옮긴다.
	 *
	 * <p>점수형 답은 "얼마나 원하는가" 를 0~1 로 준다. 무게는 싫음까지 표현하므로
	 * 0.5(중립)가 0 이 되도록 늘린다. 범위를 벗어난 값은 잘라 낸다 —
	 * {@code ck_user_taste_weight_range} 가 어차피 거부하지만, DB 까지 내려가서 죽는 것보다
	 * 여기서 맞추는 편이 낫다.
	 */
	private static double toWeight(double score) {
		return Math.max(-1.0, Math.min(1.0, (score * 2.0) - 1.0));
	}

	private void requireVersions() {
		if (this.properties.getVectorVersion().isEmpty() || this.properties.getOntologyVersion().isEmpty()) {
			throw new IllegalStateException("gabolle.taste-vector.vector-version / ontology-version 이 비어 있다. "
					+ "판 번호 없이 만든 벡터는 다른 벡터와 비교할 수 없어서, 기본값을 넣지 않고 실패시킨다");
		}
	}

	private static boolean sameSnapshot(UserTasteVector current, SurveySource survey) {
		UUID was = (current == null) ? null : current.getSourcePreferenceSnapshotId();
		UUID now = (survey == null) ? null : survey.snapshotId();
		return Objects.equals(was, now);
	}

	/**
	 * 계정 기본 설문의 가장 최근 판. {@code asOf} 뒤에 낸 답은 안 본다.
	 *
	 * <p>{@code scope='TRIP'} 은 제외한다 — 여행에서 고친 취향이 계정 기본값을 덮어쓰면 안 된다.
	 * DB 에서 그 표현이 {@code trip_id IS NULL} 이다.
	 */
	private SurveySource latestUserScopeSnapshot(UUID userId, OffsetDateTime asOf) {
		String snapshotSql = "SELECT preference_snapshot_id, version FROM preference_snapshot "
				+ "WHERE user_id = ? AND trip_id IS NULL AND scope = 'USER' AND created_at <= ? "
				+ "ORDER BY version DESC LIMIT 1";

		List<UUID> found = this.jdbc.query(snapshotSql,
				(rs, i) -> rs.getObject("preference_snapshot_id", UUID.class), userId, asOf);
		if (found.isEmpty()) {
			return null;
		}
		UUID snapshotId = found.get(0);

		// 고른 답만 가져온다. 건너뜀·모름은 애초에 value 가 NULL 이다.
		String answerSql = "SELECT dimension, value::text AS value_json FROM preference_answer "
				+ "WHERE preference_snapshot_id = ? AND answer_status = 'SELECTED' ORDER BY dimension";

		List<SurveyAnswer> answers = this.jdbc.query(answerSql,
				(rs, i) -> new SurveyAnswer(rs.getString("dimension"), rs.getString("value_json")), snapshotId);

		return new SurveySource(snapshotId, answers);
	}

	/**
	 * 이 사람의 행동을 개인화 입력으로 써도 되는가.
	 *
	 * <p>입구가 이미 행동 이벤트를 안 받는데 여기서 또 보는 것은 표에 남아 있는 과거 때문이다.
	 * 이 배치는 지난 구간을 거슬러 채우므로, 입구를 막기 전에 쌓인 행동이 backfill 때 다시
	 * 벡터로 접힌다 — 개인화를 끈 사람의 프로필이 배치가 도는 시간에 되살아나고 아무도 안 본다.
	 *
	 * <p>계정을 못 찾으면 안 보는 쪽이다. 입구와 같은 규칙이다.
	 */
	private boolean allowsBehaviorPersonalization(UUID userId) {
		String sql = "SELECT count(*) FROM app_user WHERE user_id = ? AND personalization_mode = 'BEHAVIOR_ENABLED'";
		Integer enabled = this.jdbc.queryForObject(sql, Integer.class, userId);
		return enabled != null && enabled > 0;
	}

	/**
	 * 표시 이후 도착한 취향 신호의 수.
	 *
	 * <p>구간은 {@code (watermark, asOf]} — 왼쪽은 열고 오른쪽은 닫는다. 그래야 구간을 이어
	 * 붙일 때 경계의 한 건이 두 구간에 다 들리거나 어느 구간에도 안 들리는 일이 없다.
	 *
	 * <p>이 메서드 자체는 동의를 보지 않는다. 부르기 전에
	 * {@link #allowsBehaviorPersonalization(UUID)} 를 통과해야 한다.
	 */
	private int countTasteSignals(UUID userId, OffsetDateTime watermark, OffsetDateTime asOf) {
		// `= ANY (?)` 에 자바 String[] 을 그대로 넘기면 컴파일은 되지만 실행할 때 타입을 못 정해
		// 죽는다. string_to_array 로 DB 쪽에서 배열을 만들면 파라미터가 문자열 하나라 그 문제가
		// 없다. 이벤트 종류 이름은 열거형 이름이라 쉼표가 없어서 쉼표로 이어도 안전하다.
		String sql = "SELECT count(*) FROM event_outbox WHERE user_id = ? "
				+ "AND received_at > ? AND received_at <= ? AND event_type = ANY (string_to_array(?, ','))";
		Integer count = this.jdbc.queryForObject(sql, Integer.class, userId, watermark, asOf,
				String.join(",", TASTE_SIGNAL_EVENTS));
		return (count == null) ? 0 : count;
	}

	private static TasteDimension parseDimension(String raw) {
		if (raw == null) {
			return null;
		}
		try {
			return TasteDimension.valueOf(raw);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	/** 한 판의 고른 답 묶음. */
	private record SurveySource(UUID snapshotId, List<SurveyAnswer> answers) {
	}

	/** 고른 답 하나. */
	private record SurveyAnswer(String dimension, String valueJson) {
	}
}
