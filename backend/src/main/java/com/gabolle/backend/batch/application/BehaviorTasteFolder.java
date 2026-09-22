package com.gabolle.backend.batch.application;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.gabolle.backend.preference.domain.TasteDimension;

/**
 * 행동 이벤트를 취향 성분 {@code (차원, 코드)} 으로 귀속시킨다.
 *
 * <h2>빠져 있던 단계가 이것이다</h2>
 *
 * {@link TasteVectorFoldService} 는 취향 신호를 <b>개수로만</b> 셌다 — 세어서
 * {@code observedEventCount} 에 더하고 끝이었다. 그래서 하트를 백 번 눌러도
 * {@code user_taste_weight} 에는 설문 성분만 있었다 (S15P21E201-1482).
 *
 * <h2>조인은 이미 다 있었다</h2>
 *
 * <pre>
 * event_outbox.payload ->> 'placeId'
 *    └─▶ place_feature (feature_type, feature_key)
 *           └─▶ user_place_code_map (place_feature_type → user_input_code = 차원)
 * </pre>
 *
 * <p>차원 목록을 코드에 안 박는다. 대조표가 <b>어느 차원이 태그형인지까지</b> 들고 있어서
 * ({@code match_kind='TAG_OVERLAP'}) 그 조건 하나로 고른다. 목록을 여기 적으면 대조표에 차원을
 * 더한 날 이쪽이 조용히 안 따라간다.
 *
 * <h2>🔴 점수형({@code SCORE_COMPARE})은 일부러 뺀다</h2>
 *
 * 「조용한 집을 좋아했다」에서 「조용함을 좋아한다」를 끌어내려면 좋아한 장소들의 점수
 * <b>평균</b>을 내야 하는데, 그 평균은 취향이 아니라 <b>주변 지리</b>를 반영한다 — 해운대에서만
 * 고르면 관광객 비율이 저절로 높다. 편향 보정을 정하기 전에는 안 하는 편이 낫다.
 *
 * <h2>🔴 구간의 왼쪽을 안 자른다</h2>
 *
 * 세는 것({@code countTasteSignals})은 「이 구간에 몇 건 왔나」라 워터마크로 왼쪽을 자르지만,
 * 귀속은 <b>누적</b>이어야 한다. 어제 누른 좋아요가 오늘 구간에 없다고 성분이 사라지면 벡터가
 * 날마다 깜빡인다.
 *
 * <h2>🔴 지금 창은 «없다» — 90일이 아니다 (2026-09-22 정정)</h2>
 *
 * 여기 「실질적인 창은 이벤트 보관 기간(90일)」이라고 적혀 있었다. <b>거짓이었다.</b>
 * {@code PrivacyCleanupService} 는 {@code publishedAt IS NOT NULL} 인 이벤트만 지우는데,
 * {@code OutboxRelayService.relayOnce()} 를 부르는 곳이 운영 코드에 <b>한 군데도 없었다</b> —
 * 그래서 {@code published_at} 이 늘 {@code NULL} 이었고 <b>아무것도 지워진 적이 없다</b>
 * (S15P21E201-561 이 스케줄러를 만들었지만 기본값은 꺼짐이다).
 *
 * <p>즉 지금 이 귀속은 <b>사용자의 전 이력</b>을 본다. 3개월 전 하트와 어제 하트가 같은 무게다.
 * 릴레이를 켜는 날 비로소 90일 창이 생긴다 — 그때 취향이 서서히 옅어지기 시작하므로,
 * <b>켜는 것과 「왜 옛날 취향이 사라지지」는 같은 사건이다.</b> 감쇠를 따로 안 넣은 것은
 * 그 결합을 먼저 정해야 하기 때문이다.
 */
@Component
@Profile({ "db", "dev" })
public class BehaviorTasteFolder {

	private static final Logger log = LoggerFactory.getLogger(BehaviorTasteFolder.class);

	/**
	 * 한 {@code (차원, 코드)} 에 행동이 남긴 것.
	 *
	 * @param weight {@code -1}(싫다) ~ {@code +1}(좋다)
	 * @param support 이 값을 뒷받침한 관측 수
	 */
	public record Attribution(TasteDimension dimension, String code, double weight, int support) {
	}

