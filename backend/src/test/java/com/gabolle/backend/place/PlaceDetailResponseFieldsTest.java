package com.gabolle.backend.place;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.api.PlaceDetailResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code PlaceDetailResponse} 는 이미 배포된 앱이 읽는 계약이라 기존 필드가 하나도 사라지면
 * 안 된다. 칸은 더해지므로 전체를 고정 목록과 맞추지 않고, 기존 열 개의 이름·자리·타입이
 * 그대로인지와 새 칸이 실제로 늘었는지만 본다.
 */
class PlaceDetailResponseFieldsTest {

	/** 최초 계약의 필드 순서 그대로다. */
	private static final List<String> ORIGINAL_FIELD_NAMES = List.of(
			"placeId", "nameKo", "nameEn", "category", "address", "lat", "lng",
			"provenance", "features", "itineraryInclusion");

	private static final List<Class<?>> ORIGINAL_FIELD_TYPES = List.of(
			UUID.class, String.class, String.class, String.class, String.class,
			Double.class, Double.class,
			PlaceDetailResponse.Provenance.class, List.class, PlaceDetailResponse.ItineraryInclusion.class);

	@Test
	@DisplayName("🔴 기존 열 개 필드는 이름·자리·타입이 그대로다")
	void originalFieldsKeepNameOrderAndType() {
		RecordComponent[] components = PlaceDetailResponse.class.getRecordComponents();

		for (int i = 0; i < ORIGINAL_FIELD_NAMES.size(); i++) {
			assertThat(components[i].getName())
					.as("인덱스 %d 의 필드 이름이 바뀌면 안 된다", i)
					.isEqualTo(ORIGINAL_FIELD_NAMES.get(i));
			assertThat(components[i].getType())
					.as("인덱스 %d(%s) 의 필드 타입이 바뀌면 안 된다", i, ORIGINAL_FIELD_NAMES.get(i))
					.isEqualTo(ORIGINAL_FIELD_TYPES.get(i));
		}
	}

	@Test
	@DisplayName("응답에 더해 온 칸이 전부 있다 — 없으면 그 작업이 반영 안 된 것이다")
	void newFieldsWereActuallyAdded() {
		List<String> names = Arrays.stream(PlaceDetailResponse.class.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();

		// 사진에 관한 세 칸을 붙여 두면 사진만 그리고 출처·피사체를 빠뜨리기 어렵다.
		assertThat(names).containsSubsequence(
				"addressEn", "photoUrl", "photoSource", "photoSubject", "openingHours", "priceLevel",
				"resolvedLanguage", "photoLicense", "photos", "localNames");
		assertThat(names.size())
				.as("기존 열 개 + 뒤에 더해 온 열 칸 (열째: 일본어·중국어 이름 localNames, S15P21E201-1859)")
				.isEqualTo(ORIGINAL_FIELD_NAMES.size() + 10);
	}
}
