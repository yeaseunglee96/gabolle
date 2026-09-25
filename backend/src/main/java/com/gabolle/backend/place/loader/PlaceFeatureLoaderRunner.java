package com.gabolle.backend.place.loader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.function.Consumer;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 조사된 산출물을 {@code place_feature} 에 한 번 넣는다.
 *
 * <pre>
 * git show origin/bigData/dev:bigData/data/staged/place-priceband.ndjson &gt; /tmp/priceband.ndjson
 *
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=dev \
 *   --gabolle.place.loader.price-band=/tmp/priceband.ndjson \
 *   --gabolle.place.loader.dataset-version=staged-busan-202609
 * </pre>
 *
 * <p>혼밥 안심·브레이크타임·라스트오더는 다른 인자로 넣는다.
 *
 * <pre>
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=dev \
 *   --gabolle.place.loader.visitor-facts=/tmp/visitor-facts.ndjson \
 *   --gabolle.place.loader.dataset-version=staged-busan-202609
 * </pre>
 *
 * 산출물 모양은 {@link PlaceFeatureNdjsonReader#readVisitorFacts} 참고. 가격대와 달리
 * {@code namespace} 를 함께 적어야 한다 — TourAPI 출처 장소에도 붙는 산출물이라서다.
 *
 * <p>agy 헤드리스 가격+narrative 통합 조사(S15P21E201-1414)는 또 다른 인자로 넣는다.
 *
 * <pre>
 * git show origin/bigData/dev:bigData/data/staged/place-research-combined.ndjson &gt; /tmp/price-narrative.ndjson
 *
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=dev \
 *   --gabolle.place.loader.price-narrative=/tmp/price-narrative.ndjson \
 *   --gabolle.place.loader.dataset-version=price-narrative-202609
 * </pre>
 *
 * 산출물 모양은 {@link PlaceFeatureNdjsonReader#readPriceNarrative} 참고. 조사가 계속
 * 진행 중이라 산출물이 자란다 — 다시 돌려도 이미 있는 사실은 건드리지 않으므로({@link
 * PlaceFeatureLoader} 의 {@code ON CONFLICT DO NOTHING}), 늘어난 뒷부분만 새로 들어간다.
 *
 * <p>장소 경사는 장소 번호판으로 넣는다(S15P21E201-1625). 🔴 옛 경사 행(관광공사·상가 번호판, 2,682줄)을 먼저
 * 지워야 새 값이 들어간다 — 장소마다 경사 행은 하나뿐이고 적재는 있는 행을 안 건드린다.
 *
 * <pre>
 * -- 1) 옛 행 지우기 (되돌리려면 옛 산출물 place-slope.ndjson · place-slope-sbiz.ndjson 을 place-slope 인자로 다시 넣는다)
 * DELETE FROM gabolle.place_feature
 *  WHERE feature_type = 'SLOPE_PERCENT'
 *    AND source_version IN ('2026-09-16-slope-r200', '2026-09-16-slope-r200-sbiz');
 *
 * -- 2) 새 값 넣기
 * git show origin/bigData/dev:bigData/data/staged/place-slope-by-id.ndjson &gt; /tmp/place-slope-by-id.ndjson
 * java -jar gabolle-backend.jar  *   --spring.profiles.active=dev  *   --gabolle.place.loader.place-slope-by-id=/tmp/place-slope-by-id.ndjson  *   --gabolle.place.loader.dataset-version=2026-09-25-slope-p50-r200
 * </pre>
 *
 * <p>유명세는 여기서 안 넣는다. {@code PopularityLoaderRunner} 가 다른 방식으로 넣으므로,
 * 같은 값을 넣는 경로가 둘이면 어느 쪽이 진짜인지 알 수 없다.
 *
 * <p>API 가 아니라 실행 인자인 것은 적재가 사람이 한 번 하는 일이지 서비스가 제공하는 기능이
 * 아니어서다. 프로퍼티를 안 주면 이 빈은 만들어지지도 않아 평소 기동에 영향이 없다.
 *
 * <p>장소를 먼저 넣어야 한다. 산출물은 값만 들고 있어, 순서가 반대면 대부분이 "장소가 없어
 * 못 넣음" 으로 세어진다 — 그 수가 로그에 남으므로 조용히 성공하지는 않는다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnExpression("'${gabolle.place.loader.price-band:}' != '' "
		+ "or '${gabolle.place.loader.visitor-facts:}' != '' "
		+ "or '${gabolle.place.loader.place-slope:}' != '' "
		+ "or '${gabolle.place.loader.place-slope-by-id:}' != '' "
		+ "or '${gabolle.place.loader.place-quietness:}' != '' "
		+ "or '${gabolle.place.loader.place-locality:}' != '' "
		+ "or '${gabolle.place.loader.place-shade:}' != '' "
		+ "or '${gabolle.place.loader.price-narrative:}' != ''")
