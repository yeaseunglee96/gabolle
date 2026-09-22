package com.gabolle.backend.place.api;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.place.service.TaxiCardService;

/**
 * 택시 목적지 카드. 한국어를 못 하는 여행자가 택시 기사에게 화면을 보여주면 목적지가 전달되는
 * 화면에 쓴다.
 *
 * <p>{@code X-User-Id} 를 받지 않는다. 좌표와 장소만으로 답이 정해지는 조회라 로그인 여부가 결과에
 * 관여하지 않는다.
 *
 * <p>없는 {@code placeId} 는 {@link com.gabolle.backend.place.service.PlaceNotFoundException} 이
 * 나가고, 같은 패키지를 보는 {@link PlaceExceptionHandler} 가 404 로 번역한다 — 이 컨트롤러가 직접
 * 처리하지 않는다.
 */
@RestController
@RequestMapping("/api/v1/places")
@Profile({ "db", "dev" })
public class TaxiCardController {

	private final TaxiCardService taxiCardService;

	public TaxiCardController(TaxiCardService taxiCardService) {
		this.taxiCardService = taxiCardService;
	}

	@GetMapping("/{placeId}/taxi-card")
	public ApiResponse<TaxiCardResponse> get(@PathVariable UUID placeId,
			@RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		TaxiCardResponse response = this.taxiCardService.get(placeId, acceptLanguage);
		return ApiResponse.success(response, resolveRequestId(requestId));
	}

	/** 클라이언트가 준 추적 아이디를 그대로 쓴다. 로그와 응답이 같은 값을 갖게 하려는 것이다. */
	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}
}
