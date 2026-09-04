package com.gabolle.backend.place.api;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.place.service.NearbyPlaceService;

/**
 * 근처 장소 거리순 조회 (S15P21E201-469).
 *
 * <p>🔴 {@code X-User-Id} 를 받지 않는다. 좌표와 목적만으로 답이 정해지는 조회라 로그인 여부가
 * 결과에 관여하지 않는다 — 사용자 식별이 필요한 다른 API 와 이 점이 다르다.
 *
 * <p>🔴 {@code purpose} 는 선택 파라미터다. {@code gabolle.place.purposes} 설정이 아직 비어 있어서
 * ({@code PlaceProperties} 참고) {@code purpose} 를 필수로 두면 어떤 값을 보내도 항상
 * {@code UNKNOWN_PURPOSE} 로 거부되어 이 엔드포인트가 영원히 200 을 내지 못했다. 안 보내면 목적
 * 필터 없이 반경 안 장소를 거리순으로 돌려준다 — {@code NearbyPlaceService} 참고.
 */
@RestController
@RequestMapping("/api/v1/places")
@Profile({ "db", "dev" })
public class NearbyPlaceController {

	private final NearbyPlaceService nearbyPlaceService;

	public NearbyPlaceController(NearbyPlaceService nearbyPlaceService) {
		this.nearbyPlaceService = nearbyPlaceService;
	}

	@GetMapping("/nearby")
	public ApiResponse<NearbyPlaceResponse> nearby(
			@RequestParam double lat,
			@RequestParam double lng,
			@RequestParam(required = false) String purpose,
			@RequestParam(defaultValue = "20") int limit,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		NearbyPlaceResponse response = this.nearbyPlaceService.findNearby(lat, lng, purpose, limit);
		return ApiResponse.success(response, resolveRequestId(requestId));
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}
}