public class PlaceFeatureLoaderRunner implements ApplicationRunner {

	public static final String PRICE_BAND_SOURCE_TYPE = "RESEARCH_PRICEBAND";

	public static final String VISITOR_FACTS_SOURCE_TYPE = "RESEARCH_VISITOR_FACTS";

	/** agy 헤드리스 조사가 낸 가격·narrative. 사람이 웹에서 찾은 {@link #PRICE_BAND_SOURCE_TYPE}
	 * 와는 값 목록이 달라 출처를 따로 둔다. */
	public static final String PRICE_NARRATIVE_SOURCE_TYPE = "RESEARCH_PRICE_NARRATIVE";

	/**
	 * 경사만 {@link TourApiPlaceLoader#SOURCE_TYPE} 을 쓴다. 되짚기가 아니라 장소 아이디 때문이다
	 * — {@link PlaceFeatureLoader} 가 이 값을 보고 열쇠를 {@code contentid} 로 읽는다. 다른 이름을
	 * 주면 상가업소번호로 읽어 한 곳도 못 찾고, 아무 오류도 안 난다.
	 */
	public static final String PLACE_SLOPE_SOURCE_TYPE = TourApiPlaceLoader.SOURCE_TYPE;

	/**
	 * 유도한 값은 원천 이름을 빌려 쓰지 않는다. {@code source_type} 은 값이 어디서 왔나를 적는
	 * 칸이고, 되돌리기가 {@code DELETE … WHERE source_type = ?} 라 원천 이름으로 넣으면 실제로
	 * 그 원천에서 온 표식과 한 덩어리가 되어 따로 지울 수 없다.
	 *
	 * <p>열쇠를 무엇으로 읽을지는 이 값과 무관하다 — 그건 {@code Fact.keySource} 가 줄마다 따로
	 * 들고 온다.
	 */
	public static final String DERIVED_QUIETNESS_SOURCE_TYPE = "DERIVED_QUIETNESS";

	public static final String DERIVED_LOCALITY_SOURCE_TYPE = "DERIVED_LOCALITY";

	public static final String DERIVED_SHADE_SOURCE_TYPE = "DERIVED_SHADE";

	/**
	 * 장소 번호판 경사(S15P21E201-1625). 옛 경사 행은 원천 이름(TOURAPI·SBIZ)을 빌려 들어가 있어 원천의 표식과
	 * 한 덩어리였다 — 이 판부터는 유도값 이름으로 따로 둔다.
	 *
	 * <p>🔴 옛 행 위에 그냥 돌리면 안 바뀐다. 장소마다 경사 행은 하나뿐이고({@code uq_place_feature_unkeyed})
	 * 적재는 있으면 건드리지 않는다. 옛 행을 먼저 지운다 — 절차는 이 파일 머리말.
	 */
	public static final String DERIVED_SLOPE_SOURCE_TYPE = "DERIVED_SLOPE";

	private static final Logger LOGGER = LoggerFactory.getLogger(PlaceFeatureLoaderRunner.class);

	/** 한 트랜잭션에 넣는 사실 수. 파일이 작아 한 번에 넣어도 되지만 다른 적재와 규칙을 같게 둔다. */
	private static final int CHUNK = 500;

	private final PlaceFeatureLoader loader;

	private final String priceBandPath;

	private final String visitorFactsPath;

	private final String placeSlopePath;

	private final String placeSlopeByIdPath;

	private final String placeQuietnessPath;

	private final String placeLocalityPath;

	private final String placeShadePath;

	private final String priceNarrativePath;

	private final String datasetVersion;

