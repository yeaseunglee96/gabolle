package com.gabolle.backend.place.api;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.place.service.PlaceFacetViewService;

/**
 * 갈래별 이용 집계 — S15P21E201-475.
 *
 * <h2>운영자 경로에 두는 이유</h2>
 * 이 숫자는 사용자에게 보여 줄 것이 아니라 <b>우리가 어느 갈래에 데이터를 채울지</b> 정하는
 * 근거다. 그리고 "어느 갈래가 인기 없다" 는 사실은 서비스의 약한 자리를 그대로 말해 준다.
 *
 * <p>경로가 {@code /api/v1/admin/} 아래인 것이 실제 보호 장치다. 이 저장소는 메서드 보안이
 * 꺼져 있어서 애너테이션으로는 못 막고, {@code SecurityConfig} 의 경로 규칙 하나가 운영자
 * 인가를 전부 담당한다 — 그 아래에 없는 운영자 경로는 아무도 막지 않는다.
 */
@RestController
@Profile({ "db", "dev" })
public class AdminFacetViewController {

	private final PlaceFacetViewService facetViewService;

	public AdminFacetViewController(PlaceFacetViewService facetViewService) {
		this.facetViewService = facetViewService;
	}

	/**
	 * 갈래별 열람 수를 많은 순으로.
	 *
	 * <p>한 번도 안 열린 갈래는 목록에 없다. 0 을 지어내 채우지 않는다 — "아직 아무도 안
	 * 열었다" 와 "그 갈래가 없어졌다" 를 여기서 구분할 방법이 없고, 갈래 목록은 갈래 조회가
	 * 정본이다.
	 */
	@GetMapping(value = "/api/v1/admin/facet-views")
	public ApiResponse<List<PlaceFacetViewService.FacetViewCount>> counts() {
		return ApiResponse.success(this.facetViewService.counts(), "req_" + UUID.randomUUID());
	}
}
