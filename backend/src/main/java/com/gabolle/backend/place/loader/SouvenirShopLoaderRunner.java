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
 * 기념품샵을 손으로 고른 목록만큼 넣는다 — S15P21E201-471.
 *
 * <h2>🔴 왜 목록이 하나뿐인가</h2>
 *
 * {@link TourApiExploreFacet} 문서가 이미 실측해 뒀다 — 수집본 656곳(음식 제외 관광공사
 * 자료)의 이름·분류를 다 훑어도 기념품샵을 가리키는 신호가 없다("SOUVENIR_SHOP: 신호가
 * 없다, 0곳"). 2026-09-16 에 웹 검색으로 후보를 더 찾았지만(BIG shop·유행통신 등),
 * <b>좌표를 구할 방법이 없어서</b> 넣지 못했다 — 이 저장소는 카카오·네이버 지도 API 응답
 * 저장을 금지하고(`bigData/CLAUDE.md` 1절), 그 밖의 지오코딩 출처가 등록돼 있지 않다.
 * 지어낸 좌표를 넣으면 현장 안내가 엉뚱한 곳을 가리키는 안전 문제가 된다.
 *
 * <p>대신 <b>이미 공공누리 라이선스로 좌표가 붙어 있는</b> TourAPI 원본에서 찾았다 —
 * {@code contenttypeid=38}(쇼핑) 94곳 중 이름과 소분류(cat3=A04010700, 공방)로 실제
 * 기념품 성격이 확인되는 것은 <b>아이하시(수제젓가락공예) 하나</b>였다. 나머지는 전통시장·
 * 백화점·아울렛이라 "기념품샵" 으로 볼 수 없다.
 *
 * <h2>왜 이름으로 갈래를 정하지 않았나 — 코드로 정한 것도 아니다</h2>
 *
 * {@link TourApiExploreFacet}(자동 분류)에 {@code cat3=A04010700} 을 추가해 자동으로
 * {@code SOUVENIR_SHOP} 이 되게 하지 않았다 — 그 코드가 공식 문서에서 정확히 "기념품점" 을
 * 뜻하는지 확인하지 못했다("공방" 일 가능성이 있다). <b>확인 안 된 분류 규칙을 자동화
 * 하는 것</b>과 <b>확인된 사실 하나를 손으로 넣는 것</b>은 위험이 다르다 — 앞의 것은 앞으로
 * 들어올 모든 A04010700 항목을 조용히 잘못 분류할 수 있지만, 이것은 이 한 곳만 틀릴 수 있다.
 *
 * <h2>취급 품목도 지어내지 않았다</h2>
 *
 * {@code HANDMADE_CHOPSTICKS} 는 원천의 상호명("수제젓가락공예")을 그대로 옮긴 것이다.
 * 실제 방문 조사 없이는 그 이상(가격대·재질 등)을 넣지 않는다.
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

		// 🔴 TourAPI 원본 그대로다 — 사진(firstimage)은 저작권 유형이 Type3(제3자 저작물)라 뺀다
		//    (TourApiPlaceLoader 의 FREE_TO_USE_COPYRIGHT_TYPE 과 같은 판단).
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
