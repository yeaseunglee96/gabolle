package com.gabolle.backend.dataquality;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.common.privacy.SensitiveDataInPayloadException;
import com.gabolle.backend.common.privacy.SensitivePayloadGuard;

/**
 * 추천 후보와 실제 노출이 실제로 이어지는지 검사한다 — S15P21E201-546.
 *
 * <h2>왜 필요한가</h2>
 * 티켓의 목적 그대로다 — <b>"로그는 쌓였지만 추천과 행동이 연결되지 않는 상태를 배포 전에
 * 발견한다."</b> 그 상태는 어디에서도 오류로 나타나지 않는다. 표도 멀쩡하고 API 는 202 를
 * 주고 이벤트 수도 늘어난다. <b>조인해 봐야 알 수 있다.</b>
 *
 * <p>그래서 조인을 사람이 기억하는 것에 맡기지 않는다. 축을 푸는 자리는
 * {@code recommendation_exposure} 뷰 하나이고, 판정은 이 클래스 하나다.
 *
 * <h2>비율과 위반을 나눈 이유</h2>
 * 비율(스키마 유효율·중복률·누락률·결측률)은 <b>추세를 보는 값</b>이고, 위반(고아 노출·순위
 * 불일치·버전 누락·개인정보·FAIL 노출)은 <b>통과를 막는 값</b>이다.
 *
 * <p>🔴 비율에 문턱을 두지 않았다. "결측률 5% 미만이면 통과" 같은 숫자를 지금 정하면 그것이
 * 계약이 되는데, 실제 분포를 아직 한 번도 못 봤다. 재서 남기고, 문턱은 값을 본 뒤에 정한다.
 * 대신 <b>위반은 한 건이라도 있으면 막는다</b> — 그건 분포와 무관하게 틀린 것이다.
 */
@Component
@Profile({ "db", "dev" })
public class EventQualityGate {

	/**
	 * 🔴 애플리케이션이 설정한 {@code ObjectMapper} 빈을 주입받지 않는다. 두 가지 이유다.
	 *
	 * <ol>
	 * <li>추천 테스트 슬라이스에는 그 빈이 없다 (Jackson 자동 설정이 안 올라온다).
	 * 게이트가 남의 빈 유무에 매달리면 검사가 못 도는 환경이 생긴다</li>
	 * <li>더 중요한 것 — <b>게이트는 앱이 관대하게 읽어 주는 것을 그대로 믿으면 안 된다.</b>
	 * 앱 설정이 알 수 없는 필드를 무시하거나 느슨하게 읽도록 되어 있으면, 망가진 payload 가
	 * 게이트 눈에는 멀쩡해 보인다. 기본 설정으로 읽어야 망가진 것이 망가진 것으로 보인다</li>
	 * </ol>
	 */
	private static final ObjectMapper STRICT_MAPPER = new ObjectMapper();

	private final JdbcTemplate jdbcTemplate;

	private final SensitivePayloadGuard sensitivePayloadGuard;

	private final Clock clock;

	public EventQualityGate(JdbcTemplate jdbcTemplate, SensitivePayloadGuard sensitivePayloadGuard, Clock clock) {
		this.jdbcTemplate = jdbcTemplate;
		this.sensitivePayloadGuard = sensitivePayloadGuard;
		this.clock = clock;
	}

	/**
	 * 검사하고 결과를 남긴다. <b>실패해도 예외를 던지지 않는다</b> — 결과를 보고 판단하려는
	 * 호출자를 위한 입구다.
	 */
	@Transactional
	public EventQualityReport measure(String datasetVersion) {
		EventQualityReport report = compute(datasetVersion);
		save(report);
		return report;
	}

	/**
	 * 검사하고, 위반이 하나라도 있으면 던진다 — <b>이것이 게이트다.</b>
	 *
	 * <p>🔴 실패해도 결과는 먼저 남긴다. 실패한 날의 숫자가 없으면 "언제부터 나빠졌는가" 를
	 * 되짚을 수 없고, 그건 게이트가 있는 이유의 절반을 버리는 것이다.
	 */
	@Transactional
	public EventQualityReport gate(String datasetVersion) {
		EventQualityReport report = measure(datasetVersion);
		if (!report.passed()) {
			throw new EventQualityGateFailedException(report);
		}
		return report;
	}

