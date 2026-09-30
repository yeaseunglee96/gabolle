package com.gabolle.backend.place;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import com.gabolle.backend.place.api.PlacePageResponse;
import com.gabolle.backend.place.api.PlaceSummaryResponse;
import com.gabolle.backend.place.api.PlaceSummaryResponse.MatchedField;
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
 * 관광공사 일본어·중국어 공식 이름으로 찾았을 때의 순위와 「어느 칸이 걸렸나」(S15P21E201-1875).
 *
 * <p>어느 행을 가져오는지는 {@code PlaceSearchIntegrationTest} 가 진짜 PostgreSQL 에서 본다. 여기서는
 * 가져온 뒤의 순서만 본다 — DB 가 없는 PC 에서도 돌아야 순위 규칙이 어긋난 것을 바로 안다.
 */
class LocalNameSearchRankTest {

	private final PlaceRepository placeRepository = mock(PlaceRepository.class);

	private final PlaceSearchService service = new PlaceSearchService(this.placeRepository,
			mock(PlaceFeatureRepository.class));

	private static Place place(String nameKo, String nameEn, String ja, String hans, String hant) {
		Place place = Place.imported(UUID.randomUUID(), nameKo, "ATTRACTION", "부산", 35.1, 129.0, "TOURAPI",
				UUID.randomUUID().toString(), OffsetDateTime.now(), null, "test", null, null);
		ReflectionTestUtils.setField(place, "nameEn", nameEn);
		ReflectionTestUtils.setField(place, "nameJa", ja);
		ReflectionTestUtils.setField(place, "nameZhHans", hans);
		ReflectionTestUtils.setField(place, "nameZhHant", hant);
		return place;
	}

	private List<PlaceSummaryResponse> search(String query, Place... found) {
		when(this.placeRepository.searchByName(anyString(), any(Limit.class))).thenReturn(List.of(found));
		PlacePageResponse page = this.service.search(query, null, null, null);
		return page.items();
	}

	@Test
	@DisplayName("🔴 번체 이름이 정확히 같으면 그 장소가 맨 앞이고, 걸린 칸은 현지 이름이다")
	void exactTraditionalNameRanksFirst() {
		Place beachParking = place("해운대해수욕장 주차장", null, null, null, "海雲臺海水浴場停車場");
		Place beach = place("해운대해수욕장", "Haeundae Beach", "海雲台海水浴場", "海云台海水浴场", "海雲臺海水浴場");

		List<PlaceSummaryResponse> items = search("海雲臺海水浴場", beachParking, beach);

		assertThat(items).extracting(PlaceSummaryResponse::placeId)
				.containsExactly(beach.getPlaceId(), beachParking.getPlaceId());
		assertThat(items.get(0).matchedField()).isEqualTo(MatchedField.NAME_LOCAL);
	}

	@Test
	@DisplayName("🔴 안 걸린 칸을 「포함」으로 치지 않는다 — 일본어로만 걸렸는데 한국어가 걸렸다고 하면 안 된다")
	void unmatchedFieldIsNotCountedAsContains() {
		Place gamcheon = place("감천문화마을", "Gamcheon Culture Village", "甘川文化村", null, null);

		List<PlaceSummaryResponse> items = search("文化", gamcheon);

		assertThat(items.get(0).matchedField()).isEqualTo(MatchedField.NAME_LOCAL);
	}

	@Test
	@DisplayName("한국어·영어로 걸리면 예전 그대로 — 현지 이름이 생겨도 기존 검색의 답은 안 바뀐다")
	void koreanAndEnglishMatchesUnchanged() {
		Place gamcheon = place("감천문화마을", "Gamcheon Culture Village", "甘川文化村", "甘川文化村", "甘川文化村");

		assertThat(search("감천", gamcheon).get(0).matchedField()).isEqualTo(MatchedField.NAME_KO);
		assertThat(search("gamcheon", gamcheon).get(0).matchedField()).isEqualTo(MatchedField.NAME_EN);
	}
}