	/**
	 * 이벤트 하나가 그 장소의 태그 쪽으로 미는 힘.
	 *
	 * <p>🔴 {@code place_view} 가 작은 것은 <b>「목록 맨 위」가 곧 취향이 되는 것을 막기</b>
	 * 위해서다. 사람은 위에 있는 것을 더 보고, 위에 있는 이유는 지금 추천이 그렇게 정했기
	 * 때문이다. 크게 주면 추천이 자기가 고른 것을 근거로 자기를 강화한다.
	 *
	 * <p>🔴 {@code itinerary_remove} 가 <b>약한</b> 부정인 것은 일정에서 빼는 이유가 「싫어서」만이
	 * 아니기 때문이다 — 문 닫았고, 비 오고, 시간이 없다. 운영 사유가 적힌 것은 아예 안 세고
	 * (질의의 {@code operational_reason} 조건), 안 적힌 것도 확신하지 않는다.
	 *
	 * <p>여기 없는 취향 신호({@code itinerary_replace}·{@code route_skip})는 <b>아직 아무도 안
	 * 만들어서 payload 모양이 안 정해졌다.</b> 모양을 모르는 채로 기여값을 적으면 그것이 계약이
	 * 된다 — 만드는 쪽이 정해지면 그때 한 줄씩 더한다.
	 */
	private static final Map<String, Double> CONTRIBUTION = Map.of(
			"place_like", 1.0,
			"place_visit", 0.5,
			"place_view", 0.1,
			"place_dislike", -1.0,
			"itinerary_remove", -0.5);

	/**
	 * payload 에 장소가 하나 실리고, <b>같은 장소를 두 번 세면 안 되는</b> 이벤트.
	 *
	 * <h2>🔴 하트 한 번이 이벤트 두 건이다</h2>
	 *
	 * 앱이 하트를 켜면 <b>두 경로가 각자 적는다</b> — 저장 API 가 서버에서
	 * ({@code SavedPlaceService.recordLike}), 앱이 분석 이벤트로 한 번 더. {@code eventId} 가
	 * 달라 Outbox 멱등도 안 걸린다. 그래서 한 번 누른 하트가 「두 번 관측」이 되어
	 * {@link #MIN_SUPPORT} 가드가 무력화된다 (S15P21E201-1485).
	 *
	 * <p>그런데 이 {@code DISTINCT} 는 그 중복을 덮는 반창고가 <b>아니다.</b> 하트는 켜짐/꺼짐
	 * 이라 애초에 「두 번 켠 상태」가 없다 — {@code SavedPlaceService} 주석이 같은 말을 한다.
	 * 같은 장소를 두 번 좋아할 수는 없으므로 한 번으로 세는 것이 <b>옳은 의미</b>이고,
	 * 앱 쪽 중복을 걷어내도 이 조건은 그대로 있어야 한다 — 옛 앱 판이 한참 계속 보낸다.
	 *
	 * <p>{@code place_dislike} 도 같다. 의견은 상태이지 반복하는 행동이 아니다.
	 *
	 * <h2>🔴 그래서 «마지막 것» 만 본다 (S15P21E201-1506)</h2>
	 *
	 * 상태라면 읽는 법도 상태여야 한다. 예전에는 {@code DISTINCT} 로 <b>첫 관측</b>을 남겼는데,
	 * 그러면 켰다 끈 사람을 읽지 못한다 — 끈 것이 이력 뒤에 있어도 앞의 「켬」이 남는다.
	 * 이제 {@code (사람, 장소)} 마다 <b>가장 최근</b> 이벤트 하나만 보고 그 종류로 판정한다.
	 *
	 * <pre>
	 * 마지막이 place_like          → +1.0
	 * 마지막이 place_like_removed  →  기여 없음 (하트를 아예 안 누른 것과 같다)
	 * 마지막이 place_dislike       → -1.0
	 * </pre>
	 *
	 * <p>중복 제거는 그대로 살아 있다 — 장소마다 행이 하나만 나오므로 앱·서버가 각자 적은
	 * 하트 두 건도 한 번으로 세어진다.
	 *
	 * <p>순서는 {@code seq} 로 가른다. {@code occurred_at} 만으로 정렬하면 같은 시각의 이벤트
	 * 순서가 임의로 떨어진다 — 그 칼럼이 있는 이유가 정확히 이것이다.
	 */
	private static final List<String> STATE_PLACE_EVENTS =
			List.of("place_like", "place_like_removed", "place_dislike");

