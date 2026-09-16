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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.loader.PlaceFeatureLoader;
import com.gabolle.backend.place.loader.PlaceFeatureNdjsonReader;
import com.gabolle.backend.place.loader.ResearchQueueReader;
import com.gabolle.backend.place.loader.SbizPlaceLoader;
import com.gabolle.backend.place.loader.SbizRow;
import com.gabolle.backend.place.loader.TourApiPlaceLoader;
import com.gabolle.backend.place.loader.TourApiPlaceRow;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 조사된 가격대가 진짜 {@code place_feature} 행이 되는지 진짜 PostgreSQL 에서 잰다.
 *
 * <p>🔴 <b>여기서만 잴 수 있는 것이 둘이다.</b> 하나는 JSONB 로 실제로 들어가는가이고, 다른 하나는
 * <b>장소가 없는 번호를 만났을 때 무슨 일이 일어나는가</b>다. 두 산출물의 열쇠는 상가업소번호인데
 * 그 번호로 만든 {@code place} 행이 없으면 외래키가 거부한다 — 그 상황을 예외가 아니라 <b>세어진
 * 건너뜀</b>으로 처리한다는 것이 이 적재기의 계약이고, 그 계약은 진짜 DB 에서만 확인된다.
 */
class PlaceFeatureLoaderIntegrationTest extends PlacePostgresIntegrationTest {

	private static final String DATASET = "staged-test-202609";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private SbizPlaceLoader placeLoader;

	@Autowired
	private PlaceFeatureLoader featureLoader;

	@Autowired
	private TourApiPlaceLoader tourApiPlaceLoader;

