package com.gabolle.backend.place.loader;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlaceEvidenceStatus;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * 상가정보 한 덩어리를 {@code place} · {@code place_feature} 로 넣는다 — S15P21E201-636.
 *
 * <h2>🔴 이 코드가 없어서 표가 통째로 비어 있었다</h2>
 *
 * 표도, 제약도, 대조표({@code user_place_code_map})도 다 있는데 <b>행을 넣는 운영 코드·배치·
 * 시드·DAG 가 하나도 없었다.</b> 유일한 INSERT 는 테스트 픽스처였다. 그래서 후보가 0건이고,
 * 앱과 엔진 사이의 다른 계약을 다 맞춰도 추천이 나올 수 없었다.
 *
 * <h2>🔴 무엇을 채우고 무엇을 비워 두나</h2>
 *
 * <table border="1">
 * <caption>place_feature 14종 중 이 적재가 건드리는 것</caption>
 * <tr><th>피처</th><th>자료</th><th>채우나</th></tr>
 * <tr><td>{@code CATEGORY_TAG:FOOD}</td><td>대분류가 "음식" 이다</td><td>🟢 전부</td></tr>
 * <tr><td>{@code CATEGORY_TAG:CAFE_HEALING}</td><td>소분류가 "카페" 다</td><td>🟢 카페만</td></tr>
 * <tr><td>{@code CUISINE_TAG:<앱 코드>}</td><td>소분류 + 상호명 → {@link AppFoodVocabulary}</td>
 *     <td>🟢 가를 수 있는 것만</td></tr>
 * <tr><td>{@code ATMOSPHERE_TAG}</td><td>🔴 없다</td><td>비운다</td></tr>
 * <tr><td>{@code POPULARITY_SCORE}</td><td>🔴 <b>출처가 없다.</b> 방문수·리뷰수·조회수 어느
 *     것도 우리에게 없다</td><td>비운다</td></tr>
 * <tr><td>{@code LOCALITY_SCORE}·{@code QUIETNESS_SCORE}·{@code TOURIST_RATIO}</td>
 *     <td>🔴 없다</td><td>비운다</td></tr>
 * <tr><td>{@code SHADE_SCORE}·{@code SLOPE_PERCENT}</td>
 *     <td>도로 구간별 값은 bigData 에 있지만 장소에 붙이는 작업이 없다</td><td>비운다</td></tr>
 * <tr><td>{@code ALLERGEN_TAG}·{@code DIETARY_SUPPORT_TAG}·{@code ACCESSIBILITY_TAG}·
 *     {@code STAIRS_PRESENT}</td>
 *     <td>🔴 안전·접근성. 업종 이름에서 추정하면 안 되고 DB 도 막는다
 *     ({@code ck_place_feature_safety_never_estimated})</td><td>비운다</td></tr>
 * </table>
 *
 * <p>🔴 <b>비운 칸을 그럴듯한 값으로 채우지 않는다.</b> 특히 인기 점수는 채점기가 0.10 을
 * 배정해 둔 자리라 채우고 싶은 유혹이 크다. 없는 것을 만들어 놓고 "추정" 이라 적으면 그건
 * 추정이 아니라 창작이고, 그 창작을 모델이 배운다. 비어 있으면 그 항이 <b>빠질 뿐</b>이다
 * (0 점이 되는 것이 아니다 — {@code BaselineCandidateScorer} 가 그렇게 만들어져 있다).
 *
 * <h2>🔴 {@code category} 에 {@code FOOD} 를 넣는 이유</h2>
 *
 * {@code BaselineCandidateTranslator} 가 사용자의 {@code CATEGORY} 취향 답을
 * {@code place.category} 필터로 쓴다. 앱이 보내는 코드는 {@code SEA_BEACH}·{@code CITY}·
 * {@code CAFE_HEALING}·{@code CULTURE_TEMPLE}·{@code FOOD}·{@code NATURE_WALK} 여섯이고,
 * 그중 <b>{@code FOOD} 하나만</b> 이 자료로 채울 수 있다 — 상가정보 대분류 "음식" 이 앱의
 * "맛집 & 먹거리" 와 같은 것을 가리킨다. 나머지 다섯은 이 자료에 없다. 그 다섯만 고른
 * 사용자는 후보가 0건인데, <b>그것이 사실이다</b> — 그 갈래의 장소를 아직 아무도 안 넣었다.
 *
 * <p>🔴 <b>{@code category} 는 카페도 {@code FOOD} 로 둔다.</b> 카페에 {@code INTEREST_TAG:
 * CAFE_HEALING} 은 붙지만 {@code category} 는 한 칸뿐이라 둘을 같이 못 적는다. 여기서 카페만
 * {@code CAFE_HEALING} 으로 바꾸면 "맛집 & 먹거리" 를 고른 사용자에게서 카페 7,335곳이
 * <b>사라진다.</b> 한 칸에 여러 갈래를 담는 것은 표를 바꾸는 일이라 여기서 혼자 정하지 않는다 —
 * 지금은 {@code CAFE_HEALING} <b>만</b> 고른 사용자의 후보가 0건이다.
 */
@Component
@Profile({ "db", "dev" })
public class SbizPlaceLoader {

	/** {@code place.source_type}. 어디서 온 행인지 되짚을 때 이 값으로 찾는다. */
	public static final String SOURCE_TYPE = "SBIZ";

	/**
	 * 🔴 {@code place.name_ko} 는 VARCHAR(200), {@code address} 는 VARCHAR(300) 이다.
	 * 넘치면 DB 가 거절해서 그 덩어리 전체가 롤백된다 — 한 행 때문에 1,000행이 사라진다.
	 */
	private static final int NAME_MAX = 200;

	private static final int ADDRESS_MAX = 300;

