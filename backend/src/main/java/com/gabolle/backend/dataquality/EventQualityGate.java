package com.gabolle.backend.dataquality;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.common.privacy.SensitiveDataInPayloadException;
import com.gabolle.backend.common.privacy.SensitivePayloadGuard;

/**
 * 추천 후보와 실제 노출이 실제로 이어지는지 검사한다. 로그는 쌓였는데 추천과 행동이 안 이어진
 * 상태는 어디에서도 오류로 안 나타나고 — 표도 멀쩡하고 API 는 202 를 주고 이벤트 수도 늘어난다
 * — 조인해 봐야 안다. 축을 푸는 자리는 {@code recommendation_exposure} 뷰 하나이고 판정은 여기다.
 *
 * <p>비율은 추세를 보는 값이고 위반은 통과를 막는 값이다. 비율에 문턱을 두지 않은 것은 실제
 * 분포를 아직 못 봤기 때문이다 — 지금 숫자를 정하면 그것이 계약이 된다. 위반은 분포와 무관하게
 * 틀린 것이라 한 건이라도 있으면 막는다.
 */
@Component
@Profile({ "db", "dev" })
public class EventQualityGate {

	/**
	 * 애플리케이션이 설정한 {@code ObjectMapper} 빈을 일부러 안 쓴다. 앱 설정이 느슨하게 읽도록
	 * 되어 있으면 망가진 payload 가 게이트 눈에 멀쩡해 보인다. 테스트 슬라이스에 그 빈이 없어
	 * 검사가 못 도는 환경이 생기는 것도 막는다.
	 */
	private static final ObjectMapper STRICT_MAPPER = new ObjectMapper();

	/**
	 * schema 이름으로 허용하는 모양. {@code SET search_path} 는 바인딩 파라미터를 못 받아 이름을
	 * 문자열로 이어 붙여야 하므로, 설정값이라도 검사 없이 넣지 않는다.
	 */
	private static final Pattern SAFE_SCHEMA = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

	private final JdbcTemplate jdbcTemplate;

	private final SensitivePayloadGuard sensitivePayloadGuard;

	private final Clock clock;

	/**
	 * 표가 어느 schema 에 있는가. 비어 있으면 아무것도 하지 않는다.
	 *
	 * <p>운영은 백엔드 표를 별도 schema 에 두는데 그 설정은 Flyway 와 Hibernate 에만 걸린다 —
	 * {@link JdbcTemplate} 로 던지는 수식 없는 SQL 은 물려받지 않고 연결의 {@code search_path}
	 * 를 쓴다. 테스트 데이터소스에는 그 설정이 없어 초록인데 운영에서 표를 못 찾는 조합이 된다.
	 *
	 * <p>표 이름에 schema 를 박지 않는 것은 이름을 바꾸는 날 이 파일을 다시 뒤지지 않으려는
	 * 것이다.
	 */
	private final String defaultSchema;

	public EventQualityGate(JdbcTemplate jdbcTemplate, SensitivePayloadGuard sensitivePayloadGuard, Clock clock,
			@Value("${spring.jpa.properties.hibernate.default_schema:}") String defaultSchema) {
		this.jdbcTemplate = jdbcTemplate;
		this.sensitivePayloadGuard = sensitivePayloadGuard;
		this.clock = clock;
		this.defaultSchema = (defaultSchema == null) ? "" : defaultSchema.trim();
	}

	/**
	 * 이 트랜잭션 안에서만 표를 찾는 순서를 맞춘다. 반드시 {@code SET LOCAL} 이다 — 그냥
	 * {@code SET} 은 연결 풀에 남아서 그 연결을 다음에 빌려 쓰는 코드까지 바뀐 채로 돈다.
	 *
	 * <p>{@code public} 을 뒤에 남겨 둔다. PostGIS 함수처럼 거기 사는 것을 쓰게 되는 날
	 * 조용히 깨지지 않게 한다.
	 */
	private void alignSearchPath() {
		if (this.defaultSchema.isEmpty()) {
			return;
		}
		if (!SAFE_SCHEMA.matcher(this.defaultSchema).matches()) {
			throw new IllegalStateException(
					"schema 이름으로 쓸 수 없는 값이다 (영문자·숫자·밑줄만): " + this.defaultSchema);
		}
		this.jdbcTemplate.execute("SET LOCAL search_path TO \"" + this.defaultSchema + "\", public");
	}

	/**
	 * 검사하고 결과를 남긴다. 실패해도 예외를 던지지 않는다 — 결과를 보고 판단하려는
	 * 호출자를 위한 입구다.
	 */
	@Transactional
	public EventQualityReport measure(String datasetVersion) {
		alignSearchPath();
		EventQualityReport report = compute(datasetVersion);
		save(report);
		return report;
	}

