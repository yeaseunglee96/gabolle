package com.gabolle.backend.facility.api;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.facility.service.FacilityNearbyService;

/**
 * 근처 편의시설 — 주차장·약국·공중화장실·지하철 물품보관함 (S15P21E201-2052).
 *
 * <p>로그인만 요구한다(익명 세션도 된다 — 장소 조회와 같은 문, {@code SecurityConfig} 를 안 바꾼다). 좌표와 종류만으로 답이 정해지고
 * 사용자별로 다른 것이 없어서 요청자를 받지 않는다 — {@code Authentication} 인자도 {@code X-User-Id} 헤더도 없다.
 */
@RestController
@RequestMapping("/api/v1/facilities")
@Profile({ "db", "dev" })
public class FacilityController {

	private final FacilityNearbyService service;

	public FacilityController(FacilityNearbyService service) {
		this.service = service;
	}

	/**
	 * 기준점 근처의 한 종류.
	 *
	 * @param kind {@code PARKING} · {@code PHARMACY} · {@code TOILET} · {@code LOCKER}
	 * @param lat 기준 위도 (내 위치나 고른 목적지)
	 * @param lng 기준 경도
	 * @param radiusM 반경(m) — 1~2000, 기본 700
	 * @param limit 개수 상한 — 1~50, 기본 20
	 * @param stayMinutes 주차장 예상요금에 쓸 체류 시간(분) — 1~1440, 기본 60. 주차장만 쓴다
	 * @param accessible 화장실만 — {@code true} 면 장애인용만
	 * @param diaper 화장실만 — {@code true} 면 기저귀 교환대가 있는 곳만
	 */
	@GetMapping("/nearby")
	public ApiResponse<FacilityNearbyResponse> nearby(
			@RequestParam String kind,
			@RequestParam double lat,
			@RequestParam double lng,
			@RequestParam(required = false) Integer radiusM,
			@RequestParam(required = false) Integer limit,
			@RequestParam(required = false) Integer stayMinutes,
			@RequestParam(required = false) Boolean accessible,
			@RequestParam(required = false) Boolean diaper,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		FacilityNearbyResponse response = this.service.nearby(
				new FacilityNearbyService.Query(kind, lat, lng, radiusM, limit, stayMinutes, accessible, diaper));
		return ApiResponse.success(response, requestId == null || requestId.isBlank() ? "req_" + UUID.randomUUID() : requestId);
	}
}
