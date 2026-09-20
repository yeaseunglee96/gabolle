package com.gabolle.backend.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.api.PlaceCategoryResponse;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.repository.PlaceRepository.CategoryCount;

/**
 * 적재된 갈래를 그대로 내는지 본다. 지키는 것은 "무엇을 안 하는가" 다 — 앱의 낱말 목록을 서버가
 * 알고 있다가 0 을 채워 내보내면 그 목록이 계약이 되고, 적재가 새 갈래를 넣어도 안 보인다.
 */
class PlaceCategoryServiceTest {

	private final PlaceRepository placeRepository = mock(PlaceRepository.class);

	private final PlaceCategoryService service = new PlaceCategoryService(this.placeRepository);

	@Test
	void returnsLoadedCategoriesWithCounts() {
		when(this.placeRepository.countByCategory())
				.thenReturn(List.of(count("FOOD", 2355), count("CULTURE_TEMPLE", 162)));

		PlaceCategoryResponse response = this.service.categories();

		assertThat(response.categories()).extracting(PlaceCategoryResponse.CategoryItem::code)
				.containsExactly("FOOD", "CULTURE_TEMPLE");
		assertThat(response.categories()).extracting(PlaceCategoryResponse.CategoryItem::placeCount)
				.containsExactly(2355L, 162L);
		assertThat(response.generatedAt()).isNotNull();
	}

	/** 화면은 이 응답에 없는 갈래를 감춰서, 고르면 반드시 실패하는 선택지를 아예 안 만든다. */
	@Test
	void returnsOnlyWhatIsLoaded() {
		when(this.placeRepository.countByCategory()).thenReturn(List.of(count("FOOD", 2355)));

		assertThat(this.service.categories().categories())
				.singleElement()
				.satisfies(item -> assertThat(item.code()).isEqualTo("FOOD"));
	}

	/** 적재가 하나도 안 됐어도 답은 빈 목록이다 — 지어낸 갈래로 채우지 않는다. */
	@Test
	void returnsEmptyWhenNothingLoaded() {
		when(this.placeRepository.countByCategory()).thenReturn(List.of());

		assertThat(this.service.categories().categories()).isEmpty();
	}

	/**
	 * 목이 아니라 실제 구현이다. 목으로 만들면 이 메서드가 {@code when(...)} 의 인자 안에서
	 * 불리면서 바깥 스터빙 중간에 안쪽 스터빙이 끼어들어 {@code UnfinishedStubbingException}
	 * 이 난다. 값 두 개짜리 줄을 흉내 내는 데 목이 필요하지도 않다.
	 */
	private CategoryCount count(String code, long placeCount) {
		return new CategoryCount() {

			@Override
			public String getCode() {
				return code;
			}

			@Override
			public long getPlaceCount() {
				return placeCount;
			}
		};
	}
}
