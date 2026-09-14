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
 * 여행에 안 묶인 갈래 열람을 남기는 경로 — S15P21E201-894.
 *
 * <h2>왜 경로가 하나 더 필요한가</h2>
 * S15P21E201-475 가 연 자리는 {@code POST /api/v1/trips/{tripId}/facet-views/{facetKey}} 뿐이고
 * 여행 회원인지를 검사한다. 그런데 로컬 탐색 화면(S15P21E201-127)은 여행에 속하지 않는 전역
 * 탭이라 실을 {@code tripId} 가 없어서 <b>한 번도 기록을 못 남기고 있었다.</b> 475 의 목적이
 * 이용 집계인데 화면 하나가 통째로 빠지면 그 숫자로 우선순위를 정할 수 없다.
 *
 * <h2>🔴 기존 경로를 느슨하게 하지 않는다</h2>
 * {@code tripId} 를 선택값으로 바꾸는 방법도 있었다. 그러면 경로 하나로 끝나지만, 여행 번호를
 * 안 보내면 소유권 검사를 건너뛰게 되므로 <b>남의 여행 이름으로 기록을 남기는 길</b>이 그 옆에
 * 열린다. 그래서 검사를 지나는 경로와 안 지나는 경로를 아예 따로 둔다 — 이 자리는 여행 번호를
 * 받지 않으므로 남의 여행에 무엇도 남길 수 없다.
 *
 * <p>표는 나누지 않는다. 나누면 집계가 두 곳을 더해야 하고, 더하는 것을 잊은 자리가 하나라도
 * 생기면 그때부터 숫자가 조용히 반쪽이 된다({@code PlaceFacetViewService.counts}).
 *
 * <p>{@code place} 패키지에 두는 것은 {@code TripFacetViewController} 와 반대 방향이 아니다.
 * 그쪽이 {@code trip} 에 있는 이유는 <b>여행 회원 판정</b>이 거기 있기 때문이고, 이 경로에는 그
 * 판정 자체가 없다.
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
	 * <p>202 로 답하는 이유는 여행 안 경로와 같다 — 기록은 부수적인 일이라 저장 성공을 약속하지
	 * 않는다. 200 으로 답하면 화면이 "남았다" 로 읽고, 실패했을 때 그 응답이 거짓이 된다.
	 *
	 * <p>로그인은 요구한다. 누가 남겼는지를 표에 적지는 않지만, 열어 두면 아무나 집계를 부풀릴 수
	 * 있고 그 숫자로는 아무 판단도 못 한다.
	 *
	 * <p>갈래 코드는 검사하지 않는다. 갈래가 늘거나 이름이 바뀌어도 그때의 값으로 남아야 하고,
	 * 모르는 값이 오면 그것 자체가 알아야 할 사실이다(집계에 그대로 보인다).
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
