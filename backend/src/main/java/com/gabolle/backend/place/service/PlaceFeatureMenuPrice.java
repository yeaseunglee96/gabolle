package com.gabolle.backend.place.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;

/**
 * 적재된 {@code MENU_PRICE_WON} 행을 읽어 답하는 구현.
 *
 * <p>{@link PlaceFeatureOpeningHoursFilter} 와 같은 자리·같은 방식이다 — 값 모양을 읽는 규칙은
 * {@link MenuPriceWon} 이 갖고 여기는 행을 찾아오는 일만 한다.
 *
 * <p>행이 없으면 예외가 아니다. 값이 없는 장소가 대다수이고 그것이 정상 상태다 — 2026-09-22
 * 운영 실측으로 장소 6,866곳 중 189곳에만 값이 있다.
 */
@Component
@Profile({ "db", "dev" })
public class PlaceFeatureMenuPrice implements PlaceMenuPricePort {

	private final PlaceFeatureRepository placeFeatureRepository;

	public PlaceFeatureMenuPrice(PlaceFeatureRepository placeFeatureRepository) {
		this.placeFeatureRepository = placeFeatureRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public Map<UUID, Integer> pricesOf(Collection<UUID> placeIds) {
		if (placeIds == null || placeIds.isEmpty()) {
			return Map.of();
		}
		List<PlaceFeature> features = this.placeFeatureRepository.findByPlaceIdIn(placeIds);
		Map<UUID, Integer> wonByPlace = new LinkedHashMap<>();
		for (PlaceFeature feature : features) {
			if (!MenuPriceWon.FEATURE_TYPE.equals(feature.getFeatureType())) {
				continue;
			}
			Integer won = MenuPriceWon.wonOf(feature.getValue());
			// 읽을 수 없는 값은 「없음」과 같게 둔다 — 열쇠를 만들지 않는다.
			if (won != null) {
				wonByPlace.putIfAbsent(feature.getPlaceId(), won);
			}
		}
		return wonByPlace;
	}
}
