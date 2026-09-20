package com.gabolle.backend.place.api;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.place.service.PlaceFacetViewService;

/**
 * 갈래별 이용 집계. 사용자에게 보여 줄 숫자가 아니라 어느 갈래에 데이터를 채울지 정하는 근거다.
 *
 * <p>경로가 {@code /api/v1/admin/} 아래인 것이 실제 보호 장치다. 이 저장소는 메서드 보안이 꺼져
 * 있어 애너테이션으로는 못 막고, {@code SecurityConfig} 의 경로 규칙 하나가 운영자 인가를 전부
 * 담당한다 — 그 아래에 없는 운영자 경로는 아무도 막지 않는다.
 */
@RestController
@Profile({ "db", "dev" })
public class AdminFacetViewController {

	private final PlaceFacetViewService facetViewService;

	public AdminFacetViewController(PlaceFacetViewService facetViewService) {
		this.facetViewService = facetViewService;
	}

	/**
	 * 갈래별 열람 수를 많은 순으로. 한 번도 안 열린 갈래는 0 으로 채우지 않고 목록에서 빠진다 —
	 * 갈래 목록의 정본은 갈래 조회다.
	 */
	@GetMapping(value = "/api/v1/admin/facet-views")
	public ApiResponse<List<PlaceFacetViewService.FacetViewCount>> counts() {
		return ApiResponse.success(this.facetViewService.counts(), "req_" + UUID.randomUUID());
	}
}
