package com.gabolle.backend.place.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;

/** 적재된 음식 종류 표식({@code CUISINE_TAG})을 한 번에 읽어 답한다 — S15P21E201-1635. */
@Component
@Profile({ "db", "dev" })
public class PlaceFeatureDessertOnly implements PlaceDessertOnlyPort {

	static final String CUISINE_TAG = "CUISINE_TAG";

	static final String DESSERT = "CAFE_DESSERT";

	private final PlaceFeatureRepository placeFeatureRepository;

	public PlaceFeatureDessertOnly(PlaceFeatureRepository placeFeatureRepository) {
		this.placeFeatureRepository = placeFeatureRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public Set<UUID> dessertOnly(Collection<UUID> placeIds) {
		if (placeIds == null || placeIds.isEmpty()) {
			return Set.of();
		}
		// 장소마다 「디저트만 봤나」 — 디저트가 아닌 음식 종류가 하나라도 나오면 거짓으로 굳는다.
		Map<UUID, Boolean> onlyDessert = new HashMap<>();
		for (PlaceFeature feature : this.placeFeatureRepository.findByPlaceIdIn(placeIds)) {
			if (CUISINE_TAG.equals(feature.getFeatureType())) {
				onlyDessert.merge(feature.getPlaceId(), DESSERT.equals(feature.getFeatureKey()), Boolean::logicalAnd);
			}
		}
		return onlyDessert.entrySet().stream()
				.filter(Map.Entry::getValue)
				.map(Map.Entry::getKey)
				.collect(Collectors.toUnmodifiableSet());
	}
}
