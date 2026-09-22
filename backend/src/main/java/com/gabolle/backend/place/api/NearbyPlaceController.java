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
 * 근처 장소 거리순 조회.
 *
 * <p>{@code X-User-Id} 를 받지 않는다. 좌표와 목적만으로 답이 정해지는 조회라 로그인 여부가 결과에
 * 관여하지 않는다.
 *
 * <p>{@code purpose} 는 선택 파라미터다. {@code gabolle.place.purposes} 설정이 비어 있어
 * ({@code PlaceProperties}) 필수로 두면 어떤 값을 보내도 {@code UNKNOWN_PURPOSE} 로 거부된다.
 * 안 보내면 목적 필터 없이 반경 안 장소를 거리순으로 돌려준다 — {@code NearbyPlaceService} 참고.
 */
@RestController
@RequestMapping("/api/v1/places")
@Profile({ "db", "dev" })
public class NearbyPlaceController {

	private final NearbyPlaceService nearbyPlaceService;

	public NearbyPlaceController(NearbyPlaceService nearbyPlaceService) {
		this.nearbyPlaceService = nearbyPlaceService;
	}

	/**
	 * @param facetKey 여덟 갈래 코드 하나 (예: {@code SOUVENIR_SHOP}). {@code purpose} 와 함께
	 *        보내면 400 이다 — 어느 쪽이 이겼는지 요청자가 모르게 되기 때문이다
	 * @param radiusMeters 시작 반경. 주면 그 반경과 두 배까지만 찾는다. 안 주면 설정 사다리를 쓴다
	 *        ({@code NearbyPlaceService} 참고)
	 */
	@GetMapping("/nearby")
	public ApiResponse<NearbyPlaceResponse> nearby(
			@RequestParam double lat,
			@RequestParam double lng,
			@RequestParam(required = false) String purpose,
			@RequestParam(required = false) String facetKey,
			@RequestParam(required = false) Integer radiusMeters,
			@RequestParam(defaultValue = "20") int limit,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		NearbyPlaceResponse response = this.nearbyPlaceService.findNearby(lat, lng, purpose, facetKey,
				radiusMeters, limit);
		return ApiResponse.success(response, resolveRequestId(requestId));
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}
}
