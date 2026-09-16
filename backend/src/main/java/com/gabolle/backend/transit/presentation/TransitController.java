package com.gabolle.backend.transit.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.transit.application.TransitService;
import com.gabolle.backend.transit.domain.NearbyBusArrivalsQuery;
import com.gabolle.backend.transit.domain.NearbyBusArrivalsResult;
import com.gabolle.backend.transit.presentation.dto.NearbyBusArrivalsResponseDto;

/**
 * 좌표 근처의 버스 정류소·실시간 도착정보를 답한다 — S15P21E201-988.
 *
 * <h2>🔴 인가는 "로그인한 사람이면 된다" 다</h2>
 * {@code WeatherController}와 같은 이유 — 좌표는 부르는 쪽이 준 값이고 우리 자원이 아니다.
 * 로그인을 요구하는 것은 우리 TAGO 키로 남이 대신 호출을 돌리는 것(호출 한도 소진)을 막기
 * 위해서다.
 *
 * <p>{@code @Profile({"db","dev"})}는 {@code weather} 패키지와 같은 이유다.
 */
@RestController
@RequestMapping("/api/v1/transit")
@Profile({ "db", "dev" })
public class TransitController {

	private final TransitService transitService;

	public TransitController(TransitService transitService) {
		this.transitService = transitService;
	}

	/** @throws com.gabolle.backend.transit.application.TransitVendorException TAGO 호출 실패 — 502 */
	@GetMapping("/nearby-bus-arrivals")
	public ApiResponse<NearbyBusArrivalsResponseDto> nearbyBusArrivals(
			@RequestParam double lat,
			@RequestParam double lng,
			Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		AuthenticatedUsers.requireId(authentication);

		NearbyBusArrivalsResult result = this.transitService.nearbyArrivals(new NearbyBusArrivalsQuery(lat, lng));

		return ApiResponse.success(NearbyBusArrivalsResponseDto.from(result), resolveRequestId(requestId));
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? "req_" + UUID.randomUUID() : requestId;
	}
}
