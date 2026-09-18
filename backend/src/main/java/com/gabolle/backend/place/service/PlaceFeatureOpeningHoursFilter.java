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
 * 적재된 영업시간 행을 읽어 답하는 구현 — S15P21E201-852.
 *
 * <p>{@code NotCollectedOpeningHoursFilter}(무엇을 물어도 "수집 안 했다" 만 답하던 구현)를
 * 대신한다. 그 클래스 주석이 <i>"영업시간 칸이 생기면 그것을 읽는 구현을 하나 더하고 이 클래스를
 * 지운다"</i> 고 적어 뒀고, 이것이 그 구현이다.
 *
 * <p>판정 자체는 {@link OpeningHoursValue} 가 한다. 여기는 <b>행을 찾아오는 일</b>만 한다 —
 * 값 모양을 읽는 규칙이 DB 에 붙으면 노트북에 DB 가 없는 사람은 그 규칙을 검사할 수 없게 된다.
 *
 * <p>🔴 행이 없으면 {@link Answer#NOT_COLLECTED} 다. 예외를 던지지 않는다 — 지금 값이 있는
 * 장소는 관광공사에서 넣은 268곳뿐이고, 상가정보 2,355곳은 물어보면 전부 이 답이 나온다.
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
