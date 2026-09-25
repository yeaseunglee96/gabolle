package com.gabolle.backend.itinerary.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 보정 작업은 첫 판에서 「기본값의 ±20%」까지만 움직인다(S15P21E201-1692). 그 기본값을 파이썬 쪽이 따로 들고 있으므로 두 벌이
 * 어긋나면 기준선이 엔진이 실제로 쓰던 값과 달라진다 — 오류 없이. 그래서 여기서 둘을 맞대 본다.
 */
class StayCalibrationBaselineTest {

	private static final Path CHECKS = Path.of("calibration", "jobs", "stay_minutes", "checks.json");

	@Test
	@DisplayName("🔴 보정 작업의 기준선이 StayDefaults 와 같다 — 갈래 하나라도 어긋나면 첫 판의 폭 제한이 엉뚱한 값에서 잰다")
	void baselineMatchesStayDefaults() throws Exception {
		JsonNode checks = JsonMapper.builder().build().readTree(Files.readString(CHECKS));

		Map<String, Integer> baseline = new java.util.HashMap<>();
		checks.get("baseline").properties().forEach((entry) -> baseline.put(entry.getKey(), entry.getValue().asInt()));

		assertThat(baseline).isEqualTo(StayDefaults.BY_CATEGORY);
		assertThat(checks.get("baseline_default").asInt()).isEqualTo(StayDefaults.UNKNOWN_CATEGORY_MINUTES);
		assertThat(checks.get("min_value").asInt()).isEqualTo(StayDefaults.MIN_STAY_MINUTES);
	}
}
