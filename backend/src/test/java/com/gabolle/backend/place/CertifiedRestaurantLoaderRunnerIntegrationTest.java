package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import com.gabolle.backend.place.loader.CertifiedRestaurantLoaderRunner;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

/**
 * 적재기 실행기를 **앱이 뜨는 순간 그대로** 돌려 본다 (S15P21E201-2051) — 프로퍼티를 주면 기동 끝에 파일을 읽어 DB 에 넣는다.
 * 단위 시험은 읽기와 한 덩어리 넣기를 따로 보고, 이 시험은 그 둘을 잇는 실행기(덩어리 나누기·건너뛴 줄 기록·사람 확인 목록 파일)를
 * 본다. 파일은 실제 666줄 파일에서 문제 줄을 뽑은 표본 스물세 줄이고, 운영 절차가 그대로 따라 쓰는 명령({@code --gabolle.place.loader.…})과
 * 같은 프로퍼티로 넣는다.
 *
 * <p>표본의 {@code existingPlaceId} 는 이 시험 DB 에 없는 장소를 가리킨다 — 「다른 DB 의 번호로 만든 파일」 경로(표식을 못 달고 센다)를 겸해 본다.
 */
@TestPropertySource(properties = "gabolle.place.loader.dataset-version=" + CertifiedRestaurantLoaderRunnerIntegrationTest.DATASET)
class CertifiedRestaurantLoaderRunnerIntegrationTest extends PlacePostgresIntegrationTest {

	static final String DATASET = "certified-runner-test";

	/** 실행기가 입력 파일 옆에 쓰는 사람 확인 목록(CSV)도 확인하려고, 표본을 임시 폴더로 복사해 쓴다. */
	private static Path input;

	@DynamicPropertySource
	static void runnerProperty(DynamicPropertyRegistry registry) throws IOException {
		Path dir = Files.createTempDirectory("certified-runner");
		input = Files.copy(Path.of("src/test/resources/place/certified-restaurants-sample.ndjson"),
				dir.resolve("certified-restaurants.ndjson"));
		registry.add("gabolle.place.loader.certified-restaurants", () -> input.toString());
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired(required = false)
	private CertifiedRestaurantLoaderRunner runner;

	@AfterEach
	void tearDown() {
		this.jdbcTemplate.update("DELETE FROM place_feature WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND source_version = ?",
				DATASET);
		this.jdbcTemplate.update("DELETE FROM place WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND dataset_version = ?",
				DATASET);
	}

	@Test
	@DisplayName("🔴 프로퍼티만 주면 기동 끝에 표본이 들어간다 — 새 장소 일곱, 건너뛴 줄과 남는 줄은 안 들어가고, 표식은 장소마다 붙는다")
	void runnerLoadsTheSampleAtStartup() throws IOException {
		assertThat(this.runner).as("프로퍼티가 있으면 실행기 빈이 올라온다").isNotNull();

		// 표본 열두 곳 가운데 existingPlaceId 가 있는 다섯은 (대부분 이 DB 에 그 장소가 없어 못 달고) 새로 만들지 않고, 나머지 일곱이 새 장소다.
		assertThat(count("SELECT count(*) FROM place WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND dataset_version = ?"))
				.isEqualTo(7);
		assertThat(count("SELECT count(photo_url) FROM place WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND dataset_version = ?"))
				.isEqualTo(3);

		// 구·동이 틀린 줄(같은 번호의 남는 줄·주소와 안 맞는 줄·구 모름)은 아무 흔적도 없다 — 새 장소에 붙은 구·동은 남은 일곱 곳의 것뿐이다.
		// (표본의 existingPlaceId 하나는 마이그레이션이 심어 둔 장소를 가리켜 거기에도 표식이 붙는다 — 새 장소만 골라 센다.)
		List<String> areas = this.jdbcTemplate.queryForList("""
				SELECT f.feature_key FROM place_feature f JOIN place p ON p.place_id = f.place_id
				 WHERE f.feature_type = 'ADMIN_AREA_TAG' AND p.source_type = 'BUSAN_CERTIFIED_FOOD' AND p.dataset_version = ?
				 ORDER BY f.feature_key
				""", String.class, DATASET);
		assertThat(areas).containsExactlyInAnyOrder("강서구/대저2동", "해운대구/좌동", "부산진구/양정동", "해운대구/우동", "부산진구/전포동",
				"영도구/남항동3가", "부산진구/부전동");
		assertThat(count("""
				SELECT count(*) FROM place_feature f JOIN place p ON p.place_id = f.place_id
				 WHERE f.feature_type = 'INTEREST_TAG' AND f.feature_key = 'LOCAL_RESTAURANT'
				   AND p.source_type = 'BUSAN_CERTIFIED_FOOD' AND p.dataset_version = ?
				""")).isEqualTo(7);

		// 사람 확인 목록 — 짝이 없어도 머리줄만 있는 파일을 쓴다(「없었다」와 「안 돌았다」를 가르려고).
		Path csv = input.toAbsolutePath().resolveSibling("same-place-review-BUSAN_CERTIFIED_FOOD-" + DATASET + ".csv");
		assertThat(csv).exists();
		assertThat(Files.readAllLines(csv)).hasSize(1);
	}

	private long count(String sql) {
		return this.jdbcTemplate.queryForObject(sql, Long.class, DATASET);
	}
}