	/**
	 * 상태를 <b>끄는</b> 이벤트 — 마지막이 이것이면 그 장소는 기여가 없다.
	 *
	 * <p>🔴 {@link #CONTRIBUTION} 에 {@code 0.0} 으로 넣지 않고 질의에서 <b>빼는</b> 이유가
	 * 있다. 기여가 0 이어도 행이 나오면 {@code observations} 가 세어져 {@link #MIN_SUPPORT}
	 * 가드를 넘길 수 있다 — 「끈 하트 두 개」가 성분을 만들어 낸다. 아예 안 나오는 것이 맞다.
	 */
	private static final List<String> STATE_CLEARING_EVENTS = List.of("place_like_removed");

	/**
	 * payload 에 장소가 하나 실리고, <b>반복이 뜻을 가지는</b> 이벤트.
	 *
	 * <p>같은 장소를 두 번 본 것은 한 번 본 것과 다르고, 두 번 간 것은 한 번 간 것과 다르다.
	 * 여기에 {@code DISTINCT} 를 걸면 그 차이가 사라진다.
	 */
	private static final List<String> REPEATABLE_PLACE_EVENTS = List.of("place_view", "place_visit");

	/** payload 에 장소가 <b>여럿</b> 실리는 이벤트 — {@code place_ids} 배열. */
	private static final List<String> MANY_PLACE_EVENTS = List.of("itinerary_remove");

	/**
	 * 「몇 건이면 확신하나」. {@code weight = raw / (|raw| + K)} 의 K 다 — 같은 태그로 K 건이
	 * 모이면 0.5, 3K 건이면 0.75 가 된다. 올리면 더 신중해지고 내리면 성급해진다.
	 */
	private static final double CONFIDENCE_K = 3.0;

	/**
	 * 이보다 적게 관측된 성분은 안 내보낸다.
	 *
	 * <p>🔴 한 번 누른 것을 확신처럼 다루지 않는다. {@code UserTasteWeight} 가
	 * {@code SURVEY} 가 아닌 성분에 {@code support=0} 을 DB 에서 거부하는 것과 같은 정신이다.
	 */
	private static final int MIN_SUPPORT = 2;

	/** JSONB 에는 UUID 가 아닌 문자열도 들어올 수 있다. 캐스팅 전에 모양을 먼저 본다. */
	private static final String UUID_SHAPE =
			"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

	/**
	 * 장소가 하나인 이벤트와 여럿인 이벤트를 한 모양으로 편 뒤, 장소 표식과 대조표를 지나
	 * {@code (차원, 코드, 이벤트 종류)} 별 관측 수로 접는다.
	 *
	 * <p>{@code place_ids} 쪽의 {@code CASE} 는 장식이 아니다. {@code jsonb_array_elements_text}
	 * 는 배열이 아닌 값을 받으면 <b>오류를 던지고</b>, 그 검사를 {@code WHERE} 에 두면 펼친
	 * 뒤에 평가될 수 있어 막지 못한다. 그래서 함수에 들어가기 전에 빈 배열로 바꾼다.
	 */
	private static final String ATTRIBUTION_SQL = """
			WITH state AS (
			    -- 하트·하트끔·싫어요 — «상태» 라 마지막 것만 본다. 이유는 STATE_PLACE_EVENTS 에 있다.
			    SELECT DISTINCT ON (place_id) event_type, place_id
			      FROM (SELECT e.event_type                          AS event_type,
			                   CAST(e.payload ->> 'placeId' AS uuid) AS place_id,
			                   e.seq                                 AS seq
			              FROM event_outbox e
			             WHERE e.user_id = ?
			               AND e.received_at <= ?
			               AND e.event_type = ANY (string_to_array(?, ','))
			               AND e.payload ->> 'placeId' ~ ?) observed
			     ORDER BY place_id, seq DESC
			),
			signal AS (
			    -- 마지막이 «끔» 인 장소는 행 자체가 안 나온다 — 하트를 아예 안 누른 것과 같다.
			    SELECT event_type, place_id
			      FROM state
			     WHERE event_type <> ALL (string_to_array(?, ','))
			    UNION ALL
			    -- 보기·방문 — 반복이 뜻을 가지므로 그대로 센다.
			    SELECT e.event_type,
			           CAST(e.payload ->> 'placeId' AS uuid)
			      FROM event_outbox e
			     WHERE e.user_id = ?
			       AND e.received_at <= ?
			       AND e.event_type = ANY (string_to_array(?, ','))
			       AND e.payload ->> 'placeId' ~ ?
			    UNION ALL
			    SELECT e.event_type,
			           CAST(pid AS uuid)
			      FROM event_outbox e
			      CROSS JOIN LATERAL jsonb_array_elements_text(
			               CASE WHEN jsonb_typeof(e.payload -> 'place_ids') = 'array'
			                    THEN e.payload -> 'place_ids'
			                    ELSE '[]'::jsonb END) AS pid
			     WHERE e.user_id = ?
			       AND e.received_at <= ?
			       AND e.event_type = ANY (string_to_array(?, ','))
			       AND e.payload ->> 'operational_reason' IS NULL
			       AND pid ~ ?
			)
			SELECT m.user_input_code AS dimension,
			       pf.feature_key    AS code,
			       s.event_type      AS event_type,
			       count(*)          AS observations
			  FROM signal s
			  JOIN place_feature pf
			    ON pf.place_id = s.place_id
			   AND pf.feature_key IS NOT NULL
			   AND pf.evidence_status IN ('VERIFIED', 'ESTIMATED')
			   AND (pf.value IS NULL OR pf.value::text <> 'false')
			  JOIN user_place_code_map m
			    ON m.place_feature_type = pf.feature_type
			   AND m.user_input_kind = 'PREFERENCE'
			   AND m.match_kind = 'TAG_OVERLAP'
			 GROUP BY 1, 2, 3
			""";