	// ── 재는 것 ───────────────────────────────────────────────────────────────

	private EventQualityReport compute(String datasetVersion) {
		long events = count("SELECT count(*) FROM event_outbox");
		long impressions = count("SELECT count(*) FROM recommendation_exposure");
		long candidates = count("SELECT count(*) FROM recommendation_candidate");

		// 🔴 스키마 유효율 — placeId·finalRank 가 뷰에서 uuid·integer 로 풀렸는가.
		//    뷰가 모양을 먼저 검사하므로, NULL 인 것은 "값이 없다" 가 아니라 "모양이 틀렸다" 다.
		long schemaValid = count("""
				SELECT count(*) FROM recommendation_exposure
				WHERE place_id IS NOT NULL AND final_rank IS NOT NULL
				""");

		// 🔴 중복률을 event_id 로 세지 않는다. event_id 는 PK 라서 중복이 애초에 불가능하고,
		//    그것을 세면 언제나 0 이 나와서 "중복이 없다" 는 거짓 안심을 준다.
		//    실제 위험은 같은 (요청, 장소)가 서로 다른 event_id 로 두 번 들어오는 것이다 —
		//    클라이언트가 재렌더링 때 새 UUID 를 만들면 그렇게 된다 (S15P21E201-544).
		long distinctExposures = count("""
				SELECT count(*) FROM (
				    SELECT DISTINCT request_id, place_id FROM recommendation_exposure
				    WHERE request_id IS NOT NULL AND place_id IS NOT NULL
				) d
				""");
		long identifiableExposures = count("""
				SELECT count(*) FROM recommendation_exposure
				WHERE request_id IS NOT NULL AND place_id IS NOT NULL
				""");

		long requestIdMissing = count("""
				SELECT count(*) FROM recommendation_exposure WHERE request_id IS NULL
				""");

		// 후보 피처 결측률 — 빈 JSONB 는 "안 채워졌다" 는 뜻이다.
		long featureMissing = count("""
				SELECT count(*) FROM recommendation_candidate
				WHERE feature_values IS NULL OR feature_values = '{}'::jsonb
				""");

		List<String> violations = new ArrayList<>();

		// (0) 🔴 requestId 가 없는 노출 — 완료 기준 "requestId 누락이 조용히 통과하지 않는다".
		//     비율로만 남기면 조용히 통과한다. 축이 없는 노출은 영영 후보와 못 잇는다 (API-07).
		if (requestIdMissing > 0) {
			violations.add("requestId 없는 노출 " + requestIdMissing + "건 (후보와 이을 축이 없음)");
		}

		// (1) 존재하지 않는 후보의 노출 — 축이 끊겼다는 가장 직접적인 증거다.
		long orphans = count("""
				SELECT count(*) FROM recommendation_exposure e
				WHERE e.request_id IS NOT NULL AND e.place_id IS NOT NULL
				  AND NOT EXISTS (
				      SELECT 1 FROM recommendation_candidate c
				      WHERE c.request_id = e.request_id AND c.place_id = e.place_id)
				""");
		if (orphans > 0) {
			violations.add("존재하지 않는 후보의 노출 " + orphans + "건 (request_id + place_id 로 후보를 못 찾음)");
		}

		// (2) 순위 불일치 — 화면이 보여준 순위와 저장된 순위가 다르면 둘 중 하나가 거짓이다.
		long rankMismatches = count("""
				SELECT count(*) FROM recommendation_exposure e
				JOIN recommendation_candidate c
				  ON c.request_id = e.request_id AND c.place_id = e.place_id
				WHERE e.final_rank IS DISTINCT FROM c.final_rank
				""");
		if (rankMismatches > 0) {
			violations.add("순위 불일치 " + rankMismatches + "건 (노출 finalRank ≠ 후보 final_rank)");
		}

		// (3) 버전 누락 — FR-REC-12. 요구 버전은 fallbackMode 가 정한다.
		//     🔴 fallbackMode 가 없으면 MODEL(가장 엄격)로 본다. 없는 값을 낙관적으로 채우지 않는다.
		long versionMissing = count("""
				SELECT count(*) FROM recommendation_exposure
				WHERE dataset_version IS NULL
				   OR policy_version IS NULL
				   OR (upper(coalesce(fallback_mode, 'MODEL')) IN ('MODEL', 'RULE')
				       AND ontology_version IS NULL)
				   OR (upper(coalesce(fallback_mode, 'MODEL')) = 'MODEL'
				       AND (model_version IS NULL OR feature_version IS NULL))
				""");
		if (versionMissing > 0) {
			violations.add("버전 누락 " + versionMissing + "건 (fallbackMode 가 요구하는 버전이 비었음)");
		}

		// (4) 🔴 하드 제약을 위반한 후보가 화면에 나갔다. 데이터 문제가 아니라 안전 문제다.
		//     DB CHECK 가 returned=true 를 막지만, 노출 이벤트는 클라이언트가 만들어 보내므로
		//     그 CHECK 를 지나지 않는다. 여기가 그것을 잡는 유일한 자리다.
		long failExposed = count("""
				SELECT count(*) FROM recommendation_exposure e
				JOIN recommendation_candidate c
				  ON c.request_id = e.request_id AND c.place_id = e.place_id
				WHERE c.constraint_verdict = 'FAIL'
				""");
		if (failExposed > 0) {
			violations.add("🔴 하드 제약 위반(FAIL) 후보가 노출됨 " + failExposed + "건 — 안전 문제");
		}

		// (5) 개인정보·정밀 좌표가 payload 에 들어왔다 (DR-13).
		long piiViolations = countSensitivePayloads(violations);

		return new EventQualityReport(LocalDate.now(this.clock.withZone(ZoneId.of("Asia/Seoul"))), datasetVersion,
				events, impressions, candidates,
				// 🔴 좋은 비율과 나쁜 비율은 비어 있을 때의 기본값이 반대다. 아래 두 함수 참고.
				goodRate(schemaValid, impressions),
				badRate(identifiableExposures - distinctExposures, identifiableExposures),
				badRate(requestIdMissing, impressions), badRate(featureMissing, candidates), orphans, rankMismatches,
				versionMissing, piiViolations, failExposed, violations);
	}

