package com.gabolle.backend.facility.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import com.gabolle.backend.facility.repository.FacilityRepository;
import com.gabolle.backend.facility.support.FacilityPostgresIntegrationTest;

/**
 * 적재기 실행기를 <b>앱이 뜨는 순간 그대로</b> 돌려 본다 (S15P21E201-2052) — 네 프로퍼티를 주면 기동 끝에 파일 넷을 읽어 DB 에 넣는다. 단위
 * 시험은 읽기와 한 덩어리 넣기를 따로 보고, 이 시험은 그 둘을 잇는 실행기(파일 검사 → 종류별 넣기 → 두 번째는 아무 일도 없음)를 본다.
 * 운영 절차가 그대로 따라 쓰는 명령({@code --gabolle.facility.loader.…})과 같은 프로퍼티로 넣는다.
 *
 * <p>표본은 실제 파일에서 문제 줄을 그대로 뽑은 것이다 — 주차장 18 · 약국 11 · 화장실 14(주소가 구까지뿐인 1곳을 건너뛰어 13) · 물품보관함 9.
 */
@TestPropertySource(properties = "gabolle.facility.loader.dataset-version=" + FacilityLoaderRunnerIntegrationTest.DATASET)
class FacilityLoaderRunnerIntegrationTest extends FacilityPostgresIntegrationTest {

	static final String DATASET = "facility-runner-test";

	private static Path dir;

	@DynamicPropertySource
	static void runnerProperties(DynamicPropertyRegistry registry) throws IOException {
		dir = Files.createTempDirectory("facility-runner");
		for (String kind : new String[] { "parking", "pharmacy", "toilet", "locker" }) {
			Files.copy(Path.of("src/test/resources/facility/" + kind + "-sample.ndjson"), dir.resolve(kind + ".ndjson"));
			registry.add("gabolle.facility.loader." + kind, () -> dir.resolve(kind + ".ndjson").toString());
		}
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired(required = false)
	private FacilityLoaderRunner runner;

	@Autowired
	private FacilityLoader loader;

	@Autowired
	private FacilityRepository repository;

	@BeforeEach
	void ensureLoaded() {
		// 컨텍스트는 클래스 안에서 하나라 앞 시험이 지운 뒤 다시 채워 둔다 — 어느 시험이 먼저 돌아도 같은 상태에서 시작한다.
		if (count("") == 0) {
			this.runner.run(new DefaultApplicationArguments());
		}
	}

	@AfterEach
	void tearDown() {
		this.jdbcTemplate.update("DELETE FROM facility WHERE dataset_version LIKE ?", DATASET + "%");
	}

	private long count(String kindFilter) {
		return kindFilter.isEmpty()
				? this.jdbcTemplate.queryForObject("SELECT count(*) FROM facility WHERE dataset_version = ?", Long.class, DATASET)
				: this.jdbcTemplate.queryForObject(
						"SELECT count(*) FROM facility WHERE dataset_version = ? AND kind = ?", Long.class, DATASET, kindFilter);
	}

	private OffsetDateTime latestCollectedAt() {
		return this.jdbcTemplate.queryForObject("SELECT max(collected_at) FROM facility WHERE dataset_version = ?",
				OffsetDateTime.class, DATASET);
	}

	@Test
	@DisplayName("🔴 프로퍼티만 주면 기동 끝에 표본 넷이 들어간다 — 주소가 구까지뿐인 화장실 한 곳은 안 들어가고, 물품보관함 위치는 역 좌표다")
	void runnerLoadsTheFourSamplesAtStartup() {
		assertThat(this.runner).as("프로퍼티가 있으면 실행기 빈이 올라온다").isNotNull();

		assertThat(count("PARKING")).isEqualTo(18);
		assertThat(count("PHARMACY")).isEqualTo(11);
		assertThat(count("TOILET")).isEqualTo(13);
		assertThat(count("LOCKER")).isEqualTo(9);
		assertThat(this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM facility WHERE source_id = '202433700000100351'", Long.class)).isZero();

		// 다대포해수욕장 물품보관함 — 파일 좌표(35.1019, 128.9771)가 아니라 역 자료의 좌표(다대포해수욕장역 35.0487, 128.9641)
		Double lat = this.jdbcTemplate.queryForObject(
				"SELECT lat FROM facility WHERE kind = 'LOCKER' AND source_id = 'BTC-1-다대포해수욕장-1'", Double.class);
		assertThat(lat).isBetween(35.04, 35.06);
	}

	@Test
	@DisplayName("🔴 두 번째 실행은 아무 일도 하지 않는다 — 행 수도 수집 시각도 그대로다")
	void secondRunChangesNothing() {
		long before = count("");
		OffsetDateTime collectedBefore = latestCollectedAt();

		this.runner.run(new DefaultApplicationArguments());

		assertThat(count("")).isEqualTo(before).isEqualTo(51);
		assertThat(latestCollectedAt().toInstant()).isEqualTo(collectedBefore.toInstant());
	}

	@Test
	@DisplayName("파일 하나만 줘도 돈다 — 약국만 다시 넣고 나머지는 손대지 않는다")
	void aSingleFileReloadsOnlyThatKind() {
		long parkingBefore = count("PARKING");
		FacilityLoaderRunner pharmacyOnly = new FacilityLoaderRunner(this.loader, this.repository, "",
				dir.resolve("pharmacy.ndjson").toString(), "", "", DATASET);

		pharmacyOnly.run(new DefaultApplicationArguments());

		assertThat(count("PHARMACY")).isEqualTo(11);
		assertThat(count("PARKING")).isEqualTo(parkingBefore);
	}

	@Test
	@DisplayName("수집분 버전이 없으면 멈춘다 — 어느 수집분인지 모르면 되돌릴 수 없다")
	void datasetVersionIsRequired() {
		FacilityLoaderRunner noVersion = new FacilityLoaderRunner(this.loader, this.repository, "",
				dir.resolve("pharmacy.ndjson").toString(), "", "", " ");

		assertThatThrownBy(() -> noVersion.run(new DefaultApplicationArguments())).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("dataset-version");
	}

	@Test
	@DisplayName("파일이 없으면 멈춘다 — 어느 종류의 파일이 없는지 이름에 담긴다")
	void missingFileStops() {
		FacilityLoaderRunner missing = new FacilityLoaderRunner(this.loader, this.repository, "",
				dir.resolve("pharmacy.ndjson").toString(), dir.resolve("nope.ndjson").toString(), "", DATASET + "-x");

		assertThatThrownBy(() -> missing.run(new DefaultApplicationArguments())).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("화장실");
	}

	@Test
	@DisplayName("🔴 한 파일이라도 틀리면 한 행도 안 넣는다 — 앞선 멀쩡한 파일도 넣기 전에 검사에서 멈춘다")
	void aBrokenFileStopsBeforeAnythingIsWritten() throws IOException {
		Path broken = Files.writeString(dir.resolve("broken-toilet.ndjson"), "{\"sourceId\":\"x\",\"모르는칸\":1}\n");
		String version = DATASET + "-broken";
		FacilityLoaderRunner runner = new FacilityLoaderRunner(this.loader, this.repository, "",
				dir.resolve("pharmacy.ndjson").toString(), broken.toString(), "", version);

		assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments())).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("1번째 줄");

		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM facility WHERE dataset_version = ?", Long.class,
				version)).isZero();
	}
}