	public PlaceFeatureLoaderRunner(PlaceFeatureLoader loader,
			@Value("${gabolle.place.loader.price-band:}") String priceBandPath,
			@Value("${gabolle.place.loader.visitor-facts:}") String visitorFactsPath,
			@Value("${gabolle.place.loader.place-slope:}") String placeSlopePath,
			@Value("${gabolle.place.loader.place-slope-by-id:}") String placeSlopeByIdPath,
			@Value("${gabolle.place.loader.place-quietness:}") String placeQuietnessPath,
			@Value("${gabolle.place.loader.place-locality:}") String placeLocalityPath,
			@Value("${gabolle.place.loader.place-shade:}") String placeShadePath,
			@Value("${gabolle.place.loader.price-narrative:}") String priceNarrativePath,
			@Value("${gabolle.place.loader.dataset-version:}") String datasetVersion) {
		this.loader = loader;
		this.priceBandPath = priceBandPath;
		this.visitorFactsPath = visitorFactsPath;
		this.placeSlopePath = placeSlopePath;
		this.placeSlopeByIdPath = placeSlopeByIdPath;
		this.placeQuietnessPath = placeQuietnessPath;
		this.placeLocalityPath = placeLocalityPath;
		this.placeShadePath = placeShadePath;
		this.priceNarrativePath = priceNarrativePath;
		this.datasetVersion = datasetVersion;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (this.datasetVersion == null || this.datasetVersion.isBlank()) {
			// 기본값을 지어내지 않는다 — 어느 산출물이었는지 안 적으면 되짚을 수 없다.
			throw new IllegalStateException(
					"gabolle.place.loader.dataset-version 을 함께 줘야 한다 — 어느 산출물인지 적지 않으면 "
							+ "이 값으로 만든 추천을 나중에 되짚을 수 없다");
		}
		load("가격대", this.priceBandPath, PRICE_BAND_SOURCE_TYPE, PlaceFeatureNdjsonReader::readPriceBands);
		load("방문객 안내", this.visitorFactsPath, VISITOR_FACTS_SOURCE_TYPE, PlaceFeatureNdjsonReader::readVisitorFacts);
		load("장소 경사", this.placeSlopePath, PLACE_SLOPE_SOURCE_TYPE,
				PlaceFeatureNdjsonReader::readPlaceSlopes);
		load("장소 경사(장소 번호)", this.placeSlopeByIdPath, DERIVED_SLOPE_SOURCE_TYPE,
				PlaceFeatureNdjsonReader::readPlaceSlopesById);
		// 조용함·로컬성은 산출물이 0~100 인데 채점기 눈금은 0~1 이다. 나누는 것은 읽는 쪽이
		// 한다 — PlaceFeatureNdjsonReader.readPlaceScores 참고.
		load("장소 조용함", this.placeQuietnessPath, DERIVED_QUIETNESS_SOURCE_TYPE,
				(file, chunkSize, chunkConsumer) -> PlaceFeatureNdjsonReader.readPlaceScores(
						file, "quietnessScore", "QUIETNESS_SCORE", chunkSize, chunkConsumer));
		load("장소 로컬성", this.placeLocalityPath, DERIVED_LOCALITY_SOURCE_TYPE,
				(file, chunkSize, chunkConsumer) -> PlaceFeatureNdjsonReader.readPlaceScores(
						file, "localityScore", "LOCALITY_SCORE", chunkSize, chunkConsumer));
		// 그늘은 기록이 없는 곳이 산출물에 줄로 아예 없고, 여기서도 그런 줄을 만들지 않는다 —
		// 「그늘 0」과 「모름」은 다른 것이라 0 으로 채우면 조사 안 된 곳이 「그늘 없음」이 된다.
		load("장소 그늘", this.placeShadePath, DERIVED_SHADE_SOURCE_TYPE,
				(file, chunkSize, chunkConsumer) -> PlaceFeatureNdjsonReader.readPlaceScores(
						file, "shadeScore", "SHADE_SCORE", chunkSize, chunkConsumer));
		load("가격+narrative 조사", this.priceNarrativePath, PRICE_NARRATIVE_SOURCE_TYPE,
				PlaceFeatureNdjsonReader::readPriceNarrative);
	}

	private void load(String label, String path, String sourceType,
			ReaderCall reader) {
		if (path == null || path.isBlank()) {
			return;
		}
		Path file = Path.of(path);
		if (!Files.isRegularFile(file)) {
			// 없는 파일을 건너뛰지 않는다. 경로를 틀리게 준 실행이 "0곳 적재" 로 조용히 끝나면
			// 값이 안 들어간 것을 아무도 모른다.
			throw new IllegalStateException(label + " 산출물을 찾을 수 없다: " + file.toAbsolutePath());
		}

		OffsetDateTime collectedAt = OffsetDateTime.now();
		long startedAt = System.nanoTime();
		LOGGER.info("{} 적재를 시작한다 — 파일={} 수집분={}", label, file.toAbsolutePath(), this.datasetVersion);

		PlaceFeatureLoader.Saved[] saved = { new PlaceFeatureLoader.Saved(0, 0, 0) };
		PlaceFeatureNdjsonReader.Counts counts = reader.read(file, CHUNK,
				chunk -> saved[0] = saved[0].plus(this.loader.saveChunk(chunk, sourceType, this.datasetVersion,
						collectedAt)));

		long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
		// 넣은 것과 못 넣은 것을 갈라서 남긴다. 합계만 남기면 "장소가 아직 없어 안 들어간 것" 과
		// "두 번째 실행이라 건너뛴 것" 이 구분되지 않는다.
		LOGGER.info("{} 적재를 마쳤다 — {} · {} · {}ms", label, counts, saved[0], elapsedMs);
	}

	/** 읽기 메서드를 {@link #load} 에 넘기는 통로 — 파일 확인·시각·로그를 산출물마다 베끼지 않는다. */
	@FunctionalInterface
	private interface ReaderCall {

		PlaceFeatureNdjsonReader.Counts read(Path file, int chunkSize,
				Consumer<List<PlaceFeatureNdjsonReader.Fact>> chunkConsumer);

	}

}
