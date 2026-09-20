package com.gabolle.backend.trip.presentation;

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
import com.gabolle.backend.trip.application.TripQueryService;

/**
 * 갈래를 열었다는 사실을 남기는 경로.
 *
 * <p>{@link TripController} 에 붙이지 않는다. 그쪽은 프로필이 없어 DB 없는 컨텍스트에도 뜨는데
 * 기록 저장은 {@code db}·{@code dev} 프로필의 빈을 필요로 해서, 합치면 {@code no-db}
 * 컨텍스트가 통째로 못 뜬다.
 *
 * <p>기록과 집계는 {@code place} 에 있지만 누가 남길 수 있는지는 여행의 문제다. 그 판정
 * ({@link TripQueryService#get})과 오류 번역이 이 패키지에 있어서 여기에 둔다 — place 로
 * 옮기면 남의 여행을 막는 규칙이 두 곳에 살게 된다.
 */
@RestController
@Profile({ "db", "dev" })
public class TripFacetViewController {

	private final TripQueryService queryService;

	private final PlaceFacetViewService facetViewService;

	public TripFacetViewController(TripQueryService queryService, PlaceFacetViewService facetViewService) {
		this.queryService = queryService;
		this.facetViewService = facetViewService;
	}

	/**
	 * 갈래를 열었다는 사실을 남긴다. 202 는 보고를 받았다는 뜻일 뿐 저장 성공을 약속하지
	 * 않는다 — 200 으로 답하면 화면이 "남았다" 로 읽고, 실패했을 때 그 응답이 거짓이 된다.
	 *
	 * <p>{@code queryService.get} 이 회원 아닌 요청을 없는 여행과 같은 404 로 막는다. 그
	 * 검사가 없으면 아무나 남의 여행 번호로 집계를 부풀릴 수 있다.
	 *
	 * <p>갈래 코드는 검사하지 않는다. 갈래가 늘거나 이름이 바뀌어도 그때의 값으로 남아야 하고,
	 * 모르는 값이 오면 집계에 그대로 보이는 것이 낫다.
	 */
	@PostMapping("/api/v1/trips/{tripId}/facet-views/{facetKey}")
	public ResponseEntity<ApiResponse<Void>> recordFacetView(
			@PathVariable String tripId,
			@PathVariable String facetKey,
			Authentication authentication) {

		String requester = AuthenticatedUsers.requireId(authentication).toString();
		this.queryService.get(tripId, requester);

		this.facetViewService.record(facetKey, UUID.fromString(tripId));

		return ResponseEntity.accepted().body(ApiResponse.success(null, "req_" + UUID.randomUUID()));
	}
}