	/**
	 * 쌓인 payload 를 개인정보 검사에 다시 통과시킨다.
	 *
	 * <p>🔴 쓰는 쪽({@code OutboxService})이 이미 같은 검사를 한다. 그런데도 여기서 또 보는
	 * 이유는, 게이트의 일이 <b>그 검사가 꺼졌거나 우회됐을 때를 잡는 것</b>이기 때문이다.
	 * 원시 SQL 로 넣은 행, 검사가 없던 시절의 행, 검사를 지나지 않는 경로가 생긴 행은
	 * 쓰는 쪽 검사로는 절대 안 잡힌다.
	 */
	private long countSensitivePayloads(List<String> violations) {
		List<Map<String, Object>> rows = this.jdbcTemplate
				.queryForList("SELECT event_id, event_type, payload::text AS payload FROM event_outbox");

		long offenders = 0;
		List<String> samples = new ArrayList<>();
		for (Map<String, Object> row : rows) {
			String payload = (String) row.get("payload");
			if (payload == null || payload.isBlank()) {
				continue;
			}
			try {
				Map<?, ?> parsed = STRICT_MAPPER.readValue(payload, Map.class);
				this.sensitivePayloadGuard.verify(parsed, "payload");
			}
			catch (SensitiveDataInPayloadException ex) {
				offenders++;
				if (samples.size() < 3) {
					samples.add(row.get("event_type") + "/" + row.get("event_id") + ": " + ex.getMessage());
				}
			}
			catch (Exception ex) {
				// payload 가 JSON 객체가 아니면 그 자체가 스키마 위반이다. 조용히 넘기지 않는다.
				offenders++;
				if (samples.size() < 3) {
					samples.add(row.get("event_type") + "/" + row.get("event_id") + ": payload 를 읽을 수 없음");
				}
			}
		}
		if (offenders > 0) {
			violations.add("payload 개인정보·좌표 위반 " + offenders + "건 — " + String.join(", ", samples));
		}
		return offenders;
	}

