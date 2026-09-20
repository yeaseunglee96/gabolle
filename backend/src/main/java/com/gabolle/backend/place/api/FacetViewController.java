package com.gabolle.backend.place.api;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.place.service.PlaceFacetViewService;

/**
 * 여행에 안 묶인 갈래 열람을 남기는 경로. 여행 안 경로
 * ({@code POST /api/v1/trips/{tripId}/facet-views/{facetKey}})는 여행 회원인지를 검사하므로,
 * 실을 {@code tripId} 가 없는 전역 탐색 화면은 그쪽으로 기록을 남길 수 없다.
 *
 * <p>기존 경로의 {@code tripId} 를 선택값으로 바꾸는 대신 경로를 따로 둔다 — 선택값으로 만들면
 * 여행 번호를 안 보내는 것만으로 소유권 검사를 건너뛰어 남의 여행 이름으로 기록을 남길 수 있다.
 * 이 자리는 여행 번호를 아예 받지 않는다.
 *
 * <p>표는 여행 안 경로와 함께 쓴다. 나누면 {@code PlaceFacetViewService.counts} 가 두 곳을 더해야
 * 하고, 한 곳을 빠뜨리면 숫자가 조용히 반쪽이 된다.
 *
 * <p>{@code TripFacetViewController} 가 {@code trip} 패키지에 있는 이유는 여행 회원 판정이 거기
 * 있기 때문이고, 이 경로에는 그 판정 자체가 없어 {@code place} 에 둔다.
 */
@RestController
@Profile({ "db", "dev" })
public class FacetViewController {

	private final PlaceFacetViewService facetViewService;

	public FacetViewController(PlaceFacetViewService facetViewService) {
		this.facetViewService = facetViewService;
	}

	/**
	 * 갈래를 열었다는 사실을 남긴다. 여행 번호를 받지 않는다.
	 *
	 * <p>202 로 답한다 — 기록은 부수적인 일이라 저장 성공을 약속하지 않는다. 로그인은 요구한다.
	 * 누가 남겼는지를 표에 적지는 않지만, 열어 두면 아무나 집계를 부풀릴 수 있다.
	 *
	 * <p>갈래 코드는 검사하지 않는다. 갈래가 늘거나 이름이 바뀌어도 그때의 값으로 남아야 하고,
	 * 모르는 값이 오면 집계에 그대로 보인다.
	 */
	@PostMapping("/api/v1/facet-views/{facetKey}")
	public ResponseEntity<ApiResponse<Void>> recordFacetView(
			@PathVariable String facetKey,
			Authentication authentication) {

		AuthenticatedUsers.requireId(authentication);

		this.facetViewService.recordGlobal(facetKey);

		return ResponseEntity.accepted().body(ApiResponse.success(null, "req_" + UUID.randomUUID()));
	}
}
