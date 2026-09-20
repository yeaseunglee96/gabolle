package com.gabolle.backend.place.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;

/**
 * 적재된 영업시간 행을 읽어 답하는 구현.
 *
 * <p>판정 자체는 {@link OpeningHoursValue} 가 하고 여기는 행을 찾아오는 일만 한다 — 값 모양을
 * 읽는 규칙이 DB 에 붙으면 DB 없이 그 규칙을 검사할 수 없게 된다.
 *
 * <p>행이 없으면 예외가 아니라 {@link Answer#NOT_COLLECTED} 다. 값이 없는 장소가 대다수이고
 * 그것이 정상 상태다.
 */
@Component
@Profile({ "db", "dev" })
public class PlaceFeatureOpeningHoursFilter implements OpeningHoursFilterPort {

	/** 영업시간 피처의 갈래. DB 의 {@code ck_place_feature_type} 이 이미 허용하고 있다. */
	public static final String FEATURE_TYPE = "OPENING_HOURS";

	private final PlaceFeatureRepository placeFeatureRepository;

	public PlaceFeatureOpeningHoursFilter(PlaceFeatureRepository placeFeatureRepository) {
		this.placeFeatureRepository = placeFeatureRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public Answer openAt(UUID placeId, OffsetDateTime at) {
		if (placeId == null || at == null) {
			return Answer.NOT_COLLECTED;
		}
		List<PlaceFeature> features = this.placeFeatureRepository.findByPlaceId(placeId);
		for (PlaceFeature feature : features) {
			if (FEATURE_TYPE.equals(feature.getFeatureType())) {
				return OpeningHoursValue.answerAt(feature.getValue(), at);
			}
		}
		return Answer.NOT_COLLECTED;
	}
}
