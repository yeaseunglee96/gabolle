package com.gabolle.backend.place;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import com.gabolle.backend.place.api.PlaceSummaryResponse;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.service.PlaceSearchService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 같은 등급 안에서 대표 관광지가 먼저 나온다(S15P21E201-1995). 10/5 녹화에서 「광안리」를 치면
 * 드론쇼·SUP·횟집이 먼저고 광안리해수욕장은 16번째였다(가나다순). 운영 결과에서 옮긴 이름들이다.
 */
class LandmarkSearchRankTest {

	private final PlaceRepository placeRepository = mock(PlaceRepository.class);

	private final PlaceSearchService service = new PlaceSearchService(this.placeRepository,
			mock(PlaceFeatureRepository.class));

	private static Place place(String nameKo, String nameEn, String category) {
		Place place = Place.imported(UUID.randomUUID(), nameKo, category, "부산", 35.1, 129.0, "TOURAPI",
				UUID.randomUUID().toString(), OffsetDateTime.now(), null, "test", null, null);
		ReflectionTestUtils.setField(place, "nameEn", nameEn);
		return place;
	}

	private List<String> search(String query, Place... found) {
		when(this.placeRepository.searchByName(anyString(), any(Limit.class))).thenReturn(List.of(found));
		return this.service.search(query, null, null, null).items().stream()
				.map(PlaceSummaryResponse::nameKo).toList();
	}

	@Test
	@DisplayName("🔴 「광안리」 → 광안리해수욕장이 맨 앞, 횟집은 관광지 뒤")
	void gwangalliBeachFirst() {
		List<String> names = search("광안리",
				place("광안리 M 드론 라이트쇼", null, "CULTURE_TEMPLE"),
				place("광안리 SUP Zone", "Gwangalli SUP Zone", null),
				place("광안리남매", null, "FOOD"),
				place("광안리대영횟집", null, "FOOD"),
				place("광안리해변 테마거리", "Gwangalli Beach Theme Street", "CULTURE_TEMPLE"),
				place("광안리해수욕장", "Gwangalli Beach", "SEA_BEACH"));

		assertThat(names.get(0)).isEqualTo("광안리해수욕장");
		assertThat(names.indexOf("광안리해변 테마거리")).isLessThan(names.indexOf("광안리남매"));
		assertThat(names.subList(names.size() - 2, names.size())).containsExactlyInAnyOrder("광안리남매", "광안리대영횟집");
	}

	@Test
	@DisplayName("🔴 영어 「Gwangalli」 → Gwangalli Beach 가 맨 앞")
	void englishGwangalliBeachFirst() {
		List<String> names = search("Gwangalli",
				place("광안리 SUP Zone", "Gwangalli SUP Zone", null),
				place("광안리언양불고기부산집", "Gwangalli Eonyang Bulgogi Busanjip", "FOOD"),
				place("광안리해수욕장", "Gwangalli Beach", "SEA_BEACH"));

		assertThat(names.get(0)).isEqualTo("광안리해수욕장");
	}

	@Test
	@DisplayName("「해운대」 → 해운대해수욕장, 「감천」 → 감천문화마을이 맨 앞")
	void otherLandmarks() {
		assertThat(search("해운대",
				place("해운대 관광특구", null, "CULTURE_TEMPLE"),
				place("해운대 글로리콘도", null, "LODGING"),
				place("해운대시장", null, "CITY"),
				place("해운대해수욕장", "Haeundae Beach", "SEA_BEACH")).get(0)).isEqualTo("해운대해수욕장");
		assertThat(search("감천",
				place("감천동 느티나무", null, "NATURE_WALK"),
				place("감천문화마을", "Gamcheon Culture Village", "CULTURE_TEMPLE"),
				place("감천횟집", null, "FOOD")).get(0)).isEqualTo("감천문화마을");
	}

	@Test
	@DisplayName("정확일치는 여전히 맨 앞 — 「태종대」")
	void exactStillFirst() {
		assertThat(search("태종대",
				place("태종대교회", null, "CULTURE_TEMPLE"),
				place("태종대", null, "SEA_BEACH")).get(0)).isEqualTo("태종대");
	}
}
