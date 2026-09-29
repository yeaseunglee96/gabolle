package com.gabolle.backend.route.presentation;

import java.util.Locale;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.route.application.RouteQueryService;
import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.domain.RouteQuery;
import com.gabolle.backend.route.domain.TravelMode;
import com.gabolle.backend.route.presentation.dto.RouteDirectionsResponse;

/**
 * 두 좌표 사이의 경로. 지도 화면·경로 상세·일정 생성이 전부 이 자리를 쓴다.
 *
 * 여행이나 일정 아래에 두지 않았다 — 출발지를 고르는 화면처럼 여행이 생기기 전에 쓰는
 * 자리에서도 불러야 하기 때문이다. 그래서 인가는 "로그인한 사람이면 된다" 이고, 좌표는
 * 부르는 쪽이 준 값이라 남의 것을 볼 위험이 없다.
 */
@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

	private final RouteQueryService routeQueryService;

	public RouteController(RouteQueryService routeQueryService) {
		this.routeQueryService = routeQueryService;
	}

	/** 좌표 범위를 벗어났거나 모르는 이동수단이면 IllegalArgumentException 이고 400 이 된다. */
	@GetMapping("/directions")
	public ApiResponse<RouteDirectionsResponse> directions(
			@RequestParam double originLat,
			@RequestParam double originLng,
			@RequestParam double destLat,
			@RequestParam double destLng,
			@RequestParam(required = false, defaultValue = "CAR") String mode,
			// 계단·급경사를 피하는 길. 안 보내면 전처럼 가장 짧은 길이다 — 예전 화면이 그대로 돈다.
			@RequestParam(required = false, defaultValue = "false") boolean stepFree,
			Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		// 사용자를 쓰지는 않지만 인증은 요구한다. 좌표만 넣으면 누구나 부를 수 있는 자리는
		// 바깥 업체 호출을 대신 시켜 주는 창구가 된다 — 우리 키로, 우리 비용으로.
		AuthenticatedUsers.requireId(authentication);

		RouteQuery query = new RouteQuery(originLat, originLng, destLat, destLng, parseMode(mode), null, stepFree);
		RouteLeg leg = this.routeQueryService.find(query);

		return ApiResponse.success(RouteDirectionsResponse.from(leg), resolveRequestId(requestId));
	}

	private TravelMode parseMode(String raw) {
		try {
			return TravelMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException | NullPointerException exception) {
			throw new IllegalArgumentException("mode 는 CAR · TRANSIT · WALK 중 하나여야 합니다: " + raw);
		}
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? "req_" + UUID.randomUUID() : requestId;
	}
}