	private final PlaceRepository placeRepository;

	private final PlaceFeatureRepository placeFeatureRepository;

	public SbizPlaceLoader(PlaceRepository placeRepository, PlaceFeatureRepository placeFeatureRepository) {
		this.placeRepository = placeRepository;
		this.placeFeatureRepository = placeFeatureRepository;
	}

	/**
	 * 한 덩어리를 넣는다.
	 *
	 * <h2>🔴 이미 있는 장소는 <b>건너뛴다</b> — 고치지 않는다</h2>
	 *
	 * 같은 파일을 두 번 돌려도 행이 두 배가 되지 않게 하는 것이 여기서 지키는 전부다.
	 * "다음 분기 파일로 갱신한다"(폐업·이전·상호 변경을 어떻게 반영하나)는 별개의 결정이라
	 * 여기서 미리 정하지 않는다. 지금 갱신까지 넣으면, 누구도 정한 적 없는 규칙이 코드에
	 * 박히고 그것이 곧 계약이 된다.
	 *
	 * @param datasetVersion 어느 수집분인가. 🔴 이 값이 없으면 이 장소로 만든 추천이
	 *     {@code VERSION_UNRESOLVED} 로 실패한다
	 * @return 실제로 넣은 장소 수
	 */
	@Transactional
	public int saveChunk(List<SbizRow> rows, String datasetVersion, OffsetDateTime collectedAt) {
		List<UUID> ids = rows.stream().map(row -> placeIdOf(row.storeId())).toList();
		Set<UUID> existing = new HashSet<>();
		this.placeRepository.findAllById(ids).forEach(place -> existing.add(place.getPlaceId()));

		List<Place> places = new ArrayList<>(rows.size());
		List<PlaceFeature> features = new ArrayList<>(rows.size() * 2);
		for (SbizRow row : rows) {
			UUID placeId = placeIdOf(row.storeId());
			if (!existing.add(placeId)) {
				// 이미 있거나(DB) 이 덩어리 안에서 중복된 상가업소번호다.
				continue;
			}
			places.add(Place.imported(placeId, cut(row.displayName(), NAME_MAX), "FOOD",
					cut(row.address(), ADDRESS_MAX), row.lat(), row.lng(),
					SOURCE_TYPE, row.storeId(), collectedAt,
					// 🔴 원천에 "이 사실이 언제 관측됐나" 칸이 없다. 지어내지 않고 비운다 —
					//    수집분 자체는 datasetVersion 이 말해 준다.
					null, datasetVersion));

			// 🔴 앱이 보내는 낱말만 넣는다. 채점이 글자 그대로 비교하므로 여기 다른 낱말을
			//    적으면 그 항이 조용히 0 이 된다 — AppFoodVocabulary 참조.
			// 🔴 갈래는 CATEGORY_TAG 다 (S15P21E201-904). 전에는 INTEREST_TAG 에 넣었는데
			//    그 갈래는 탐색 아코디언의 여덟 낱말이 쓰는 자리라 두 사전이 한 서랍에 섞여
			//    있었다. 지금은 place_feature_code 조회표가 외래키로 갈라 놓는다 — 여기에
			//    탐색 쪽 낱말을 적으면 DB 가 그 자리에서 거부한다.
			for (String category : AppFoodVocabulary.categoryTags(row.subCategory())) {
				features.add(feature(placeId, row.storeId(), "CATEGORY_TAG", category, collectedAt, datasetVersion));
			}
			for (String cuisine : AppFoodVocabulary.cuisineTags(row.subCategory(), row.name())) {
				features.add(feature(placeId, row.storeId(), "CUISINE_TAG", cuisine, collectedAt, datasetVersion));
			}
		}
		if (places.isEmpty()) {
			return 0;
		}
		this.placeRepository.saveAll(places);
		this.placeFeatureRepository.saveAll(features);
		return places.size();
	}

	private static PlaceFeature feature(UUID placeId, String storeId, String featureType, String featureKey,
			OffsetDateTime collectedAt, String datasetVersion) {
		return PlaceFeature.imported(
				featureIdOf(storeId, featureType, featureKey), placeId, featureType, featureKey,
				// 태그형의 값은 "이 표식이 있다" 하나뿐이다.
				"true",
				// 🔴 ESTIMATED — 업종 칸에서 옮긴 것이지 가게에 직접 확인한 것이 아니다.
				//    VERIFIED 로 적으면 나중에 아무도 이 값을 의심하지 않는다.
				PlaceEvidenceStatus.ESTIMATED,
				SOURCE_TYPE, storeId, null, datasetVersion, collectedAt);
	}

	/**
	 * 상가업소번호에서 <b>언제나 같은</b> 장소 아이디를 만든다.
	 *
	 * <p>🔴 무작위 UUID 를 쓰면 같은 파일을 두 번 돌릴 때 같은 가게가 두 행이 된다. 그러면
	 * 후보 수가 부풀어 백분위가 좋아 <b>보이고</b>, 정답이 두 행 중 한 행에만 붙어 나머지 한
	 * 행이 오답으로 학습된다. S15P21E201-712 가 상가정보와 OSM 을 합칠 때 같은 이유로 중복을
	 * 막았다.
	 */
	public static UUID placeIdOf(String storeId) {
		return UUID.nameUUIDFromBytes(("gabolle:place:SBIZ:" + storeId).getBytes(StandardCharsets.UTF_8));
	}

	static UUID featureIdOf(String storeId, String featureType, String featureKey) {
		return UUID.nameUUIDFromBytes(
				("gabolle:place_feature:SBIZ:" + storeId + ":" + featureType + ":" + featureKey)
						.getBytes(StandardCharsets.UTF_8));
	}

	private static String cut(String value, int max) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
	}
}