	@TempDir
	Path tempDir;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		this.jdbcTemplate.update(
				"DELETE FROM place_feature WHERE source_type IN ('SBIZ', 'RESEARCH_PRICEBAND', 'RESEARCH_VISITOR_FACTS')");
		this.jdbcTemplate.update("DELETE FROM place WHERE source_type IN ('SBIZ', 'TOURAPI')");
	}

	/** 상가업소번호로 장소 하나를 만든다. 값이 붙을 자리가 있어야 하기 때문이다. */
	private void givenPlaces(String... storeIds) {
		List<SbizRow> rows = new ArrayList<>();
		for (String storeId : storeIds) {
			rows.add(new SbizRow(storeId, "어느 집", "", "백반/한정식", "부산광역시 중구 광복로 1", 35.10, 129.03));
		}
		this.placeLoader.saveChunk(rows, DATASET, OffsetDateTime.now());
	}

	/** 관광공사 contentid 로 장소 하나를 만든다 — S15P21E201-453 이 namespace 를 일반화한 대상. */
	private void givenTourApiPlace(String contentId) {
		this.tourApiPlaceLoader.saveChunk(
				List.of(new TourApiPlaceRow(contentId, "12", "A01", null, "가덕도 등대", "부산광역시 강서구 외양포로 10",
						35.10, 129.03, null, null)),
				DATASET, OffsetDateTime.now());
	}

	private Path file(String name, String... lines) {
		try {
			Path path = this.tempDir.resolve(name);
			Files.writeString(path, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
			return path;
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private PlaceFeatureLoader.Saved loadPriceBands(Path path) {
		PlaceFeatureLoader.Saved[] saved = { new PlaceFeatureLoader.Saved(0, 0, 0) };
		PlaceFeatureNdjsonReader.readPriceBands(path, 500,
				chunk -> saved[0] = saved[0].plus(this.featureLoader.saveChunk(chunk, "RESEARCH_PRICEBAND", DATASET,
						OffsetDateTime.now())));
		return saved[0];
	}


	@Test
	@DisplayName("가격대가 PRICE_LEVEL 행이 되고 출처 칸이 함께 채워진다")
	void 가격대가_행이_된다() {
		givenPlaces("MA0101");

		PlaceFeatureLoader.Saved saved = loadPriceBands(
				file("priceband.ndjson", "{\"placeId\":\"MA0101\",\"raw\":\"mid\",\"band\":\"MID\"}"));

		assertThat(saved.inserted()).isEqualTo(1);
		Map<String, Object> row = this.jdbcTemplate.queryForMap("""
				SELECT feature_type, feature_key, value::text AS value, evidence_status,
				       source_type, source_id, source_version
				FROM place_feature WHERE source_type = 'RESEARCH_PRICEBAND'
				""");
		assertThat(row.get("feature_type")).isEqualTo("PRICE_LEVEL");
		// 점수형·값형이라 키가 없다 (ck_place_feature_key_shape).
		assertThat(row.get("feature_key")).isNull();
		assertThat((String) row.get("value")).contains("\"band\": \"MID\"");
		// 🔴 조사원이 적은 것이지 가게에 확인한 것이 아니다.
		assertThat(row.get("evidence_status")).isEqualTo("ESTIMATED");
		// 🔴 값과 함께 출처를 남기는 자리가 place_feature 에 이미 있다 — 비워 두지 않는다.
		assertThat(row.get("source_id")).isEqualTo("MA0101");
		assertThat(row.get("source_version")).isEqualTo(DATASET);
	}

	@Test
	@DisplayName("🔴 장소가 없는 번호는 안 들어가고, 못 넣은 수가 세어진다")
	void 장소가_없으면_세어서_건너뛴다() {
		givenPlaces("MA0101");

		PlaceFeatureLoader.Saved saved = loadPriceBands(file("priceband.ndjson",
				"{\"placeId\":\"MA0101\",\"raw\":\"mid\",\"band\":\"MID\"}",
				// 상가정보 전체를 적재하지 않은 환경에서는 이런 번호가 대부분이다
				"{\"placeId\":\"MA9999\",\"raw\":\"cheap\",\"band\":\"LOW\"}"));

		assertThat(saved.inserted()).isEqualTo(1);
		// 🔴 조용히 넘어가면 "967곳을 넣었다" 고 믿는데 실제로는 그보다 훨씬 적은 상태가 된다.
		assertThat(saved.missingPlace()).isEqualTo(1);
		assertThat(featureCount("RESEARCH_PRICEBAND")).isEqualTo(1);
	}


	@Test
	@DisplayName("🔴 같은 파일을 두 번 돌려도 행이 두 배가 되지 않는다")
	void 두_번_돌려도_안_늘어난다() {
		givenPlaces("MA0101");
		Path path = file("priceband.ndjson", "{\"placeId\":\"MA0101\",\"raw\":\"mid\",\"band\":\"MID\"}");

		PlaceFeatureLoader.Saved first = loadPriceBands(path);
		PlaceFeatureLoader.Saved second = loadPriceBands(path);

		assertThat(first.inserted()).isEqualTo(1);
		assertThat(second.inserted()).isZero();
		// "두 번째라 건너뛴 것" 과 "장소가 없어 못 넣은 것" 은 다른 사실이라 따로 센다.
		assertThat(second.alreadyPresent()).isEqualTo(1);
		assertThat(featureCount("RESEARCH_PRICEBAND")).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 S15P21E201-453 — TourAPI 출처 장소에도 사실을 붙일 수 있다 (전에는 SBIZ 전용이라 못 붙었다)")
	void TourAPI_장소에도_붙는다() {
		givenTourApiPlace("129156");

		PlaceFeatureLoader.Saved[] saved = { new PlaceFeatureLoader.Saved(0, 0, 0) };
		PlaceFeatureNdjsonReader.readVisitorFacts(
				file("visitor-facts.ndjson",
						"{\"namespace\":\"TOURAPI\",\"storeId\":\"129156\",\"featureType\":\"SOLO_FRIENDLY\",\"value\":true}"),
				500,
				chunk -> saved[0] = saved[0]
						.plus(this.featureLoader.saveChunk(chunk, "RESEARCH_VISITOR_FACTS", DATASET, OffsetDateTime.now())));

		assertThat(saved[0].inserted()).isEqualTo(1);
		assertThat(saved[0].missingPlace()).isZero();
		Map<String, Object> row = this.jdbcTemplate.queryForMap("""
				SELECT feature_type, feature_key, value::text AS value, evidence_status, source_type, source_id
				FROM place_feature WHERE source_type = 'RESEARCH_VISITOR_FACTS'
				""");
		assertThat(row.get("feature_type")).isEqualTo("SOLO_FRIENDLY");
		assertThat(row.get("feature_key")).isNull();
		assertThat((String) row.get("value")).isEqualTo("true");
		assertThat(row.get("source_id")).isEqualTo("129156");
	}

	@Test
	@DisplayName("🔴 S15P21E201-948 — 같은 사실을 동시에 두 번 넣어도 예외 없이 하나만 남는다")
	void 동시에_돌려도_예외_없이_하나만_남는다() throws Exception {
		givenPlaces("MA0101");
		PlaceFeatureNdjsonReader.Fact fact = onlyFact(
				file("priceband.ndjson", "{\"placeId\":\"MA0101\",\"raw\":\"mid\",\"band\":\"MID\"}"));

		// 🔴 진짜 경합을 만든다 — 순서대로 실행하면 두 번째 호출은 이미 커밋된 첫 트랜잭션을
		// 보고 ON CONFLICT 로만 걸러지고, 그건 이 로더가 예전에도 처리하던 경우(재실행)다.
		// 이 테스트가 재려는 것은 그게 아니라 "두 트랜잭션이 동시에 열려 있을 때" 다 — 그래서
		// 두 스레드를 같은 순간에 풀어 준다.
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<PlaceFeatureLoader.Saved>> futures = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					go.await(5, TimeUnit.SECONDS);
					return this.featureLoader.saveChunk(List.of(fact), "RESEARCH_PRICEBAND", DATASET,
							OffsetDateTime.now());
				}));
			}
			ready.await(5, TimeUnit.SECONDS);
			go.countDown();

			int totalInserted = 0;
			for (Future<PlaceFeatureLoader.Saved> future : futures) {
				// 🔴 예전 코드(먼저 조회하고 없으면 넣기)라면 여기서 DataIntegrityViolationException
				// 이 튀어나온다 — 두 스레드 다 "없다" 고 읽은 뒤 둘 다 넣으려 하기 때문이다.
				totalInserted += future.get(5, TimeUnit.SECONDS).inserted();
			}

			assertThat(totalInserted).isEqualTo(1);
			assertThat(featureCount("RESEARCH_PRICEBAND")).isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}
	}

	private PlaceFeatureNdjsonReader.Fact onlyFact(Path path) {
		List<PlaceFeatureNdjsonReader.Fact> facts = new ArrayList<>();
		PlaceFeatureNdjsonReader.readPriceBands(path, 500, facts::addAll);
		return facts.get(0);
	}

	/**
	 * 🔴 <b>진짜 산출물 전체를 넣어 몇 행이 되는지 센다.</b>
	 *
	 * <p>두 파일은 이 저장소({@code back/dev})에 없고 {@code bigData/dev} 에 있다. 그래서 경로를
	 * 환경변수로 받는다 — 안 주면 위의 다른 검사들이 배선을 재고, 주면 <b>실제 개수</b>를 잰다.
	 * {@code ResearchQueueRealFileTest} 가 같은 자리를 같은 방법으로 두었다.
	 *
	 * <pre>
	 * git show origin/bigData/dev:bigData/research/data/queue.ndjson        &gt; /tmp/queue.ndjson
	 * git show origin/bigData/dev:bigData/data/staged/place-priceband.ndjson &gt; /tmp/priceband.ndjson
	 *
	 * GABOLLE_PLACE_QUEUE=/tmp/queue.ndjson \
	 * GABOLLE_PRICE_BAND=/tmp/priceband.ndjson \
	 * ./gradlew test --tests '*PlaceFeatureLoaderIntegrationTest'
	 * </pre>
	 *
	 * <p>🔴 <b>장소를 먼저 넣는다.</b> 순서가 반대면 전부 "장소가 없어 못 넣음" 이 된다. 그 수도
	 * 함께 재서, 못 넣은 것이 있으면 그 사실이 실패로 드러나게 한다.
	 */
	@Test
	@DisplayName("🔴 진짜 산출물을 주면 몇 행이 되는지 센다 — 못 넣은 것이 있으면 실패한다")
	void 실제_산출물의_개수를_잰다() {
		Path queue = fromEnv("GABOLLE_PLACE_QUEUE");
		Path band = fromEnv("GABOLLE_PRICE_BAND");
		if (queue == null || band == null) {
			// 🔴 건너뛴 것으로 표시하지 않는다. 파일이 없는 것은 이 검사가 못 도는 이유이지
			//    배선이 안 도는 이유가 아니고, 배선은 위의 검사들이 이미 쟀다.
			return;
		}
		ResearchQueueReader.read(queue, 500,
				chunk -> this.placeLoader.saveChunk(chunk, DATASET, OffsetDateTime.now()));

		PlaceFeatureLoader.Saved prices = loadPriceBands(band);

		System.out.printf("실측 — 가격대 %s%n", prices);
		// 산출물의 열쇠는 상가업소번호이고 짝은 정확히 하나이거나 없다. 하나라도 못 붙었으면
		// 그건 "이었다" 고 말하면 안 되는 상태다.
		assertThat(prices.missingPlace()).isZero();
		assertThat(featureCount("RESEARCH_PRICEBAND")).isEqualTo(prices.inserted());
	}

	private static Path fromEnv(String key) {
		String value = System.getenv(key);
		return (value == null || value.isBlank()) ? null : Path.of(value);
	}

	private long featureCount(String sourceType) {
		Long count = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM place_feature WHERE source_type = ?", Long.class, sourceType);
		return count == null ? 0 : count;
	}

}
