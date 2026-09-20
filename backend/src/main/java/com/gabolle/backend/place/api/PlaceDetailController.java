package com.gabolle.backend.place.api;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.place.service.PlaceDetailService;

/**
 * 장소 상세 조회.
 *
 * <p>사용자를 인증 principal 에서 얻는다. {@code X-User-Id} 요청 헤더를 쓰면 인증만 통과한 사람이
 * 남의 ID 를 주장할 수 있다.
 *
 * <p>목록 조회({@code GET /api/v1/places})는 다른 컨트롤러에 있다. Spring 이 리터럴
 * ({@code /api/v1/places/facets})을 템플릿({@code /api/v1/places/{placeId}})보다 먼저 맞추므로 두
 * 컨트롤러가 공존해도 충돌하지 않는다.
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
	 * @param acceptLanguage {@code en} 으로 시작하면 영문 이름·주소를 우선한다. 영문 값이 없으면
	 *        한국어로 되돌리고, 응답의 {@code resolvedLanguage} 가 어느 언어로 답했는지 알린다
	 * @param itineraryId 어느 일정에 대해 포함 여부를 묻는가. 선택이다 — 안 주면 응답의
	 *        {@code itineraryInclusion} 이 {@code UNAVAILABLE} 로 나가고 그것이 정상이다.
	 *        여행이 아니라 일정을 받는다 — 여행에서 일정으로 가는 단계를 서버가 대신 밟으면 "그
	 *        여행에 일정이 여럿이면 어느 것인가" 를 이 엔드포인트가 몰래 정하게 된다
	 *        ({@code ItineraryMembershipPort} 클래스 주석).
	 *        <p>형식이 UUID 가 아니면 {@code PlaceExceptionHandler} 가 400 으로 답한다. 형식이 맞는데
	 *        볼 수 없는 일정은 200 에 {@code UNAVAILABLE} 이다
	 */
	@GetMapping("/{placeId}")
	public ApiResponse<PlaceDetailResponse> get(@PathVariable UUID placeId,
			Authentication authentication,
			@RequestParam(value = "itineraryId", required = false) UUID itineraryId,
			@RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		UUID viewerId = AuthenticatedUsers.optionalId(authentication).orElse(null);
		return ApiResponse.success(this.placeDetailService.get(placeId, viewerId, acceptLanguage, itineraryId),
				resolveRequestId(requestId));
	}

	/** 클라이언트가 준 추적 아이디를 그대로 쓴다. 로그와 응답이 같은 값을 갖게 하려는 것이다. */
	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}
}
