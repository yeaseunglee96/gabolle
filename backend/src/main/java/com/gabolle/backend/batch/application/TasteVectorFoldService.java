package com.gabolle.backend.batch.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.preference.domain.TasteDimension;
import com.gabolle.backend.preference.domain.UserTasteVector;
import com.gabolle.backend.preference.domain.UserTasteWeight;
import com.gabolle.backend.preference.repository.UserTasteVectorRepository;
import com.gabolle.backend.preference.repository.UserTasteWeightRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 설문 답과 행동을 취향 벡터로 접는다 — MLOps Phase 1.
 *
 * <p>{@link UserTasteVector} 의 javadoc 이 <i>"그 순서를 지키는 코드는 벡터를 실제로 계산하는
 * 쪽의 몫이고 이 티켓 범위가 아니다"</i> 라고 남겨 둔 그 자리다.
 *
 * <h2>🔴 시각은 하나다 — {@code asOf} 만 쓴다</h2>
 *
 * 이 클래스는 "지금" 을 읽지 않는다. 부르는 쪽(Airflow)이 자기 실행 구간의 끝을 넘겨 준다.
 * 이유는 <b>backfill</b>(밀린 과거 구간을 거슬러 채우는 것)이다. 8월 1일 구간을 채우는데
 * 안에서 "지금" 을 읽으면 9월의 데이터를 먹고, 결과는 그럴듯한데 틀린다. 그리고
 * <b>DB 제약이 그 틀림을 못 잡는다</b> — 값이 있기는 하기 때문이다.
 *
 * <h2>🔴 표시를 {@code received_at} 으로 옮긴다 — {@code occurred_at} 이 아니다</h2>
 *
 * 이벤트에는 시각이 둘이다. {@code occurred_at}(실제로 일어난 때)과
 * {@code received_at}(서버가 받은 때). 클라이언트가 만드는 이벤트는 <b>늦게 도착한다</b> —
 * 비행기 모드였다가 켜면 어제 일이 오늘 들어온다.
 *
 * <p>표시를 {@code occurred_at} 으로 두면 그 늦은 이벤트는 <b>영원히 안 읽힌다.</b> 표시가
 * 이미 그 시각을 지나 있기 때문이다. 우리가 볼 수 있는 것은 도착한 것뿐이므로 표시도 도착
 * 시각으로 센다. 그러면 늦게 온 이벤트는 <b>도착한 구간</b>에서 정확히 한 번 읽힌다.
 *
 * <h2>왜 읽기는 JDBC 이고 쓰기는 엔티티인가</h2>
 *
 * 읽는 쪽({@code preference_snapshot} · {@code preference_answer} · {@code event_outbox})은
 * 사람 수만큼 훑는 배치 조회다. 엔티티 신원도 변경 추적도 필요 없다. 게다가 그 저장소들은
 * {@code trip.infra} 안에서 package-private 이라 밖에서 못 쓴다.
 *
 * <p>쓰는 쪽은 반드시 엔티티를 지난다. {@link UserTasteWeight#fromSurvey} 같은 정적 생성자가
 * {@code evidence} 와 {@code support} 의 짝을 맞추는 <b>유일한 문</b>이어야 하기 때문이다.
 * SQL 로 직접 넣으면 DB 제약({@code ck_user_taste_weight_interaction_has_support})까지
 * 내려가서야 막히고, 그때는 어느 코드가 그랬는지 알기 어렵다.
 */
@Service
@Profile({ "db", "dev" })
public class TasteVectorFoldService {

	private static final Logger log = LoggerFactory.getLogger(TasteVectorFoldService.class);

	/** 앞 판이 없는 사람의 표시 시작점. 처음에는 도착한 것을 전부 본다. */
	private static final OffsetDateTime BEGINNING = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

	/**
	 * 점수형 답의 성분 이름.
	 *
	 * <p>🔴 이 값은 <b>이 티켓이 정한 것</b>이다. {@code user_taste_weight} 의 키는
	 * {@code (판, 차원, 코드)} 인데, 태그형 답은 코드가 있고 점수형 답은 없다. 없는 것을
	 * {@code NULL} 로 둘 수 없으니(키의 일부다) 이름을 하나 정해야 한다.
	 *
	 * <p>차원 이름을 그대로 쓰는 것도 후보였지만 같은 값이 두 칸에 겹쳐 보여서 골랐다는
	 * 느낌이 안 난다. 온톨로지가 점수형 차원에 진짜 코드를 주면 그때 마이그레이션 없이
	 * 이 상수만 바뀐다.
	 */
	private static final String SCORE_CODE = "SCORE";

	/**
	 * 취향 신호로 세는 이벤트 종류.
	 *
	 * <p>🔴 <b>지금 이 중 어느 것도 계측되지 않는다.</b> {@code EventType.requiredForM1()} 이
	 * 전부 {@code false} 라서 아무도 안 만든다. 그래서 실제로 세어지는 값은 0 이고, 벡터는
	 * 당분간 {@code evidence=SURVEY} 만으로 만들어진다.
	 *
	 * <p>그 사실을 숨기지 않고 목록을 미리 적어 두는 이유는, 계측이 붙는 날 이 배치를
	 * 고치지 않아도 되게 하려는 것이다. 그리고 <b>세는 것과 성분으로 바꾸는 것은 다른 일</b>
	 * 이다 — 세는 것은 지금 맞게 돌고(표시가 정확히 움직인다), 장소를 차원·코드로 바꾸는
	 * 대조({@code user_place_code_map})는 계측된 이벤트가 생긴 뒤에 붙인다.
	 * 지어낸 대조를 지금 넣으면 그 값이 계약처럼 굳는다.
	 */
	private static final String[] TASTE_SIGNAL_EVENTS = { "PLACE_LIKE", "PLACE_DISLIKE", "PLACE_VISIT", "PLACE_VIEW",
			"ITINERARY_REMOVE", "ITINERARY_REPLACE", "ROUTE_SKIP" };

	private final JdbcTemplate jdbc;

	private final UserTasteVectorRepository vectors;

	private final UserTasteWeightRepository weights;

	private final TasteVectorProperties properties;

	private final ObjectMapper objectMapper;

	public TasteVectorFoldService(JdbcTemplate jdbc, UserTasteVectorRepository vectors,
			UserTasteWeightRepository weights, TasteVectorProperties properties, ObjectMapper objectMapper) {
		this.jdbc = jdbc;
		this.vectors = vectors;
		this.weights = weights;
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	/**
	 * 한 사람을 접는다.
	 *
	 * <p>🔴 트랜잭션이 <b>사람 하나</b>다. 배치 전체를 한 트랜잭션으로 묶으면 한 사람이
	 * 실패할 때 앞서 성공한 수천 명이 같이 되돌아간다. 그리고 그건 재시도해도 같은 사람에서
	 * 또 죽으므로 배치가 영영 안 끝난다.
	 *
	 * @param userId 접을 사람
	 * @param asOf 이 시각까지 도착한 것만 본다. 부르는 쪽의 실행 구간 끝
	 */
	@Transactional
	public TasteVectorFoldOutcome fold(UUID userId, OffsetDateTime asOf) {
		requireVersions();

		Optional<UserTasteVector> currentOpt = this.vectors.findByUserIdAndSupersededAtIsNull(userId);
		UserTasteVector current = currentOpt.orElse(null);

		OffsetDateTime watermark = (current == null || current.getObservedUntil() == null) ? BEGINNING
				: current.getObservedUntil();

		// 🔴 이미 이 구간까지 본 판이 있으면 아무것도 안 한다. 같은 구간을 두 번 돌려도
		//    결과가 같아야 하고(멱등), 그것이 재시도를 안전하게 만든다.
		if (current != null && !watermark.isBefore(asOf)) {
			return new TasteVectorFoldOutcome(userId, TasteVectorFoldOutcome.Action.UNCHANGED,
					current.getTasteVectorId(), current.getVersion(), 0, 0);
		}

		SurveySource survey = latestUserScopeSnapshot(userId, asOf);
		int newEvents = countTasteSignals(userId, watermark, asOf);

		// 🔴 성분이 하나도 없는 벡터를 만들지 않는다. "취향이 없는 사람" 과
		//    "아직 안 물어본 사람" 은 다르고, 한 번 섞으면 되돌릴 수 없다.
		if (current == null && survey == null && newEvents == 0) {
			return new TasteVectorFoldOutcome(userId, TasteVectorFoldOutcome.Action.NOTHING_TO_FOLD, null, 0, 0, 0);
		}

		boolean sourceChanged = !sameSnapshot(current, survey);
		if (current != null && !sourceChanged && newEvents == 0) {
			current.advanceWatermark(asOf, 0);
			return new TasteVectorFoldOutcome(userId, TasteVectorFoldOutcome.Action.WATERMARK_ADVANCED,
					current.getTasteVectorId(), current.getVersion(), 0, 0);
		}

		return rebuild(userId, asOf, current, survey, newEvents);
	}

	private TasteVectorFoldOutcome rebuild(UUID userId, OffsetDateTime asOf, UserTasteVector current,
			SurveySource survey, int newEvents) {

		int nextVersion = (current == null) ? 1 : current.getVersion() + 1;
		int cumulativeEvents = ((current == null) ? 0 : current.getObservedEventCount()) + newEvents;

		// 🔴 순서가 중요하다. 옛 판을 먼저 내리고 **DB 까지 밀어낸 뒤**에 새 판을 넣는다.
		//    조건부 UNIQUE 색인(uq_user_taste_vector_current)이 "현재는 하나" 를 DB 에서
		//    막으므로 순서가 뒤집히면 둘이 잠깐 현재가 되어 거부당한다. flush 를 빼면
		//    Hibernate 가 INSERT 를 먼저 보낼 수 있어서 실패가 재현되다 말다 한다.
		if (current != null) {
			current.supersede(asOf);
			this.vectors.saveAndFlush(current);
		}

		UserTasteVector next = UserTasteVector.open(userId, nextVersion, (survey == null) ? null : survey.snapshotId(),
				cumulativeEvents, asOf, this.properties.getVectorVersion(), this.properties.getOntologyVersion(), asOf);
		this.vectors.save(next);

		List<UserTasteWeight> folded = foldSurvey(next.getTasteVectorId(), survey, asOf);
		if (!folded.isEmpty()) {
			this.weights.saveAll(folded);
		}

		log.info("취향 벡터를 접었다 user={} version={} 성분={} 새 행동={} asOf={}", userId, nextVersion, folded.size(), newEvents,
				asOf);

		return new TasteVectorFoldOutcome(userId, TasteVectorFoldOutcome.Action.REBUILT, next.getTasteVectorId(),
				nextVersion, folded.size(), newEvents);
	}

	/**
	 * 설문 답을 성분으로 바꾼다.
	 *
	 * <p>🔴 <b>고른 답만 성분이 된다.</b> 건너뜀({@code SKIPPED})·모름({@code UNKNOWN})은
	 * 행을 만들지 않는다 — 0 을 넣지 않는다. 0 은 "좋지도 싫지도 않다" 는 <b>의견</b>이고
	 * 없음은 "안 물어봤다" 는 <b>무지</b>다. 한 번 0 으로 적으면 둘을 영영 구분할 수 없고,
	 * 그때부터 추천은 물어본 적도 없이 "이 사람은 카페에 관심 없다" 를 근거로 카페를 뺀다.
	 *
	 * <p>모양은 두 가지고 <b>차원 이름으로 가르지 않고 값의 모양으로 가른다.</b> 어떤 차원이
	 * 태그형이고 어떤 것이 점수형인지는 화면 계약이 아직 확정하지 않았다
	 * ({@code PreferenceJson} 의 같은 문제). 목록을 여기 박으면 그 목록이 틀린 날 조용히
	 * 성분이 사라진다.
	 */
	private List<UserTasteWeight> foldSurvey(UUID tasteVectorId, SurveySource survey, OffsetDateTime asOf) {
		List<UserTasteWeight> result = new ArrayList<>();
		if (survey == null) {
			return result;
		}

		for (SurveyAnswer answer : survey.answers()) {
			TasteDimension dimension = parseDimension(answer.dimension());
			if (dimension == null) {
				// 🔴 모르는 차원은 조용히 버리지 않고 남긴다. DB CHECK 목록과 이 열거형이
				//    어긋난 상태이고, 그건 마이그레이션이 필요하다는 신호다.
				log.warn("모르는 취향 차원이라 성분으로 접지 못했다 dimension={} snapshot={}", answer.dimension(), survey.snapshotId());
				continue;
			}
			JsonNode value = readTree(answer.valueJson());
			if (value == null) {
				continue;
			}

			JsonNode codes = value.path("codes");
			if (codes.isArray() && !codes.isEmpty()) {
				for (JsonNode code : codes) {
					String text = code.asText(null);
					if (text != null && !text.isBlank()) {
						// 고른 태그는 +1 이다. 사람이 직접 고른 것이라 뒷받침 수가 필요 없다.
						result.add(UserTasteWeight.fromSurvey(tasteVectorId, dimension, text.trim(), 1.0, asOf));
					}
				}
				continue;
			}

			Double score = readScore(value);
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
	 * <p>🔴 {@code scope='TRIP'} 은 제외한다. 여행에서 고친 취향이 계정 기본값을 덮어쓰면
	 * 안 된다 (수집 명세 2.2). 그 표현이 DB 에서는 {@code trip_id IS NULL} 이다.
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

		// 🔴 고른 답만 가져온다. 건너뜀·모름은 애초에 value 가 NULL 이다
		//    (ck_preference_answer_value_matches_status 가 그것을 DB 에서 강제한다).
		String answerSql = "SELECT dimension, value::text AS value_json FROM preference_answer "
				+ "WHERE preference_snapshot_id = ? AND answer_status = 'SELECTED' ORDER BY dimension";

		List<SurveyAnswer> answers = this.jdbc.query(answerSql,
				(rs, i) -> new SurveyAnswer(rs.getString("dimension"), rs.getString("value_json")), snapshotId);

		return new SurveySource(snapshotId, answers);
	}

	/**
	 * 표시 이후 <b>도착한</b> 취향 신호의 수.
	 *
	 * <p>구간은 {@code (watermark, asOf]} — 왼쪽은 열고 오른쪽은 닫는다. 그래야 구간을 이어
	 * 붙일 때 경계의 한 건이 두 구간에 다 들리거나 어느 구간에도 안 들리는 일이 없다.
	 */
	private int countTasteSignals(UUID userId, OffsetDateTime watermark, OffsetDateTime asOf) {
		// 🔴 `= ANY (?)` 에 자바 String[] 을 그대로 넘기지 않는다. 그러려면 java.sql.Array 로
		//    바꿔야 하고, 안 바꾸면 컴파일은 되지만 실행할 때 타입을 못 정해서 죽는다.
		//    string_to_array 로 DB 쪽에서 배열을 만들면 파라미터가 문자열 하나라 그 문제가 없다.
		//    이벤트 종류 이름에는 쉼표가 없다(열거형 이름이다) — 그래서 쉼표로 이어도 안전하다.
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

	private JsonNode readTree(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return this.objectMapper.readTree(json);
		}
		catch (RuntimeException ex) {
			log.warn("취향 답의 값을 읽지 못했다 — 성분으로 접지 않는다. value={}", json, ex);
			return null;
		}
	}

	private static Double readScore(JsonNode value) {
		if (value.isNumber()) {
			return value.doubleValue();
		}
		JsonNode score = value.path("score");
		return score.isNumber() ? score.doubleValue() : null;
	}

	/** 한 판의 고른 답 묶음. */
	private record SurveySource(UUID snapshotId, List<SurveyAnswer> answers) {
	}

	/** 고른 답 하나. */
	private record SurveyAnswer(String dimension, String valueJson) {
	}
}
