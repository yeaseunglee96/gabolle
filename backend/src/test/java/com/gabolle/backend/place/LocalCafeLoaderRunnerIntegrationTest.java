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

import com.gabolle.backend.place.loader.LocalCafeLoaderRunner;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

/**
 * 카페 적재기 실행기를 **앱이 뜨는 순간 그대로** 돌려 본다 (S15P21E201-2059) — 프로퍼티를 주면 기동 끝에 파일을 읽어 DB 에 넣는다.
 * 읽기와 한 덩어리 넣기는 따로 시험하고, 이 시험은 그 둘을 잇는 실행기(덩어리 나누기·건너뛴 줄 기록·사람 확인 목록 파일)를 본다.
 * 파일은 실제 250줄 파일의 모양을 따른 열한 줄 표본이고, 운영 절차가 그대로 따라 쓰는 명령({@code --gabolle.place.loader.local-cafes})과
 * 같은 프로퍼티로 넣는다.
 *
 * <p>표본의 {@code existingPlaceId} 둘은 이 시험 DB 에 없는 장소를 가리킨다 — 「다른 DB 의 번호로 만든 파일」 경로(표식을 못 달고 센다)를 겸해 본다.
 */
@TestPropertySource(properties = "gabolle.place.loader.dataset-version=" + LocalCafeLoaderRunnerIntegrationTest.DATASET)
class LocalCafeLoaderRunnerIntegrationTest extends PlacePostgresIntegrationTest {

	static final String DATASET = "cafes-runner-test";

	/** 실행기가 입력 파일 옆에 쓰는 사람 확인 목록(CSV)도 확인하려고, 표본을 임시 폴더로 복사해 쓴다. */
	private static Path input;

	@DynamicPropertySource
	static void runnerProperty(DynamicPropertyRegistry registry) throws IOException {
		Path dir = Files.createTempDirectory("cafes-runner");
		input = Files.copy(Path.of("src/test/resources/place/local-cafes-sample.ndjson"), dir.resolve("local-cafes.ndjson"));
		registry.add("gabolle.place.loader.local-cafes", () -> input.toString());
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired(required = false)
	private LocalCafeLoaderRunner runner;

	@AfterEach
	void tearDown() {
		this.jdbcTemplate.update("DELETE FROM place_feature WHERE source_type = 'BUSAN_LOCAL_CAFE' AND source_version = ?", DATASET);
		this.jdbcTemplate.update("DELETE FROM place WHERE dataset_version = ? AND source_type IN ('TOURAPI', 'REDTABLE')", DATASET);
	}

	@Test
	@DisplayName("🔴 프로퍼티만 주면 기동 끝에 표본이 들어간다 — 새 카페 여섯, 건너뛴 줄과 없는 장소를 가리키는 줄은 안 들어가고, 표식은 장소마다 붙는다")
	void runnerLoadsTheSampleAtStartup() throws IOException {
		assertThat(this.runner).as("프로퍼티가 있으면 실행기 빈이 올라온다").isNotNull();

		// 표본 열한 줄 가운데 건너뛴 둘(구·동 모름 · 주소와 구가 안 맞음)을 빼고 아홉이 읽힌다. existingPlaceId 가 있는 둘은 이 DB 에 그 장소가 없어
		// 못 달고, 같은 이름이 12m 앞뒤인 둘(시험제일다방)은 장소 하나이므로 새 카페는 여섯이다.
		assertThat(count("SELECT count(*) FROM place WHERE dataset_version = ? AND source_type IN ('TOURAPI', 'REDTABLE')")).isEqualTo(6);
		assertThat(count("SELECT count(*) FROM place WHERE dataset_version = ? AND source_type = 'TOURAPI'")).isEqualTo(3);
		assertThat(count("SELECT count(*) FROM place WHERE dataset_version = ? AND source_type = 'REDTABLE'")).isEqualTo(3);
		assertThat(count("SELECT count(*) FROM place WHERE dataset_version = ? AND category = 'CAFE_HEALING'")).isEqualTo(6);
		// 사진은 관광공사 줄 둘에만 있다 — 레드테이블 줄은 사진이 없다.
		assertThat(count("SELECT count(photo_url) FROM place WHERE dataset_version = ? AND source_type = 'TOURAPI'")).isEqualTo(2);
		assertThat(count("SELECT count(photo_url) FROM place WHERE dataset_version = ? AND source_type = 'REDTABLE'")).isZero();

		// 구·동이 틀린 줄(구 모름 · 주소와 안 맞음)은 아무 흔적도 없다 — 새 장소에 붙은 구·동은 남은 여섯 곳의 것뿐이다.
		List<String> areas = this.jdbcTemplate.queryForList("""
				SELECT f.feature_key FROM place_feature f JOIN place p ON p.place_id = f.place_id
				 WHERE f.feature_type = 'ADMIN_AREA_TAG' AND p.dataset_version = ? AND p.source_type IN ('TOURAPI', 'REDTABLE')
				 ORDER BY f.feature_key
				""", String.class, DATASET);
		assertThat(areas).containsExactlyInAnyOrder("기장군/기장읍", "수영구/남천동", "사하구/하단동", "부산진구/부전동", "강서구/대저1동", "중구/남포동");
		assertThat(count("""
				SELECT count(*) FROM place_feature f JOIN place p ON p.place_id = f.place_id
				 WHERE f.feature_type = 'INTEREST_TAG' AND f.feature_key = 'LOCAL_CAFE'
				   AND p.dataset_version = ? AND p.source_type IN ('TOURAPI', 'REDTABLE')
				""")).isEqualTo(6);
		// 레드테이블 표식은 네 곳에 하나씩이다 — 일반 한 곳, 방송·수상 세 곳(관광공사와 겹친 곳 · 단독 · 쌍둥이를 합친 곳). 쌍둥이 둘은 장소 하나라
		// 같은 표식을 한 번만 가진다. 관광공사 표식은 관광공사 줄 셋에 하나씩이다.
		assertThat(count("SELECT count(*) FROM place_feature WHERE source_type = 'BUSAN_LOCAL_CAFE' AND source_version = ? AND feature_key = 'REDTABLE_FEATURED'"))
				.isEqualTo(4);
		assertThat(count("SELECT count(*) FROM place_feature WHERE source_type = 'BUSAN_LOCAL_CAFE' AND source_version = ? AND feature_key = 'KTO_CAFE'"))
				.isEqualTo(3);

		// 사람 확인 목록 — 짝이 없어도 머리줄만 있는 파일을 쓴다(「없었다」와 「안 돌았다」를 가르려고).
		Path csv = input.toAbsolutePath().resolveSibling("same-place-review-BUSAN_LOCAL_CAFE-" + DATASET + ".csv");
		assertThat(csv).exists();
		assertThat(Files.readAllLines(csv)).hasSize(1);
	}

	private long count(String sql) {
		return this.jdbcTemplate.queryForObject(sql, Long.class, DATASET);
	}
}
