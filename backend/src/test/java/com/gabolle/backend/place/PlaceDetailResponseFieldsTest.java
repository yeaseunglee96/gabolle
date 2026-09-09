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
 * S15P21E201-476 완료 기준 — "기존 필드가 하나도 사라지지 않는다".
 *
 * <p>{@code PlaceDetailResponse} 는 이미 배포된 앱이 읽는 계약이다. 이 티켓은 칸을 <b>더하는</b>
 * 작업이라 {@code SharedItineraryResponseWhitelistTest} 처럼 전체를 고정 목록과 정확히 맞추지는
 * 않는다 — 대신 기존 열 개의 이름·자리·타입이 그대로인지, 그리고 실제로 새 칸이 늘었는지를 본다.
 * {@code SharedItineraryResponseWhitelistTest} 를 참고해 같은 리플렉션 방식으로 만들었다.
 */
class PlaceDetailResponseFieldsTest {

	/** -476 최초 계약의 필드 순서 그대로다. 자리가 바뀌면(순서가 바뀌면) 이 테스트가 빨개진다. */
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
	@DisplayName("이번 작업이 더한 다섯 칸이 실제로 있다 — 없으면 이번 작업이 반영 안 된 것이다")
	void newFieldsWereActuallyAdded() {
		List<String> names = Arrays.stream(PlaceDetailResponse.class.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();

		assertThat(names).containsSubsequence(
				"addressEn", "photoUrl", "photoSource", "openingHours", "priceLevel", "resolvedLanguage");
		assertThat(names.size())
				.as("기존 열 개 + 새 여섯 칸")
				.isEqualTo(ORIGINAL_FIELD_NAMES.size() + 6);
	}
}
