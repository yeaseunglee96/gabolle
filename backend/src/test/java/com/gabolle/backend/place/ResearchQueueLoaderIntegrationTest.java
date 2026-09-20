package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.loader.ResearchQueueReader;
import com.gabolle.backend.place.loader.SbizPlaceLoader;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

/**
 * 조사 대기열을 장소 표에 넣는다. 대기열 줄의 모양은 실제 파일
 * ({@code bigData/research/data/queue.ndjson})에서 그대로 떠 왔다 — 지어낸 모양으로 재면
 * 실제 파일이 조금만 달라도 통과하는 검사가 된다.
 */
class ResearchQueueLoaderIntegrationTest extends PlacePostgresIntegrationTest {

	private static final String DATASET = "research-busan-test";

	/** 실제 대기열에서 뜬 세 줄. 업종이 서로 달라 표식 매핑도 함께 확인된다. */
	private static final String QUEUE = """
			{"id":"MA0101202511A0024557","name":"편의방","branch":null,\
			"roadAddr":"부산광역시 중구 해관로 64-1","gu":"중구","hdong":"중앙동",\
			"category":"중국집","lon":129.035712861823,"lat":35.1046173038424}
			{"id":"MA010120220810650561","name":"중구기사식당","branch":null,\
			"roadAddr":"부산광역시 중구 동영로 83","gu":"중구","hdong":"영주2동",\
			"category":"백반/한정식","lon":129.0320550994,"lat":35.112320646922}
			{"id":"MA010120220810195109","name":"일미밀면","branch":null,\
			"roadAddr":"부산광역시 중구 대청로99번길 3","gu":"중구","hdong":"대청동",\
			"category":"냉면/밀면","lon":129.030731283245,"lat":35.103168885925}
			""";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private SbizPlaceLoader loader;

	@TempDir
	Path tempDir;

