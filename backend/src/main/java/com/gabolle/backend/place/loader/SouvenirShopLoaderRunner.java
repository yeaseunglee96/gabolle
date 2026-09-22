package com.gabolle.backend.place.loader;

import java.time.OffsetDateTime;
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
 * 기념품샵을 손으로 고른 목록만큼 넣는다.
 *
 * <p>목록이 짧은 것은 관광공사 수집본에 기념품샵을 가리키는 신호가 없고, 웹에서 찾은 후보는
 * 좌표를 구할 길이 없어서다 — 이 저장소는 지도 API 응답 저장을 금지하고 다른 지오코딩 출처가
 * 등록돼 있지 않다. 지어낸 좌표는 현장 안내가 엉뚱한 곳을 가리키는 안전 문제가 된다. 그래서
 * 이미 좌표가 붙어 있는 TourAPI 원본에서 성격이 확인되는 것만 넣는다.
 *
 * <p>{@link TourApiExploreFacet} 에 소분류 코드를 더해 자동 분류하지 않는다. 그 코드가 공식
 * 문서에서 정확히 "기념품점" 을 뜻하는지 확인하지 못했다. 확인 안 된 규칙을 자동화하면 앞으로
 * 들어올 그 코드의 모든 항목을 조용히 잘못 분류하지만, 손으로 넣으면 이 한 곳만 틀릴 수 있다.
 *
 * <p>취급 품목도 원천의 상호명을 그대로 옮긴 것이다. 방문 조사 없이 그 이상을 넣지 않는다.
 *
 * <pre>
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=dev \
 *   --gabolle.place.loader.souvenir-shops=true \
 *   --gabolle.place.loader.dataset-version=research-souvenir-202609
 * </pre>
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnExpression("'${gabolle.place.loader.souvenir-shops:}' == 'true'")
public class SouvenirShopLoaderRunner implements ApplicationRunner {

	/** {@code place.source_type}·{@code place_feature.source_type}. */
	public static final String SOURCE_TYPE = "RESEARCH_SOUVENIR";

	private static final Logger LOGGER = LoggerFactory.getLogger(SouvenirShopLoaderRunner.class);

	private final TourApiPlaceLoader placeLoader;

	private final PlaceFeatureLoader featureLoader;

	private final String datasetVersion;

	public SouvenirShopLoaderRunner(TourApiPlaceLoader placeLoader, PlaceFeatureLoader featureLoader,
			@Value("${gabolle.place.loader.dataset-version:}") String datasetVersion) {
		this.placeLoader = placeLoader;
		this.featureLoader = featureLoader;
		this.datasetVersion = datasetVersion;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (this.datasetVersion == null || this.datasetVersion.isBlank()) {
			throw new IllegalStateException(
					"gabolle.place.loader.dataset-version 을 함께 줘야 한다 — 어느 산출물인지 적지 않으면 "
							+ "이 값으로 만든 추천을 나중에 되짚을 수 없다");
		}

		OffsetDateTime collectedAt = OffsetDateTime.now();

		// TourAPI 원본 그대로다. 사진은 저작권 유형이 제3자 저작물이라 뺀다
		// (TourApiPlaceLoader 의 FREE_TO_USE_COPYRIGHT_TYPE 과 같은 판단).
		TourApiPlaceRow aihasi = new TourApiPlaceRow("1013461", "38", "A04", "A04010700",
				"아이하시 (수제젓가락공예)", "부산광역시 중구 국제시장2길 33 (신창동4가)", 35.1024004807, 129.0283125960,
				null, null);

		int placesInserted = this.placeLoader.saveChunk(List.of(aihasi), this.datasetVersion, collectedAt);
		LOGGER.info("기념품샵 장소 적재 — 새로 넣은 곳 {}", placesInserted);

		List<PlaceFeatureNdjsonReader.Fact> facts = List.of(
				new PlaceFeatureNdjsonReader.Fact(aihasi.contentId(), "INTEREST_TAG", "true", "TOURAPI", "SOUVENIR_SHOP"),
				new PlaceFeatureNdjsonReader.Fact(aihasi.contentId(), "SOUVENIR_ITEM_TAG", "true", "TOURAPI",
						"HANDMADE_CHOPSTICKS"));
		PlaceFeatureLoader.Saved saved = this.featureLoader.saveChunk(facts, SOURCE_TYPE, this.datasetVersion,
				collectedAt);
		LOGGER.info("기념품샵 표식 적재 끝 — {}", saved);
		if (saved.missingPlace() > 0) {
			LOGGER.warn("붙일 장소가 없어 넘긴 표식이 {} 개다 — 장소 적재가 먼저 성공했는지 확인한다", saved.missingPlace());
		}
	}
}
