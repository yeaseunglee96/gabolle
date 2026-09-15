package com.gabolle.backend.place.api;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.place.service.FestivalQueryService;

/**
 * 여행 기간과 겹치는 축제 조회 (S15P21E201-465).
 *
 * <p>경로는 {@code /api/v1/festivals} 다 — 티켓 완료 기준과 프런트가 독립적으로 같은 경로를 쓰고
 * 있어 그쪽을 따랐다. 자바 패키지는 {@code place} 그대로 둔다 — 축제는 {@code place} 와
 * {@code place_event_period} 로 만들어지는 조회라 그 패키지가 맞고, 바뀐 것은 URL 뿐이다.
 *
 * <p>🔴 파라미터 이름은 티켓 원문의 {@code from}·{@code to} 를 쓰지 않고 {@code startDate}·
 * {@code endDate} 를 유지한다. 어느 쪽 날짜인지 이름만으로 드러나고, 프런트에 이미 이 이름으로
 * 계약을 보냈다 — 별칭을 함께 받으면 나중에 한쪽만 고쳐지는 날이 온다.
 *
 * <p>공개 카탈로그 조회라 {@code X-User-Id} 나 인증을 요구하지 않는다 — {@code PlaceQueryController}
 * 의 갈래 조회(-473)와 같은 이유다.
 */
@RestController
@RequestMapping("/api/v1/festivals")
@Profile({ "db", "dev" })
public class FestivalController {

	private final FestivalQueryService festivalQueryService;

	public FestivalController(FestivalQueryService festivalQueryService) {
		this.festivalQueryService = festivalQueryService;
	}

	/**
	 * {@code startDate}·{@code endDate} 는 둘 다 필수다. 빠지면 Spring 이 던지는
	 * {@code MissingServletRequestParameterException} 을 {@code PlaceExceptionHandler} 가 이미
	 * {@code 400 INVALID_REQUEST} 로 번역해 준다 — 여기서 null 검사를 다시 하지 않는다.
	 */
	@GetMapping
	public ApiResponse<FestivalResponse> festivals(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		// 🔴 기본값을 여기서 정하지 않고 null 을 그대로 넘긴다. 컨트롤러와 서비스 양쪽에
		//    기본값을 적으면 둘이 어긋나는 날이 오고, 그러면 쪽을 넘길 때 행이 겹치거나
		//    건너뛰어진다 — 기본 쪽 크기를 아는 곳은 서비스 하나뿐이어야 한다.
		FestivalResponse response = this.festivalQueryService.findOverlapping(startDate, endDate, page, size);
		return ApiResponse.success(response, resolveRequestId(requestId));
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}
}
