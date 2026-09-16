package com.gabolle.backend.place;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.loader.DesiredFoodTagLoaderRunner;
import com.gabolle.backend.place.loader.DesiredFoodVocabulary;
import com.gabolle.backend.place.loader.PlaceFeatureLoader;
import com.gabolle.backend.place.loader.PlaceFeatureNdjsonReader;
import com.gabolle.backend.place.loader.ResearchQueueReader;
import com.gabolle.backend.place.loader.SbizPlaceLoader;
import com.gabolle.backend.place.loader.SbizRow;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "이미 있는 SBIZ 장소"에 {@code DESIRED_FOOD_TAG}가 붙는지 진짜 PostgreSQL 에서 잰다
 * — S15P21E201-448.
 *
 * <p>🔴 이게 이 티켓의 핵심이다 — {@link SbizPlaceLoader}로 이미 만든 장소는 재적재로
 * 새 표식을 못 받는다("이미 있는 장소는 건너뛴다"). {@link DesiredFoodTagLoaderRunner}가
 * {@link PlaceFeatureLoader}로 <b>따로</b> 붙이는 것이 이 문제를 푸는 방식이고, 그것을
 * 여기서 확인한다.
 */
class DesiredFoodTagIntegrationTest extends PlacePostgresIntegrationTest {

	private static final String DATASET = "staged-test-202609";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private SbizPlaceLoader placeLoader;

	@Autowired
	private PlaceFeatureLoader featureLoader;

	@TempDir
	Path tempDir;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		this.jdbcTemplate.update(
				"DELETE FROM place_feature WHERE source_type IN ('SBIZ', 'RESEARCH_DESIRED_FOOD')");
		this.jdbcTemplate.update("DELETE FROM place WHERE source_type = 'SBIZ'");
	}

	private void givenPlace(String storeId, String name) {
		this.placeLoader.saveChunk(
				List.of(new SbizRow(storeId, name, "", "백반/한정식", "부산광역시 해운대구 중동1로43번길 23", 35.16, 129.16)),
				DATASET, OffsetDateTime.now());
	}

	private Path queueFile(String storeId, String name) {
		try {
			Path path = this.tempDir.resolve("queue.ndjson");
			// 🔴 괄호가 있어야 한다. `.formatted(...)` 는 `+` 보다 먼저 묶여서, 괄호가 없으면
			//    뒤쪽 조각 하나에만 붙는다 — 그 조각에는 %s 가 없으니 아무 일도 안 일어나고
			//    앞 조각의 %s 두 개가 글자 그대로 파일에 적힌다. 그러면 이 검사는 "%s" 라는
			//    이름의 가게를 찾게 되고, 낱말 매칭이 0건이라 붙일 표식도 0건이 된다
			//    (2026-09-16 CI 실측 — `expected: 1 but was: 0` 의 원인이 이것이었다).
			String line = ("{\"id\":\"%s\",\"name\":\"%s\",\"branch\":null,"
					+ "\"roadAddr\":\"부산광역시 해운대구 중동1로43번길 23\","
					+ "\"gu\":\"해운대구\",\"hdong\":\"중1동\",\"category\":\"백반/한정식\","
					+ "\"lon\":129.16,\"lat\":35.16}")
					.formatted(storeId, name);
			Files.writeString(path, line + "\n", StandardCharsets.UTF_8);
			return path;
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	/** {@link DesiredFoodTagLoaderRunner#run} 이 하는 일을 그대로 흉내 낸다 — 러너는 스프링 프로퍼티로만 켜져서 단위 호출이 어렵다. */
	private PlaceFeatureLoader.Saved runBackfill(Path queue) {
		PlaceFeatureLoader.Saved[] saved = { new PlaceFeatureLoader.Saved(0, 0, 0) };
		ResearchQueueReader.read(queue, 500, rows -> {
			List<PlaceFeatureNdjsonReader.Fact> facts = new ArrayList<>();
			for (SbizRow row : rows) {
				for (String code : DesiredFoodVocabulary.desiredFoodTags(row.name())) {
					facts.add(new PlaceFeatureNdjsonReader.Fact(row.storeId(),
							DesiredFoodVocabulary.FEATURE_TYPE, "true", "SBIZ", code));
				}
			}
			if (!facts.isEmpty()) {
				saved[0] = saved[0]
						.plus(this.featureLoader.saveChunk(facts, DesiredFoodTagLoaderRunner.SOURCE_TYPE, DATASET,
								OffsetDateTime.now()));
			}
		});
		return saved[0];
	}

	@Test
	@DisplayName("이미 SbizPlaceLoader 로 만든 장소에 DESIRED_FOOD_TAG:BOKGUK 이 붙는다")
	void 이미_있는_장소에_붙는다() {
		this.givenPlace("MA010120220808772214", "금수복국");

		PlaceFeatureLoader.Saved saved = this.runBackfill(this.queueFile("MA010120220808772214", "금수복국"));

		assertThat(saved.inserted()).isEqualTo(1);
		Map<String, Object> row = this.jdbcTemplate.queryForMap("""
				SELECT feature_type, feature_key, value::text AS value, evidence_status
				FROM place_feature WHERE source_type = 'RESEARCH_DESIRED_FOOD'
				""");
		assertThat(row.get("feature_type")).isEqualTo("DESIRED_FOOD_TAG");
		assertThat(row.get("feature_key")).isEqualTo("BOKGUK");
		assertThat(row.get("value")).isEqualTo("true");
		// 🔴 조사원이 이름으로 가른 것이지 가게에 확인한 것이 아니다.
		assertThat(row.get("evidence_status")).isEqualTo("ESTIMATED");
	}

	@Test
	@DisplayName("복국이 아니면 안 붙는다")
	void 관련_없으면_안_붙는다() {
		this.givenPlace("MA010120220808772299", "중구기사식당");

		PlaceFeatureLoader.Saved saved = this.runBackfill(this.queueFile("MA010120220808772299", "중구기사식당"));

		assertThat(saved.inserted()).isZero();
	}
}
