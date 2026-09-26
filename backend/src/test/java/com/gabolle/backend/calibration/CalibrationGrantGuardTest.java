package com.gabolle.backend.calibration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 🔴 S15P21E201-1750 — 보정 뷰 · 결과 표를 다시 만드는 마이그레이션은 보정 계정의 권한을 다시 건다.
 *
 * <p>운영의 보정 예약 잡은 전용 계정 {@code calibration_job} 으로 붙는다. 그 계정은 익명 뷰 둘
 * ({@code calibration_stay_source} · {@code calibration_travel_source})을 읽고 {@code calibration_value} 에 덧붙이는 권한만
 * 사람 손으로 받았다(backend/calibration/README.md). 권한은 <b>그 개체에</b> 붙어 있어서, 마이그레이션이 뷰나 표를
 * {@code DROP} 하고 다시 {@code CREATE} 하면 권한이 조용히 사라진다 — 그다음 새벽 예약이 {@code permission denied} 로 멈춘다.
 * ({@code CREATE OR REPLACE VIEW} 는 권한을 지킨다.)
 *
 * <p>뷰 마이그레이션 파일 머리에 적어 두지 않고 여기 시험으로 둔 까닭: 그 파일은 이미 운영에 적용됐다. 주석 한 줄만 바꿔도
 * Flyway 지문 검사가 걸려 서버가 안 뜬다. 마이그레이션을 쓰는 사람이 실제로 부딪히는 자리는 이 시험이다.
 */
class CalibrationGrantGuardTest {

	private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

	/** 보정 뷰 · 표를 만든 마이그레이션 — 이것들은 권한이 생기기 전이라 검사하지 않는다. */
	private static final List<String> ORIGINS = List.of("V20260926010000__calibration_value.sql",
			"V20260926020000__travel_calibration.sql");

	private static final Pattern RECREATES = Pattern.compile(
			"(drop\\s+(view|table)\\s+(if\\s+exists\\s+)?(gabolle\\.)?calibration_(stay_source|travel_source|value)\\b)"
					+ "|(create\\s+(view|table)\\s+(gabolle\\.)?calibration_(stay_source|travel_source|value)\\b)");

	@Test
	@DisplayName("🔴 보정 뷰 · 결과 표를 DROP · CREATE 하는 새 마이그레이션은 calibration_job 권한을 다시 건다")
	void recreatingCalibrationObjectsRegrantsTheJobRole() throws IOException {
		List<String> offenders = new ArrayList<>();
		try (Stream<Path> files = Files.list(MIGRATIONS)) {
			for (Path file : files.filter((p) -> p.getFileName().toString().endsWith(".sql")).toList()) {
				if (ORIGINS.contains(file.getFileName().toString())) {
					continue;
				}
				String sql = Files.readString(file, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
				if (RECREATES.matcher(sql).find() && !sql.contains("calibration_job")) {
					offenders.add(file.getFileName().toString());
				}
			}
		}

		assertThat(offenders)
				.withFailMessage("""
						보정 뷰나 결과 표를 DROP · CREATE 하는데 보정 계정(calibration_job)의 권한을 다시 걸지 않는 마이그레이션이 있습니다.
						권한이 사라지면 새벽 보정 예약이 permission denied 로 멈춥니다. 같은 파일 끝에 이 조각을 더하세요
						(그 계정이 없는 개발 · 시험 DB 에서는 아무것도 안 합니다) — backend/calibration/README.md 「예약」:

						DO $$ BEGIN
						  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'calibration_job') THEN
						    GRANT SELECT ON calibration_stay_source, calibration_travel_source TO calibration_job;
						    GRANT SELECT, INSERT ON calibration_value TO calibration_job;
						  END IF;
						END $$;

						%s""".formatted(String.join("\n", offenders)))
				.isEmpty();
	}

	@Test
	@DisplayName("지킴이가 실제로 걸린다 — 보정 뷰를 DROP 하는 SQL 은 잡고, CREATE OR REPLACE 는 안 잡는다")
	void theGuardPatternMatchesWhatItShould() {
		assertThat(RECREATES.matcher("drop view if exists calibration_stay_source;").find()).isTrue();
		assertThat(RECREATES.matcher("create view gabolle.calibration_travel_source as select 1").find()).isTrue();
		assertThat(RECREATES.matcher("create or replace view calibration_stay_source as select 1").find()).isFalse();
		assertThat(RECREATES.matcher("select * from calibration_value").find()).isFalse();
	}
}
