package com.gabolle.backend.place;

import java.lang.reflect.RecordComponent;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.api.NearbyPlaceItem;
import com.gabolle.backend.place.api.PlaceSummaryResponse;
import com.gabolle.backend.place.domain.Place;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S15P21E201-1120 — 목록 응답이 사진 주소를 <b>실제로 싣는가</b>.
 *
 * <p>칸을 더해 놓고 값을 안 옮기면 화면은 여전히 사진을 못 그린다. 그 종류의 실수는 컴파일도
 * 통과하고 기존 검사도 통과한다 — 그래서 여기서 값이 끝까지 가는지를 본다.
 *
 * <p>DB 를 안 띄운다. 만드는 쪽({@code of}·{@code from})이 값을 옮기는지가 이 검사의 질문이고,
 * 그건 객체 하나로 답할 수 있다.
 */
class ListResponsePhotoFieldsTest {

	private static final String PHOTO = "https://tong.visitkorea.or.kr/cms/haeundae.jpg";

	private static final String SOURCE = "한국관광공사 (공공누리 제1유형)";

	private static Place placeWithPhoto(String photoUrl, String photoSource) {
		return Place.imported(UUID.randomUUID(), "해운대해수욕장", "SEA_BEACH", "부산 해운대구 우동",
				35.1585, 129.1598, "TOURAPI", "264570",
				OffsetDateTime.now(), null, "test", photoUrl, photoSource);
	}

	@Test
	@DisplayName("검색 목록은 사진 주소와 출처를 같이 싣는다")
	void searchSummaryCarriesPhotoUrlAndSource() {
		PlaceSummaryResponse response = PlaceSummaryResponse.of(placeWithPhoto(PHOTO, SOURCE),
				PlaceSummaryResponse.MatchedField.NAME_KO);

		assertThat(response.photoUrl()).isEqualTo(PHOTO);
		assertThat(response.photoSource()).isEqualTo(SOURCE);
	}

	@Test
	@DisplayName("갈래 목록도 같이 싣는다 — 만드는 통로가 둘이라 한쪽만 고치기 쉽다")
	void facetSummaryCarriesPhotoUrlAndSource() {
		PlaceSummaryResponse response = PlaceSummaryResponse.ofFacetMatch(placeWithPhoto(PHOTO, SOURCE));

		assertThat(response.photoUrl()).isEqualTo(PHOTO);
		assertThat(response.photoSource()).isEqualTo(SOURCE);
	}

	@Test
	@DisplayName("근처 장소도 같이 싣고, 기존 hasPhoto 는 그대로 참이다")
	void nearbyItemCarriesPhotoUrlAndKeepsHasPhoto() {
		NearbyPlaceItem item = NearbyPlaceItem.from(placeWithPhoto(PHOTO, SOURCE), 120L);

		assertThat(item.photoUrl()).isEqualTo(PHOTO);
		assertThat(item.photoSource()).isEqualTo(SOURCE);
		// 🔴 지금 화면이 읽는 칸이다. 새 칸을 더하면서 이걸 건드리면 그 화면이 깨진다.
		assertThat(item.hasPhoto()).isTrue();
	}

	@Test
	@DisplayName("사진이 없으면 두 칸 다 비어 나간다 — 빈 문자열을 지어내지 않는다")
	void placesWithoutAPhotoCarryNulls() {
		Place bare = placeWithPhoto(null, null);

		assertThat(PlaceSummaryResponse.of(bare, null).photoUrl()).isNull();
		assertThat(PlaceSummaryResponse.of(bare, null).photoSource()).isNull();
		assertThat(NearbyPlaceItem.from(bare, 10L).photoUrl()).isNull();
		assertThat(NearbyPlaceItem.from(bare, 10L).hasPhoto()).isFalse();
	}

	@Test
	@DisplayName("🔴 새 칸은 맨 뒤에 있다 — 앞자리를 밀면 이미 배포된 앱이 깨진다")
	void newFieldsAreAppendedAtTheEnd() {
		assertThat(lastTwoComponentNames(PlaceSummaryResponse.class))
				.containsExactly("photoUrl", "photoSource");
		assertThat(lastTwoComponentNames(NearbyPlaceItem.class))
				.containsExactly("photoUrl", "photoSource");

		// 앞자리가 그대로인지도 같이 본다 — 맨 뒤 둘만 보면 가운데를 끼워 넣어도 안 걸린다.
		assertThat(componentNames(PlaceSummaryResponse.class).subList(0, 8)).containsExactly(
				"placeId", "nameKo", "nameEn", "category", "address", "lat", "lng", "matchedField");
		assertThat(componentNames(NearbyPlaceItem.class).subList(0, 9)).containsExactly(
				"placeId", "nameKo", "nameEn", "category", "address", "lat", "lng", "distanceM", "hasPhoto");
	}

	private static List<String> componentNames(Class<?> record) {
		return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
	}

	private static List<String> lastTwoComponentNames(Class<?> record) {
		List<String> names = componentNames(record);
		return names.subList(names.size() - 2, names.size());
	}
}
