package com.gabolle.backend.place.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.api.PlaceCategoryResponse;
import com.gabolle.backend.place.api.PlaceCategoryResponse.CategoryItem;
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * 지금 장소가 적재된 갈래만 센다.
 *
 * <p>추천 엔진은 취향 갈래 코드를 {@code place.category} 와 글자 그대로 비교하므로, 적재되지
 * 않은 갈래를 고르면 후보가 0 이 되고 일정 생성이 {@code ERROR_NO_CANDIDATES} 로 끝난다.
 * 화면은 이 목록에 있는 갈래만 보여 준다.
 *
 * <p>없는 갈래를 0 으로 채워 내보내지 않는다. 그러면 갈래가 늘 때마다 이 목록을 같이 고쳐야
 * 하고, 그 목록이 곧 계약이 된다.
 */
@Service
@Profile({"db", "dev"})
public class PlaceCategoryService {

	private final PlaceRepository placeRepository;

	public PlaceCategoryService(PlaceRepository placeRepository) {
		this.placeRepository = placeRepository;
	}

	@Transactional(readOnly = true)
	public PlaceCategoryResponse categories() {
		List<CategoryItem> items = this.placeRepository.countByCategory().stream()
				.map(row -> new CategoryItem(row.getCode(), row.getPlaceCount()))
				.toList();

		return new PlaceCategoryResponse(items, OffsetDateTime.now(ZoneOffset.UTC));
	}
}
