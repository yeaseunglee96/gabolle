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
 * 여행에 이름을 붙인다.
 *
 * <p>{@code TripController} 와 갈라 둔다. 실패를 HTTP 로 옮기는 {@code @RestControllerAdvice}
 * 가 컨트롤러 종류에 묶여 있어서, 한 클래스에 모으면 서로 다른 실패가 한 번역표를 공유한다.
 *
 * <p>PUT 인 것은 칸 하나를 통째로 정하는 요청이기 때문이다 — 빈 값을 보내면 이름이 지워진다.
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

		String requester = AuthenticatedUsers.requireTripActor(authentication);
		Trip trip = this.service.rename(tripId, requester, request == null ? null : request.title());
		return ApiResponse.success(TripTitleResponse.of(trip), "req_" + UUID.randomUUID());
	}
}
