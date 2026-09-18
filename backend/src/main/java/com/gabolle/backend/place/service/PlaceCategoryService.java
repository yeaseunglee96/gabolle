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
 * 지금 장소가 있는 갈래를 센다 — S15P21E201-896.
 *
 * <h2>왜 필요한가</h2>
 *
 * 추천 엔진은 앱이 보낸 취향 갈래 코드를 {@code place.category} 와 <b>글자 그대로</b> 비교한다
 * ({@code BaselineCandidateTranslator}). 그래서 적재되지 않은 갈래를 고른 사용자는 후보가 0 이
 * 되고, 일정 생성이 {@code ERROR_NO_CANDIDATES} 로 끝난다. 이 실패는 재시도로 안 풀린다 —
 * 후보가 모자랄 때 조건을 몰래 완화하지 않는 것이 이 엔진의 규칙이다.
 *
 * <p>2026-09-13 운영에는 {@code FOOD} 하나뿐이라 나머지 다섯 갈래를 고르면 반드시 실패한다.
 * 화면이 이 목록을 읽어 없는 갈래를 안 보여 주면 그 경로 자체가 사라진다.
 *
 * <h2>왜 서비스가 목록을 안 정하나</h2>
 *
 * 세는 것만 한다. 앱의 여섯 낱말을 여기 적어 두고 0 을 채워 내보내면, 갈래가 늘 때마다 서버도
 * 같이 고쳐야 하고 그 목록이 곧 계약이 된다. 있는 값을 그대로 내면 적재가 도는 순간 새 갈래가
 * 저절로 나타나고, 화면은 자기가 아는 낱말과 겹치는 것만 쓰면 된다.
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
