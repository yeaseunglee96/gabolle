package com.gabolle.backend.place.api;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.place.service.PlaceDetailService;

/**
 * 장소 상세 조회 (S15P21E201-476).
 *
 * <p>🔴 사용자를 <b>인증 principal</b> 에서 얻는다. 이 저장소의 {@code TripController} 와
 * {@code ItineraryEditController} 는 {@code X-User-Id} 요청 헤더를 쓰는데, 그러면 인증만 통과한
 * 사람이 남의 ID 를 주장할 수 있다. 새 컨트롤러는 그것을 따라 하지 않는다.
 *
 * <p>목록 조회({@code GET /api/v1/places})는 다른 컨트롤러에 있다. 경로가
 * {@code /api/v1/places/facets} 같은 리터럴과 {@code /api/v1/places/{placeId}} 템플릿으로 갈리는데,
 * Spring 은 리터럴을 템플릿보다 먼저 맞추므로 두 컨트롤러가 공존해도 충돌하지 않는다.
 */
@RestController
@RequestMapping("/api/v1/places")
@Profile({"db", "dev"})
public class PlaceDetailController {

	private final PlaceDetailService placeDetailService;

	public PlaceDetailController(PlaceDetailService placeDetailService) {
		this.placeDetailService = placeDetailService;
	}

	/**
	 * @param acceptLanguage {@code en} 으로 시작하면 영문 이름·주소를 우선한다 (S15P21E201-430,
	 *        부분). 영문 값이 없으면 한국어로 되돌리고, 응답의 {@code resolvedLanguage} 가 어느
	 *        언어로 답했는지 알린다 — 그러지 않으면 화면이 받은 값이 번역된 것인지 알 수 없다
	 */
	@GetMapping("/{placeId}")
	public ApiResponse<PlaceDetailResponse> get(@PathVariable UUID placeId,
			Authentication authentication,
			@RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		UUID viewerId = AuthenticatedUsers.optionalId(authentication).orElse(null);
		return ApiResponse.success(this.placeDetailService.get(placeId, viewerId, acceptLanguage),
				resolveRequestId(requestId));
	}

	/** 클라이언트가 준 추적 아이디를 그대로 쓴다. 로그와 응답이 같은 값을 갖게 하려는 것이다. */
	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}
}