	/**
	 * 검사하고, 위반이 하나라도 있으면 던진다. 실패해도 결과는 먼저 남긴다 — 실패한 날의
	 * 숫자가 없으면 언제부터 나빠졌는지 되짚을 수 없다.
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

		// 스키마 유효율 — 뷰가 모양을 먼저 검사하므로 NULL 인 것은 "값이 없다" 가 아니라
		// "모양이 틀렸다" 다.
		long schemaValid = count("""
				SELECT count(*) FROM recommendation_exposure
				WHERE place_id IS NOT NULL AND final_rank IS NOT NULL
				""");

		// 중복률을 event_id 로 세지 않는다. PK 라서 중복이 불가능해 언제나 0 이 나오고 거짓
		// 안심을 준다. 실제 위험은 같은 (요청, 장소)가 서로 다른 event_id 로 두 번 들어오는
		// 것이다 — 클라이언트가 재렌더링 때 새 UUID 를 만들면 그렇게 된다.
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

		// 비율로만 남기면 조용히 통과한다. 축이 없는 노출은 영영 후보와 못 잇는다.
		if (requestIdMissing > 0) {
			violations.add("requestId 없는 노출 " + requestIdMissing + "건 (후보와 이을 축이 없음)");
		}

		// 존재하지 않는 후보의 노출 — 축이 끊겼다는 가장 직접적인 증거다.
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

		// 순위 불일치 — 화면이 보여준 순위와 저장된 순위가 다르면 둘 중 하나가 거짓이다.
		long rankMismatches = count("""
				SELECT count(*) FROM recommendation_exposure e
				JOIN recommendation_candidate c
				  ON c.request_id = e.request_id AND c.place_id = e.place_id
				WHERE e.final_rank IS DISTINCT FROM c.final_rank
				""");
		if (rankMismatches > 0) {
			violations.add("순위 불일치 " + rankMismatches + "건 (노출 finalRank ≠ 후보 final_rank)");
		}

		// 버전 누락 — 요구 버전은 fallbackMode 가 정한다. fallbackMode 가 없으면 가장 엄격한
		// MODEL 로 본다. 없는 값을 낙관적으로 채우지 않는다.
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

		// 하드 제약을 위반한 후보가 화면에 나갔다. 데이터 문제가 아니라 안전 문제다. DB CHECK
		// 가 returned=true 를 막지만 노출 이벤트는 클라이언트가 만들어 보내 그 CHECK 를 안
		// 지나므로, 여기가 그것을 잡는 유일한 자리다.
		long failExposed = count("""
				SELECT count(*) FROM recommendation_exposure e
				JOIN recommendation_candidate c
				  ON c.request_id = e.request_id AND c.place_id = e.place_id
				WHERE c.constraint_verdict = 'FAIL'
				""");
		if (failExposed > 0) {
			violations.add("🔴 하드 제약 위반(FAIL) 후보가 노출됨 " + failExposed + "건 — 안전 문제");
		}

		// 개인정보·정밀 좌표가 payload 에 들어왔다.
		long piiViolations = countSensitivePayloads(violations);

		return new EventQualityReport(LocalDate.now(this.clock.withZone(ZoneId.of("Asia/Seoul"))), datasetVersion,
				events, impressions, candidates,
				// 좋은 비율과 나쁜 비율은 비어 있을 때의 기본값이 반대다. 아래 두 함수 참고.
				goodRate(schemaValid, impressions),
				badRate(identifiableExposures - distinctExposures, identifiableExposures),
				badRate(requestIdMissing, impressions), badRate(featureMissing, candidates), orphans, rankMismatches,
				versionMissing, piiViolations, failExposed, violations);
	}

	/**
	 * 쌓인 payload 를 개인정보 검사에 다시 통과시킨다. 쓰는 쪽이 이미 같은 검사를 하지만,
	 * 원시 SQL 로 넣은 행이나 검사를 지나지 않는 경로로 들어온 행은 그 검사로는 안 잡힌다.
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
	 * 같은 날 같은 판을 다시 재면 뒤에 잰 것으로 덮는다. 지우고 넣지 않고 {@code ON CONFLICT}
	 * 로 덮는 것은, 그 사이에 다른 트랜잭션이 읽으면 그 날 리포트가 없는 것처럼 보이기 때문이다.
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
	 * 높을수록 좋은 비율. 분모가 0 이면 1.0 이다 — 노출이 없으면 잘못된 노출도 없다.
	 * 0.0 으로 두면 빈 DB 가 "유효율 0%" 로 읽힌다.
	 */
	private double goodRate(long numerator, long denominator) {
		return (denominator <= 0) ? 1.0 : clamp((double) numerator / (double) denominator);
	}

	/**
	 * 낮을수록 좋은 비율. 분모가 0 이면 0.0 이다. {@link #goodRate} 와 합치면 안 된다 —
	 * 방향이 반대인 값을 한 함수로 계산하면 빈 DB 에서 한쪽이 반드시 틀리고, CHECK 는 0~1 만
	 * 보므로 그 거짓말이 그대로 저장된다.
	 */
	private double badRate(long numerator, long denominator) {
		return (denominator <= 0) ? 0.0 : clamp((double) numerator / (double) denominator);
	}

	private double clamp(double value) {
		return Math.max(0.0, Math.min(1.0, value));
	}
}
