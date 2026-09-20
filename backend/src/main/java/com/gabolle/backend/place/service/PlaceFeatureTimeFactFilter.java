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
 * 적재된 브레이크타임·라스트오더 행을 읽어 답하는 구현. {@link PlaceFeatureOpeningHoursFilter} 와
 * 같이 판정은 {@link TimeFactValue} 가 하고 여기는 행을 찾아오는 일만 한다.
 */
@Component
@Profile({ "db", "dev" })
public class PlaceFeatureTimeFactFilter implements PlaceTimeFactFilterPort {

	/** 피처 갈래 값. DB 의 {@code ck_place_feature_type} 이 허용하는 낱말과 같아야 한다. */
	public static final String BREAK_TIME_FEATURE_TYPE = "BREAK_TIME";

	public static final String LAST_ORDER_TIME_FEATURE_TYPE = "LAST_ORDER_TIME";

	private final PlaceFeatureRepository placeFeatureRepository;

	public PlaceFeatureTimeFactFilter(PlaceFeatureRepository placeFeatureRepository) {
		this.placeFeatureRepository = placeFeatureRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public OpeningHoursFilterPort.Answer breakTimeAt(UUID placeId, OffsetDateTime at) {
		return answerAt(placeId, at, BREAK_TIME_FEATURE_TYPE, TimeFactValue::answerBreakTimeAt);
	}

	@Override
	@Transactional(readOnly = true)
	public OpeningHoursFilterPort.Answer lastOrderAt(UUID placeId, OffsetDateTime at) {
		return answerAt(placeId, at, LAST_ORDER_TIME_FEATURE_TYPE, TimeFactValue::answerLastOrderAt);
	}

	private OpeningHoursFilterPort.Answer answerAt(UUID placeId, OffsetDateTime at, String featureType,
			java.util.function.BiFunction<String, OffsetDateTime, OpeningHoursFilterPort.Answer> answerFn) {
		if (placeId == null || at == null) {
			return OpeningHoursFilterPort.Answer.NOT_COLLECTED;
		}
		List<PlaceFeature> features = this.placeFeatureRepository.findByPlaceId(placeId);
		for (PlaceFeature feature : features) {
			if (featureType.equals(feature.getFeatureType())) {
				return answerFn.apply(feature.getValue(), at);
			}
		}
		return OpeningHoursFilterPort.Answer.NOT_COLLECTED;
	}
}
