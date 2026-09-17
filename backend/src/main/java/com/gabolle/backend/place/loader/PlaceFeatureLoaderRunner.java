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
 * 조사된 가격대 산출물을 {@code place_feature} 에 한 번 넣는다.
 *
 * <h2>쓰는 법</h2>
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
 * <h2>혼밥 안심·브레이크타임·라스트오더 (S15P21E201-453·479)</h2>
 *
 * <pre>
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=dev \
 *   --gabolle.place.loader.visitor-facts=/tmp/visitor-facts.ndjson \
 *   --gabolle.place.loader.dataset-version=staged-busan-202609
 * </pre>
 *
 * 산출물 모양은 {@link PlaceFeatureNdjsonReader#readVisitorFacts} 참고. 가격대와 달리
 * {@code namespace} 를 함께 적어야 한다 — TourAPI 출처 장소에도 붙는 첫 산출물이라서다.
 *
 * <h2>🔴 유명세는 여기서 안 넣는다 (S15P21E201-861)</h2>
 *
 * 이 실행기의 앞선 판은 가격대와 유명세를 함께 넣었다. 유명세는 그 사이
 * S15P21E201-826 이 다른 방식으로 먼저 넣었으므로({@code PopularityLoaderRunner})
 * 여기서는 뺐다. 같은 값을 넣는 경로가 둘이면 어느 쪽이 진짜인지 알 수 없다.
 *
 * <h2>🔴 왜 API 가 아니라 실행 인자인가</h2>
 *
 * {@link SbizLoaderRunner} · {@link ResearchPlaceLoaderRunner} 와 같은 이유다 — 적재는 사람이 한 번
 * 하는 일이지 서비스가 제공하는 기능이 아니다. 프로퍼티를 안 주면 이 빈은 <b>만들어지지도
 * 않으므로</b> 평소 기동에는 아무 영향이 없다.
 *
 * <h2>🔴 장소를 먼저 넣어야 한다</h2>
 *
 * 산출물은 <b>값만</b> 들고 있다. 그 값이 붙을 {@code place} 행은 {@link SbizLoaderRunner} 나
 * {@link ResearchPlaceLoaderRunner} 가 만든다. 순서가 반대면 대부분이 "장소가 없어 못 넣음" 으로
 * 세어지고, 그 수는 로그에 그대로 남는다 — 조용히 성공하지 않는다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnExpression("'${gabolle.place.loader.price-band:}' != '' "
		+ "or '${gabolle.place.loader.visitor-facts:}' != '' "
		+ "or '${gabolle.place.loader.place-slope:}' != '' "
		+ "or '${gabolle.place.loader.place-quietness:}' != '' "
		+ "or '${gabolle.place.loader.place-locality:}' != '' "
		+ "or '${gabolle.place.loader.place-shade:}' != ''")
public class PlaceFeatureLoaderRunner implements ApplicationRunner {

	/** {@code place_feature.source_type} — 가격대가 어디서 왔나. */
	public static final String PRICE_BAND_SOURCE_TYPE = "RESEARCH_PRICEBAND";

	/** {@code place_feature.source_type} — 혼밥 안심·브레이크타임·라스트오더가 어디서 왔나. */
	public static final String VISITOR_FACTS_SOURCE_TYPE = "RESEARCH_VISITOR_FACTS";

	/**
	 * {@code place_feature.source_type} — 장소 경사가 어디서 왔나 (S15P21E201-1047).
	 *
	 * <p>🔴 이 값이 {@link TourApiPlaceLoader#SOURCE_TYPE} 이어야 하는 이유는 되짚기가 아니라
	 * <b>장소 아이디</b> 때문이다. {@link PlaceFeatureLoader} 가 이 값을 보고 열쇠를
	 * {@code contentid} 로 읽는다. 다른 이름을 주면 상가업소번호로 읽어 한 곳도 못 찾는다 —
	 * 그리고 아무 오류도 안 난다.
	 */
	public static final String PLACE_SLOPE_SOURCE_TYPE = TourApiPlaceLoader.SOURCE_TYPE;

	/**
	 * {@code place_feature.source_type} — 조용함·로컬성이 어디서 왔나 (S15P21E201-1167).
	 *
	 * <p>🔴 <b>경사({@link #PLACE_SLOPE_SOURCE_TYPE})처럼 {@code TOURAPI} 를 쓰지 않는다.</b> 이유가
	 * 둘이다.
	 *
	 * <ul>
	 * <li>이 값은 <b>관광공사에서 온 것이 아니다.</b> 도로 등급에서 유도한 우리 계산이다
	 * ({@code bigData/process/place-quietness.mjs}). {@code source_type} 은 "값이 어디서 왔나" 를
	 * 적는 칸이므로 원천 이름을 빌려 쓰면 거짓말이 된다.</li>
	 * <li>🔴 <b>되돌릴 수 없게 된다.</b> 적재 절차서의 되돌리기가
	 * {@code DELETE … WHERE source_type = ?} 인데, {@code TOURAPI} 로 넣으면 관광공사에서 실제로
	 * 온 표식들과 한 덩어리가 되어 <b>조용함만 지울 수 없다.</b></li>
	 * </ul>
	 *
	 * <p>열쇠를 무엇으로 읽을지는 이 값과 <b>무관하다</b> — 그건 {@code Fact.keySource} 가 줄마다
	 * 따로 들고 온다(한 파일에 관광공사와 상가가 섞여 있어도 각각 맞게 붙는다).
	 */
	public static final String DERIVED_QUIETNESS_SOURCE_TYPE = "DERIVED_QUIETNESS";

	/** {@code place_feature.source_type} — 로컬성이 어디서 왔나. {@link #DERIVED_QUIETNESS_SOURCE_TYPE} 과 같은 이유. */
	public static final String DERIVED_LOCALITY_SOURCE_TYPE = "DERIVED_LOCALITY";

	/** {@code place_feature.source_type} — 그늘이 어디서 왔나. {@link #DERIVED_QUIETNESS_SOURCE_TYPE} 과 같은 이유. */
	public static final String DERIVED_SHADE_SOURCE_TYPE = "DERIVED_SHADE";

	private static final Logger LOGGER = LoggerFactory.getLogger(PlaceFeatureLoaderRunner.class);

	/** 한 트랜잭션에 넣는 사실 수. 파일이 1,000줄 아래라 한 번에 넣어도 되지만 규칙을 같게 둔다. */
	private static final int CHUNK = 500;

	private final PlaceFeatureLoader loader;

	private final String priceBandPath;

	private final String visitorFactsPath;

	private final String placeSlopePath;

	private final String placeQuietnessPath;

	private final String placeLocalityPath;

	private final String placeShadePath;

	private final String datasetVersion;

	public PlaceFeatureLoaderRunner(PlaceFeatureLoader loader,
			@Value("${gabolle.place.loader.price-band:}") String priceBandPath,
			@Value("${gabolle.place.loader.visitor-facts:}") String visitorFactsPath,
			@Value("${gabolle.place.loader.place-slope:}") String placeSlopePath,
			@Value("${gabolle.place.loader.place-quietness:}") String placeQuietnessPath,
			@Value("${gabolle.place.loader.place-locality:}") String placeLocalityPath,
			@Value("${gabolle.place.loader.place-shade:}") String placeShadePath,
			@Value("${gabolle.place.loader.dataset-version:}") String datasetVersion) {
		this.loader = loader;
		this.priceBandPath = priceBandPath;
		this.visitorFactsPath = visitorFactsPath;
		this.placeSlopePath = placeSlopePath;
		this.placeQuietnessPath = placeQuietnessPath;
		this.placeLocalityPath = placeLocalityPath;
		this.placeShadePath = placeShadePath;
		this.datasetVersion = datasetVersion;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (this.datasetVersion == null || this.datasetVersion.isBlank()) {
			// 🔴 기본값을 지어내지 않는다. 어느 산출물이었는지 안 적으면 이 값으로 만든 추천을
			//    나중에 되짚을 수 없다 — 다른 두 적재기와 같은 규칙이다.
			throw new IllegalStateException(
					"gabolle.place.loader.dataset-version 을 함께 줘야 한다 — 어느 산출물인지 적지 않으면 "
							+ "이 값으로 만든 추천을 나중에 되짚을 수 없다");
		}
		load("가격대", this.priceBandPath, PRICE_BAND_SOURCE_TYPE, PlaceFeatureNdjsonReader::readPriceBands);
		load("방문객 안내", this.visitorFactsPath, VISITOR_FACTS_SOURCE_TYPE, PlaceFeatureNdjsonReader::readVisitorFacts);
		// 🔴 장소 경사는 추정값이다 — 주변 길에서 유도했다(bigData/docs/PLACE-SLOPE.md).
		//    실측이 아니라는 표시는 PlaceFeatureLoader 가 evidence_status 로 붙인다.
		load("장소 경사", this.placeSlopePath, PLACE_SLOPE_SOURCE_TYPE,
				PlaceFeatureNdjsonReader::readPlaceSlopes);
		// 🔴 조용함·로컬성은 산출물이 0~100 인데 채점기 눈금은 0~1 이다. 읽는 쪽에서 나누고
		//    그 결과를 noiseP90 으로 검산한다 — PlaceFeatureNdjsonReader.readPlaceScores 참고.
		//    적재기 하나가 관광공사 파일과 상가 파일을 다 읽으므로, 두 파일을 각각 한 번씩
		//    같은 속성으로 돌리면 된다(경사처럼 상가 절반을 마이그레이션으로 넣지 않는다).
		load("장소 조용함", this.placeQuietnessPath, DERIVED_QUIETNESS_SOURCE_TYPE,
				(file, chunkSize, chunkConsumer) -> PlaceFeatureNdjsonReader.readPlaceScores(
						file, "quietnessScore", "QUIETNESS_SCORE", chunkSize, chunkConsumer));
		load("장소 로컬성", this.placeLocalityPath, DERIVED_LOCALITY_SOURCE_TYPE,
				(file, chunkSize, chunkConsumer) -> PlaceFeatureNdjsonReader.readPlaceScores(
						file, "localityScore", "LOCALITY_SCORE", chunkSize, chunkConsumer));
		// 🔴 그늘은 덮은 비율이 관광공사 55% · 상가 74% 다. 기록이 없는 곳은 산출물에 줄이
		//    아예 없고, 적재기는 그런 줄을 만들지 않는다 — 「그늘 0」과 「모름」은 다른 것이라
		//    없는 값을 0 으로 채우면, 그늘 많은 길을 찾는 사람에게 조사 안 된 곳을
		//    「그늘 없음」으로 보여주게 된다(S15P21E201-1158 이 접근성에서 정한 선과 같다).
		load("장소 그늘", this.placeShadePath, DERIVED_SHADE_SOURCE_TYPE,
				(file, chunkSize, chunkConsumer) -> PlaceFeatureNdjsonReader.readPlaceScores(
						file, "shadeScore", "SHADE_SCORE", chunkSize, chunkConsumer));
	}

	private void load(String label, String path, String sourceType,
			ReaderCall reader) {
		if (path == null || path.isBlank()) {
			return;
		}
		Path file = Path.of(path);
		if (!Files.isRegularFile(file)) {
			// 🔴 없는 파일을 건너뛰지 않는다. 경로를 틀리게 준 실행이 "0곳 적재" 로 조용히
			//    끝나면, 값이 안 들어간 것을 아무도 모른다.
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
		// 🔴 넣은 것과 못 넣은 것을 갈라서 남긴다. 합계만 남기면 "장소가 아직 없어서 안 들어간 것"
		//    과 "두 번째 실행이라 건너뛴 것" 이 구분되지 않는다.
		LOGGER.info("{} 적재를 마쳤다 — {} · {} · {}ms", label, counts, saved[0], elapsedMs);
	}

	/**
	 * 읽기 메서드를 {@link #load} 에 넘기는 통로.
	 *
	 * <p>지금 읽기 메서드는 {@code readPriceBands} 하나뿐이라 이 사이 단계가 없어도 된다.
	 * 그래도 두는 것은 {@link #load} 가 <b>파일 확인 · 시각 · 로그</b>를 이미 한 벌로 들고
	 * 있어서다 — 산출물이 하나 더 늘 때 그 세 가지를 베끼지 않으려면 이 모양이 맞다.
	 */
	@FunctionalInterface
	private interface ReaderCall {

		PlaceFeatureNdjsonReader.Counts read(Path file, int chunkSize,
				Consumer<List<PlaceFeatureNdjsonReader.Fact>> chunkConsumer);

	}

}
