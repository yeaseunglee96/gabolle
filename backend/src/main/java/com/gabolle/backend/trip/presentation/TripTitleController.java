package com.gabolle.backend.trip.presentation;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.application.TripTitleService;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.presentation.dto.TripTitleResponse;
import com.gabolle.backend.trip.presentation.dto.UpdateTripTitleRequest;

/**
 * 여행에 이름을 붙인다 — S15P21E201-1023.
 *
 * <pre>
 * PUT /api/v1/trips/{tripId}/title      {"title": "9월 부산 혼자"}
 * </pre>
 *
 * <h2>🔴 왜 이름이 필요한가</h2>
 *
 * 여행 카드의 제목 자리가 <b>날짜뿐이었다.</b> 그래서 같은 날짜로 두 번 계획하면 두 카드가
 * 글자 하나 다르지 않았고, 「다시 짜 보기」가 기본 동작인 서비스에서 그건 드문 일이 아니다.
 * 테스터가 자기가 만든 것을 못 찾으면, 받는 피드백이 기능이 아니라 <b>길 잃음</b>에 대한
 * 것이 된다.
 *
 * <h2>🔴 {@code TripController} 를 넓히지 않았다</h2>
 *
 * 여행에 딸린 것은 각자 자기 컨트롤러를 갖는 것이 이 저장소의 방식이다
 * ({@code TripCollaborationController}·{@code TripStoryController}). 실패를 HTTP 로 옮기는
 * {@code @RestControllerAdvice} 가 컨트롤러 종류에 묶여 있어서, 한 클래스에 모으면
 * 서로 다른 실패가 한 번역표를 공유하게 된다.
 *
 * <h2>왜 PUT 인가</h2>
 *
 * 같은 이름을 두 번 보내도 결과가 같다. 그리고 이 요청은 <b>칸 하나를 통째로 정하는 것</b>
 * 이지 일부를 고치는 것이 아니다 — 빈 값을 보내면 이름이 지워지는 것도 그래서 자연스럽다.
 */
@RestController
@RequestMapping("/api/v1/trips/{tripId}")
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class TripTitleController {

	private final TripTitleService service;

	public TripTitleController(TripTitleService service) {
		this.service = service;
	}

	@PutMapping("/title")
	public ApiResponse<TripTitleResponse> updateTitle(
			@PathVariable String tripId,
			@RequestBody UpdateTripTitleRequest request,
			Authentication authentication) {

		String requester = AuthenticatedUsers.requireId(authentication).toString();
		Trip trip = this.service.rename(tripId, requester, request == null ? null : request.title());
		return ApiResponse.success(TripTitleResponse.of(trip), "req_" + UUID.randomUUID());
	}
}
