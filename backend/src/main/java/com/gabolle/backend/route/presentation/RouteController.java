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
 * 두 좌표 사이의 경로 — S15P21E201-62 · -184.
 *
 * <p>지도 화면·경로 상세·일정 생성이 전부 같은 자리를 쓴다. 응답 모양은
 * {@link RouteDirectionsResponse} 에 적혀 있다.
 *
 * <h2>🔴 여행이나 일정에 매달지 않았다</h2>
 *
 * 경로 조회는 <b>좌표 두 개만 있으면 되는 일</b>이라 어느 여행의 것인지 몰라도 답할 수 있다.
 * {@code /trips/{tripId}/...} 아래 두면 아직 여행을 안 만든 사람은 경로를 못 보고, 출발지를
 * 고르는 화면처럼 여행이 생기기 <b>전에</b> 쓰는 자리에서 부를 수 없다.
 *
 * <p>그래서 인가는 "로그인한 사람이면 된다" 다. 남의 것을 볼 위험이 없다 — 좌표는 부르는
 * 쪽이 준 값이고 우리 데이터가 아니다.
 *
 * <h2>비용을 아끼는 자리는 여기가 아니다</h2>
 *
 * 같은 경로를 여러 번 물어도 바깥 업체를 여러 번 부르지는 않는다 —
 * {@code RouteCache} 가 그 자리를 맡는다(S15P21E201-196).
 */
@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

	private final RouteQueryService routeQueryService;

	public RouteController(RouteQueryService routeQueryService) {
		this.routeQueryService = routeQueryService;
	}

	/**
	 * @param mode 비우면 {@code CAR}. 앱이 이동수단을 안 정한 화면에서도 부를 수 있게 한다
	 * @throws IllegalArgumentException 좌표 범위를 벗어났거나 모르는 이동수단 — 400
	 */
	@GetMapping("/directions")
	public ApiResponse<RouteDirectionsResponse> directions(
			@RequestParam double originLat,
			@RequestParam double originLng,
			@RequestParam double destLat,
			@RequestParam double destLng,
			@RequestParam(required = false, defaultValue = "CAR") String mode,
			Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		// 🔴 사용자를 쓰지는 않지만 인증은 요구한다. 좌표만 넣으면 누구나 부를 수 있는 자리는
		//    바깥 업체 호출을 대신 시켜 주는 창구가 된다 — 우리 키로, 우리 비용으로.
		AuthenticatedUsers.requireId(authentication);

		RouteQuery query = new RouteQuery(originLat, originLng, destLat, destLng, parseMode(mode));
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