	// ── 남기는 것 ─────────────────────────────────────────────────────────────

	/**
	 * 같은 날 같은 판을 다시 재면 뒤에 잰 것으로 덮는다.
	 *
	 * <p>🔴 행을 지우고 넣지 않고 {@code ON CONFLICT} 로 덮는다. 지우고 넣는 사이에 다른
	 * 트랜잭션이 읽으면 그 날 리포트가 없는 것처럼 보인다.
	 */
	private void save(EventQualityReport report) {
		this.jdbcTemplate.update("""
				INSERT INTO event_quality_report (
				    report_id, report_date, dataset_version,
				    events_total, impressions_total, candidates_total,
				    schema_valid_rate, duplicate_rate, request_id_missing_rate,
				    candidate_feature_missing_rate,
				    orphan_impressions, rank_mismatches, version_missing, pii_violations,
				    fail_verdict_exposed, passed, failure_summary, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (report_date, dataset_version) DO UPDATE SET
				    events_total = EXCLUDED.events_total,
				    impressions_total = EXCLUDED.impressions_total,
				    candidates_total = EXCLUDED.candidates_total,
				    schema_valid_rate = EXCLUDED.schema_valid_rate,
				    duplicate_rate = EXCLUDED.duplicate_rate,
				    request_id_missing_rate = EXCLUDED.request_id_missing_rate,
				    candidate_feature_missing_rate = EXCLUDED.candidate_feature_missing_rate,
				    orphan_impressions = EXCLUDED.orphan_impressions,
				    rank_mismatches = EXCLUDED.rank_mismatches,
				    version_missing = EXCLUDED.version_missing,
				    pii_violations = EXCLUDED.pii_violations,
				    fail_verdict_exposed = EXCLUDED.fail_verdict_exposed,
				    passed = EXCLUDED.passed,
				    failure_summary = EXCLUDED.failure_summary,
				    created_at = EXCLUDED.created_at
				""", UUID.randomUUID(), report.reportDate(), report.datasetVersion(), report.eventsTotal(),
				report.impressionsTotal(), report.candidatesTotal(), report.schemaValidRate(), report.duplicateRate(),
				report.requestIdMissingRate(), report.candidateFeatureMissingRate(), report.orphanImpressions(),
				report.rankMismatches(), report.versionMissing(), report.piiViolations(), report.failVerdictExposed(),
				report.passed(), report.failureSummary(), java.time.OffsetDateTime.now(this.clock));
	}

	private long count(String sql) {
		Long value = this.jdbcTemplate.queryForObject(sql, Long.class);
		return (value == null) ? 0L : value;
	}

	/**
	 * 높을수록 좋은 비율(유효율). 🔴 분모가 0 이면 <b>1.0</b> 이다 — 노출이 없으면 잘못된
	 * 노출도 없다. 0.0 으로 두면 빈 DB 가 "유효율 0%" 로 읽히고, 그건 사실이 아니다.
	 */
	private double goodRate(long numerator, long denominator) {
		return (denominator <= 0) ? 1.0 : clamp((double) numerator / (double) denominator);
	}

	/**
	 * 낮을수록 좋은 비율(중복률 · 누락률 · 결측률). 🔴 분모가 0 이면 <b>0.0</b> 이다.
	 *
	 * <p>이 둘을 한 함수로 합쳐 뒀다가 실제로 틀렸다 — 빈 DB 에서 중복률이 1.0 으로
	 * 기록됐다. CHECK 는 0~1 만 보므로 <b>그 거짓말이 그대로 저장된다.</b> 방향이 반대인
	 * 값을 같은 함수로 계산하면 언제나 한쪽이 틀린다.
	 */
	private double badRate(long numerator, long denominator) {
		return (denominator <= 0) ? 0.0 : clamp((double) numerator / (double) denominator);
	}

	private double clamp(double value) {
		return Math.max(0.0, Math.min(1.0, value));
	}
}
