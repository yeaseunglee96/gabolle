package com.gabolle.backend.place.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;

/**
 * 적재된 영업시간·브레이크타임·라스트오더 행을 장소마다 한 번 읽어 영업표로 만드는 구현.
 *
 * <p>{@link PlaceFeatureOpeningHoursFilter}·{@link PlaceFeatureTimeFactFilter} 와 같은 행을 같은 규칙으로 고른다 — 갈래마다
 * 처음 만나는 행, 행이 없으면 「모른다」. 다른 것은 읽는 횟수뿐이다.
 */
@Component
@Profile({ "db", "dev" })
public class PlaceFeatureTimeTable implements PlaceTimeTablePort {

	private final PlaceFeatureRepository placeFeatureRepository;

	public PlaceFeatureTimeTable(PlaceFeatureRepository placeFeatureRepository) {
		this.placeFeatureRepository = placeFeatureRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public PlaceTimeTable tableOf(UUID placeId) {
		if (placeId == null) {
			return Values.NONE;
		}
		Values values = Values.NONE;
		for (PlaceFeature feature : this.placeFeatureRepository.findByPlaceId(placeId)) {
			values = values.with(feature.getFeatureType(), feature.getValue());
		}
		return values;
	}

	/**
	 * 갈래마다 처음 만난 행의 값. {@code has…} 가 거짓이면 그 갈래의 행이 없다는 뜻이고, 참인데 값이 {@code null} 이면
	 * 행은 있는데 값이 비었다는 뜻이다 — 필터들은 뒤의 행을 더 찾지 않고 그 빈 값으로 답하므로 둘을 가른다.
	 *
	 * <p>행(엔터티)이 아니라 값 글자만 든다. 조립 한 번 동안 장소 수만큼 붙들고 있으므로 작을수록 좋다.
	 */
	private record Values(boolean hasOpening, String opening, boolean hasBreakTime, String breakTime,
			boolean hasLastOrder, String lastOrder) implements PlaceTimeTable {

		static final Values NONE = new Values(false, null, false, null, false, null);

		/** 이 갈래를 아직 못 만났으면 이 값으로 채운다. 이미 만났거나 다른 갈래면 그대로다. */
		Values with(String featureType, String value) {
			if (!this.hasOpening && PlaceFeatureOpeningHoursFilter.FEATURE_TYPE.equals(featureType)) {
				return new Values(true, value, this.hasBreakTime, this.breakTime, this.hasLastOrder, this.lastOrder);
			}
			if (!this.hasBreakTime && PlaceFeatureTimeFactFilter.BREAK_TIME_FEATURE_TYPE.equals(featureType)) {
				return new Values(this.hasOpening, this.opening, true, value, this.hasLastOrder, this.lastOrder);
			}
			if (!this.hasLastOrder && PlaceFeatureTimeFactFilter.LAST_ORDER_TIME_FEATURE_TYPE.equals(featureType)) {
				return new Values(this.hasOpening, this.opening, this.hasBreakTime, this.breakTime, true, value);
			}
			return this;
		}

		@Override
		public OpeningHoursFilterPort.Answer openAt(OffsetDateTime at) {
			if (!this.hasOpening || at == null) {
				return OpeningHoursFilterPort.Answer.NOT_COLLECTED;
			}
			return OpeningHoursValue.answerAt(this.opening, at);
		}

		@Override
		public OpeningHoursFilterPort.Answer breakTimeAt(OffsetDateTime at) {
			if (!this.hasBreakTime || at == null) {
				return OpeningHoursFilterPort.Answer.NOT_COLLECTED;
			}
			return TimeFactValue.answerBreakTimeAt(this.breakTime, at);
		}

		@Override
		public OpeningHoursFilterPort.Answer lastOrderAt(OffsetDateTime at) {
			if (!this.hasLastOrder || at == null) {
				return OpeningHoursFilterPort.Answer.NOT_COLLECTED;
			}
			return TimeFactValue.answerLastOrderAt(this.lastOrder, at);
		}
	}
}
