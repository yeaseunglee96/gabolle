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
 * 갈래를 열었다는 사실을 남기는 경로 — S15P21E201-475.
 *
 * <h2>왜 {@link TripController} 에 안 붙였나</h2>
 * 처음엔 거기 붙였다가 되돌렸다. 그 컨트롤러에는 <b>프로필이 없어서</b> DB 없는 컨텍스트에도
 * 뜨는데, 기록 저장은 {@code db}·{@code dev} 프로필에만 있는 빈을 필요로 한다. 그대로 두면
 * {@code no-db} 컨텍스트가 통째로 못 뜬다 — 실제로 전체 빌드에서 서른한 개가 빨개졌다.
 *
 * <p>그래서 프로필이 붙은 별도 컨트롤러로 뺐다. {@code TripCollaborationController} 가 같은
 * 이유로 이미 갈라져 있다.
 *
 * <h2>기록은 place, 권한은 trip</h2>
 * 갈래는 장소의 개념이라 기록과 집계는 {@code place} 쪽에 있다. 그런데 <b>누가 남길 수
 * 있는지</b>는 여행의 문제다 — 그 판정({@link TripQueryService#get})과 오류 번역이 이미 이
 * 패키지에 있고, 그것을 place 로 복사하면 남의 여행을 막는 규칙이 두 곳에 살게 된다.
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
	 * 갈래를 열었다는 사실을 남긴다.
	 *
	 * <h2>202 로 답하는 이유</h2>
	 * 기록은 <b>부수적인 일</b>이다. 완료 기준이 "기록 저장을 실패시켜도 갈래 목록은 그대로
	 * 열린다" 이므로 이 경로는 저장 성공을 약속하지 않는다 — 보고를 받았다는 것까지만 답한다.
	 * 200 으로 답하면 화면이 "남았다" 로 읽고, 실패했을 때 그 응답이 거짓이 된다.
	 *
	 * <p>남의 여행 번호로는 못 남긴다. {@code queryService.get} 이 회원이 아닌 요청을 없는
	 * 여행과 같은 404 로 막는다 — 그 검사가 없으면 아무나 남의 여행 이름으로 집계를 부풀릴 수
	 * 있고, 그러면 이 숫자로 우선순위를 정할 수 없게 된다.
	 *
	 * <p>갈래 코드는 검사하지 않는다. 갈래가 늘거나 이름이 바뀌어도 그때의 값으로 남아야 하고,
	 * 모르는 값이 오면 그것 자체가 알아야 할 사실이다(집계에 그대로 보인다).
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