	private final JdbcTemplate jdbc;

	public BehaviorTasteFolder(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * 이 사람의 행동을 성분으로 접는다.
	 *
	 * <p>동의는 <b>여기서 안 본다.</b> 부르는 쪽이 이미 물었다 — 두 곳에 두면 한쪽만 바뀐다.
	 *
	 * @param asOf 이 시각까지 도착한 것만 본다. 부르는 쪽의 실행 구간 끝
	 * @return 뒷받침이 모자란 성분은 빠진 목록. 아무것도 없으면 빈 목록
	 */
	public List<Attribution> fold(UUID userId, OffsetDateTime asOf) {
		Map<Key, Running> running = new LinkedHashMap<>();

		this.jdbc.query(ATTRIBUTION_SQL, (rs) -> {
			TasteDimension dimension = parseDimension(rs.getString("dimension"));
			String code = rs.getString("code");
			String eventType = rs.getString("event_type");
			int observations = rs.getInt("observations");

			Double contribution = CONTRIBUTION.get(eventType);
			if (dimension == null || code == null || contribution == null) {
				// 질의가 골라 온 것이므로 여기 오면 대조표와 이 클래스의 목록이 어긋난 것이다.
				// 조용히 넘기지 않는다 — 그 어긋남은 "성분이 적게 나온다" 로만 나타난다.
				log.warn("귀속할 수 없는 줄을 건너뛴다 dimension={} code={} eventType={}",
						rs.getString("dimension"), code, eventType);
				return;
			}
			running.computeIfAbsent(new Key(dimension, code), (k) -> new Running())
					.add(contribution * observations, observations);
		}, userId, asOf, String.join(",", STATE_PLACE_EVENTS), UUID_SHAPE,
				String.join(",", STATE_CLEARING_EVENTS),
				userId, asOf, String.join(",", REPEATABLE_PLACE_EVENTS), UUID_SHAPE,
				userId, asOf, String.join(",", MANY_PLACE_EVENTS), UUID_SHAPE);

		List<Attribution> result = new ArrayList<>();
		running.forEach((key, sum) -> {
			if (sum.support < MIN_SUPPORT) {
				return;
			}
			result.add(new Attribution(key.dimension(), key.code(), confidence(sum.raw), sum.support));
		});
		return result;
	}

	/**
	 * 쌓인 힘을 {@code -1 ~ +1} 무게로 옮긴다. 건수가 늘수록 1 에 가까워지되 절대 넘지 않는다 —
	 * {@code ck_user_taste_weight_range} 가 범위를 막기도 하지만, 잘려서 통과하는 것과 애초에
	 * 그 안에 있는 것은 다르다. 잘리면 100 건과 1000 건이 같은 값이 된다.
	 */
	private static double confidence(double raw) {
		return raw / (Math.abs(raw) + CONFIDENCE_K);
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

	private record Key(TasteDimension dimension, String code) {
	}

	/** 한 성분에 쌓이는 중간값. */
	private static final class Running {

		private double raw;

		private int support;

		void add(double weightedContribution, int observations) {
			this.raw += weightedContribution;
			this.support += observations;
		}
	}
}
