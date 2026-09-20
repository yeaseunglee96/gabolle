package com.gabolle.backend.place;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.api.NearbyPlaceItem;
import com.gabolle.backend.place.api.PlaceDetailResponse;
import com.gabolle.backend.place.api.PlaceSummaryResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 장소 상세에만 있고 목록에는 없는 칸이 새로 생기면 빨개진다. 상세에 칸을 더할 때 목록에도
 * 실을지 한 번 생각하게 만드는 것이 전부이고, "목록이 상세의 칸을 다 가져야 한다" 는 주장은 아니다.
 * 레코드의 칸 이름만 읽으므로 DB 도 Spring 컨텍스트도 필요 없다.
 */
class PlaceListDetailFieldGapTest {

	/** 상세에만 있는 칸을 찍어 둔 것. 상세나 목록의 칸이 바뀌면 여기도 함께 고친다. */
	private static final List<String> DETAIL_ONLY_FIELDS = List.of(
			"features", "itineraryInclusion", "openingHours", "priceLevel", "provenance", "resolvedLanguage");

	@Test
	@DisplayName("🔴 상세에만 있는 칸 목록이 그대로다 — 달라졌으면 목록에도 실을지 정하라는 뜻이다")
	void detailOnlyFieldsAreAccountedFor() {
		Set<String> inLists = new LinkedHashSet<>(componentNames(PlaceSummaryResponse.class));
		inLists.addAll(componentNames(NearbyPlaceItem.class));

		List<String> detailOnly = new ArrayList<>(componentNames(PlaceDetailResponse.class));
		detailOnly.removeAll(inLists);

		List<String> appeared = new ArrayList<>(detailOnly);
		appeared.removeAll(DETAIL_ONLY_FIELDS);
		List<String> nowInLists = new ArrayList<>(DETAIL_ONLY_FIELDS);
		nowInLists.removeAll(detailOnly);

		assertThat(appeared).as("""

				🔴 장소 상세에 칸이 생겼는데 목록에는 없습니다: %s

				   이 칸을 목록(검색·갈래·근처)에도 실어야 합니까?
					 (가) 실어야 한다 — PlaceSummaryResponse·NearbyPlaceItem 과 그 팩토리를 고치십시오.
						  값이 실제로 옮겨지는지는 ListResponseFieldsTest 가 잽니다
					 (나) 상세 전용이다 — 아래 목록에 한 줄 더하십시오

				   🔴 목록에 줄을 더하는 것 자체는 목록 응답에 아무것도 싣지 않습니다.
					  그건 (가) 를 골랐을 때 하는 별개의 일입니다.
				""".formatted(appeared)).isEmpty();

		assertThat(nowInLists).as("""

				목록에 실린 칸입니다: %s

				   PlaceListDetailFieldGapTest 의 DETAIL_ONLY_FIELDS 에서 그 줄을 빼십시오.
				   이 목록은 "상세에만 있는 칸" 의 사진이고, 실렸으면 더는 거기 있으면 안 됩니다.
				""".formatted(nowInLists)).isEmpty();
	}

	private static List<String> componentNames(Class<?> record) {
		return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
	}
}
