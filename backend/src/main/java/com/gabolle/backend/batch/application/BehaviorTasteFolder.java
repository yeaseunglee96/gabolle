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
import com.gabolle.backend.preference.domain.TasteSignal;
import com.gabolle.backend.preference.domain.UserTasteWeight;

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
	 * @param raw 눌러 담기 <b>전</b> 의 기여값 합. 무게로 옮기는 것은
	 *     {@link UserTasteWeight#confidence(double)} 가 한다 — 여기서 미리 눌러 담아 넘기면
	 *     저장하는 쪽이 {@code raw} 를 알 수 없고, 그러면 소비자가 증분으로 더할 수 없다
	 *     (S15P21E201-1500)
	 * @param support 이 값을 뒷받침한 관측 수
	 */
	public record Attribution(TasteDimension dimension, String code, double raw, int support) {
	}

	// 🔴 기여값과 이벤트 분류는 TasteSignal 로 옮겼다 (S15P21E201-1500). 카프카 소비자가
	//    같은 값으로 같은 판정을 해야 하는데, 두 곳에 두면 반드시 어긋나고 어긋나면
	//    "배치가 만든 값과 소비자가 만든 값이 다르다"가 오류 없이 생긴다.

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
			    -- 하트·하트끔·싫어요 — «상태» 라 마지막 것만 본다. 이유는 TasteSignal 에 있다.
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

			Double contribution = TasteSignal.contributionOf(eventType);
			if (dimension == null || code == null || contribution == null) {
				// 질의가 골라 온 것이므로 여기 오면 대조표와 이 클래스의 목록이 어긋난 것이다.
				// 조용히 넘기지 않는다 — 그 어긋남은 "성분이 적게 나온다" 로만 나타난다.
				log.warn("귀속할 수 없는 줄을 건너뛴다 dimension={} code={} eventType={}",
						rs.getString("dimension"), code, eventType);
				return;
			}
			running.computeIfAbsent(new Key(dimension, code), (k) -> new Running())
					.add(contribution * observations, observations);
		}, userId, asOf, TasteSignal.stateEventsCsv(), UUID_SHAPE,
				TasteSignal.stateClearingEventsCsv(),
				userId, asOf, TasteSignal.repeatableEventsCsv(), UUID_SHAPE,
				userId, asOf, TasteSignal.manyPlaceEventsCsv(), UUID_SHAPE);

		List<Attribution> result = new ArrayList<>();
		running.forEach((key, sum) -> {
			if (sum.support < TasteSignal.MIN_SUPPORT) {
				return;
			}
			result.add(new Attribution(key.dimension(), key.code(), sum.raw, sum.support));
		});
		return result;
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