	private Path queueFile;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		this.jdbcTemplate.update("DELETE FROM place_feature WHERE source_type = 'SBIZ'");
		this.jdbcTemplate.update("DELETE FROM place WHERE source_type = 'SBIZ'");
	}

	@BeforeEach
	void writeQueue() throws Exception {
		this.queueFile = this.tempDir.resolve("queue.ndjson");
		Files.writeString(this.queueFile, QUEUE, StandardCharsets.UTF_8);
	}

	/** 러너는 프로퍼티가 있어야 빈이 되므로, 검사는 판독기와 적재기를 직접 잇는다. */
	private int load() {
		AtomicInteger inserted = new AtomicInteger();
		OffsetDateTime collectedAt = OffsetDateTime.now();
		ResearchQueueReader.read(this.queueFile, 500,
				chunk -> inserted.addAndGet(this.loader.saveChunk(chunk, DATASET, collectedAt)));
		return inserted.get();
	}

	@Test
	@DisplayName("완료 기준 — 대기열을 읽으면 장소가 들어간다")
	void theQueueBecomesPlaceRows() {
		assertThat(load()).isEqualTo(3);

		Integer places = this.jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM place WHERE dataset_version = ?", Integer.class, DATASET);
		assertThat(places).isEqualTo(3);
	}

	@Test
	@DisplayName("완료 기준 — 두 번 돌려도 행이 안 는다")
	void loadingTwiceDoesNotDuplicate() {
		load();

		assertThat(load()).isZero();
		Integer places = this.jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM place WHERE dataset_version = ?", Integer.class, DATASET);
		assertThat(places).isEqualTo(3);
	}

	@Test
	@DisplayName("완료 기준 — 모든 행에 수집분이 적힌다")
	void everyRowCarriesTheDatasetVersion() {
		load();

		Integer missing = this.jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM place WHERE source_type = 'SBIZ' AND "
						+ "(dataset_version IS NULL OR dataset_version <> ?)",
				Integer.class, DATASET);
		assertThat(missing).isZero();
	}

	@Test
	@DisplayName("장소 id 를 상가업소번호에서 계산한다 — 나중에 원본 CSV 를 적재해도 겹치지 않는다")
	void placeIdIsDerivedFromTheStoreNumber() {
		load();

		UUID expected = UUID.nameUUIDFromBytes(
				"gabolle:place:SBIZ:MA0101202511A0024557".getBytes(StandardCharsets.UTF_8));
		Integer found = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM place WHERE place_id = ?",
				Integer.class, expected);
		assertThat(found).isEqualTo(1);
	}

	@Test
	@DisplayName("업종에서 나오는 표식이 기존 어휘 그대로 붙는다 — 밀면집에 밀면 태그")
	void featuresUseTheExistingVocabulary() {
		load();

		UUID milmyeon = UUID.nameUUIDFromBytes(
				"gabolle:place:SBIZ:MA010120220810195109".getBytes(StandardCharsets.UTF_8));
		List<String> keys = this.jdbcTemplate.queryForList(
				"SELECT feature_key FROM place_feature WHERE place_id = ? AND feature_type = 'CUISINE_TAG'",
				String.class, milmyeon);
		assertThat(keys).contains("MILMYEON");

		// 모든 장소에 FOOD 갈래 표식이 붙는다 — AppFoodVocabulary 의 규칙이다.
		// 갈래는 INTEREST_TAG 가 아니라 CATEGORY_TAG 다. INTEREST_TAG 는 둘러보기 화면의
		// 여덟 낱말이 쓰는 자리라 섞으면 안 된다.
		Integer food = this.jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM place_feature WHERE feature_type = 'CATEGORY_TAG' "
						+ "AND feature_key = 'FOOD' AND source_version = ?",
				Integer.class, DATASET);
		assertThat(food).isEqualTo(3);
	}

	@Test
	@DisplayName("좌표가 없는 줄은 버린다 — 0 은 좌표가 아니라 안 적혔다는 뜻이다")
	void rowsWithoutCoordinatesAreDropped() throws Exception {
		Files.writeString(this.queueFile, QUEUE
				+ "{\"id\":\"MA9999999999999999\",\"name\":\"좌표없는집\",\"branch\":null,"
				+ "\"roadAddr\":\"부산\",\"gu\":\"중구\",\"hdong\":\"중앙동\","
				+ "\"category\":\"중국집\",\"lon\":0,\"lat\":0}\n", StandardCharsets.UTF_8);

		assertThat(load()).isEqualTo(3);
	}

	@Test
	@DisplayName("형식이 깨진 줄이 있어도 나머지는 들어간다 — 한 줄 때문에 전체를 버리지 않는다")
	void oneBrokenLineDoesNotStopTheRest() throws Exception {
		Files.writeString(this.queueFile, "{이건 JSON 이 아니다\n" + QUEUE, StandardCharsets.UTF_8);

		assertThat(load()).isEqualTo(3);
	}

	@Test
	@DisplayName("지점명이 있으면 이름에 붙는다 — 같은 상호의 다른 가게를 가른다")
	void branchIsAppendedToTheName() throws Exception {
		Files.writeString(this.queueFile,
				"{\"id\":\"MA1111111111111111\",\"name\":\"돼지국밥집\",\"branch\":\"서면점\","
						+ "\"roadAddr\":\"부산광역시 부산진구\",\"gu\":\"부산진구\",\"hdong\":\"부전동\","
						+ "\"category\":\"국밥\",\"lon\":129.0596,\"lat\":35.1579}\n",
				StandardCharsets.UTF_8);

		load();

		UUID placeId = UUID.nameUUIDFromBytes(
				"gabolle:place:SBIZ:MA1111111111111111".getBytes(StandardCharsets.UTF_8));
		String name = this.jdbcTemplate.queryForObject("SELECT name_ko FROM place WHERE place_id = ?",
				String.class, placeId);
		assertThat(name).isEqualTo("돼지국밥집 서면점");
	}

	@Test
	@DisplayName("읽은 줄과 버린 줄을 갈라서 센다 — 합계만 남기면 왜 줄었는지 알 수 없다")
	void countsSeparateWhatWasDropped() throws Exception {
		Files.writeString(this.queueFile, QUEUE
				+ "{\"id\":\"MA9999999999999999\",\"name\":\"좌표없는집\",\"branch\":null,"
				+ "\"roadAddr\":\"부산\",\"gu\":\"중구\",\"hdong\":\"중앙동\","
				+ "\"category\":\"중국집\",\"lon\":0,\"lat\":0}\n"
				+ "{깨진 줄\n", StandardCharsets.UTF_8);

		ResearchQueueReader.Counts counts = ResearchQueueReader.read(this.queueFile, 500, chunk -> {
		});

		assertThat(counts.total()).isEqualTo(5);
		assertThat(counts.usable()).isEqualTo(3);
		assertThat(counts.skippedNoCoordinate()).isEqualTo(1);
		assertThat(counts.skippedBroken()).isEqualTo(1);
	}
}
