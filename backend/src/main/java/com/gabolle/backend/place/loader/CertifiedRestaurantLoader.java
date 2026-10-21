package com.gabolle.backend.place.loader;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.place.domain.CertifiedRestaurant;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * 공공기관이 인증한 부산 식당을 {@code place} 와 {@code place_feature} 에 넣는다 (S15P21E201-2051).
 *
 * <p>적재 흐름 전체(줄마다 하는 일·다는 표식·두 번 돌려도 행이 안 느는 방법)는 엔진 {@link LocalPickLoader} 에 있다 — 카페
 * 적재기({@link LocalCafeLoader})와 같은 엔진이다(S15P21E201-2059). 여기에는 <b>맛집만의 것</b>이 남는다.
 *
 * <ul>
 *   <li>갈래 표식은 {@code LOCAL_RESTAURANT}, 표식의 {@code source_type} 은 {@link #SOURCE_TYPE}</li>
 *   <li>새 장소는 갈래 {@code FOOD}, 출처 {@link #SOURCE_TYPE}, 번호는 {@link #placeIdOf}({@link CertifiedRestaurantRow#newPlace})</li>
 *   <li>🔴 <b>카페로 옮겨진 장소를 맛집으로 되살리지 않는다</b> ({@link #keepsOutOfExplore}) — 운영 SQL
 *       ({@code backend/scripts/local-picks-20261015/})이 장소 갈래가 {@code CAFE_HEALING} 인 곳의 맛집 표식을 카페 표식으로 옮긴다.
 *       이 적재기를 같은 파일로 다시 돌릴 때 맛집 표식이 도로 붙으면 한 장소가 맛집과 카페 양쪽에 선다</li>
 * </ul>
 */
@Component
@Profile({ "db", "dev" })
public class CertifiedRestaurantLoader extends LocalPickLoader<CertifiedRestaurantRow> {

	public static final String SOURCE_TYPE = CertifiedRestaurant.SOURCE_TYPE;

	/** 카페로 옮긴 장소의 갈래 — 앱의 여섯 갈래 가운데 「카페·힐링」. */
	private static final String CAFE_CATEGORY = "CAFE_HEALING";

	public CertifiedRestaurantLoader(PlaceRepository placeRepository, PlaceFeatureRepository placeFeatureRepository,
			SamePlaceGuard samePlaceGuard) {
		super(placeRepository, placeFeatureRepository, samePlaceGuard);
	}

	@Override
	protected CertifiedRestaurant.Kind kind() {
		return CertifiedRestaurant.Kind.RESTAURANT;
	}

	@Override
	protected String markSourceType() {
		return SOURCE_TYPE;
	}

	/**
	 * 장소 갈래가 카페({@code CAFE_HEALING})이고 이미 카페 표식이 달려 있으면 맛집 표식을 달지 않는다. 장소 갈래가 음식이면 두 표식이
	 * 함께 서도 된다 — 음식점을 겸하는 카페가 있다.
	 */
	@Override
	protected boolean keepsOutOfExplore(Place place, List<PlaceFeature> stored) {
		return CAFE_CATEGORY.equals(place.getCategory()) && stored.stream()
				.anyMatch(feature -> "INTEREST_TAG".equals(feature.getFeatureType())
						&& CertifiedRestaurant.Kind.CAFE.exploreKey().equals(feature.getFeatureKey()));
	}

	/** 번호에서 언제나 같은 장소 id — 앞머리가 다른 출처와 겹치지 않게 한다. */
	public static UUID placeIdOf(String sourceId) {
		return UUID.nameUUIDFromBytes(("gabolle:place:" + SOURCE_TYPE + ":" + sourceId).getBytes(StandardCharsets.UTF_8));
	}
}
