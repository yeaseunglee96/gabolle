package com.gabolle.backend.itinerary.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.itinerary.application.ItineraryRunService;

/**
 * 일정 진행 — S15P21E201-1325 (시안 ⑤).
 *
 * <pre>
 * GET  /api/v1/itineraries/{id}/progress                     지금 어디인가
 * POST /api/v1/itineraries/{id}/progress/start               출발
 * POST /api/v1/itineraries/{id}/progress/pause               중지
 * POST /api/v1/itineraries/{id}/progress/stops/{key}/arrive  도착 (auto|manual)
 * POST /api/v1/itineraries/{id}/progress/stops/{key}/skip    건너뛰기
 * </pre>
 *
 * <h2>🔴 여행이 아니라 일정에 붙는다</h2>
 * 인계 문서는 {@code tripId} 당이라고 적었지만, 「몇 번째를 향하고 있나」는 <b>한 일정의
 * 정차지 순서 안에서만</b> 뜻이 있다. 일정은 고칠 때마다 새 판이 생기고 여행 하나에 일정이
 * 여럿일 수 있어서, 여행에 붙이면 그 번호가 어느 일정의 몇 번째인지 알 수 없게 된다.
 *
 * <h2>🔴 {@code POST} 인 이유</h2>
 * 출발·중지·도착·건너뛰기는 <b>일어난 일</b>이지 값이 아니다. {@code PUT} 으로 상태를
 * 덮어쓰게 하면 화면이 「지금 RUNNING 이다」를 통째로 보내게 되고, 그러면 두 기기가
 * 서로의 상태를 덮어쓴다. 사건을 보내면 서버가 순서를 정한다.
 */
@RestController
@RequestMapping("/api/v1/itineraries")
@Profile({ "db", "dev" })
@ConditionalOnBean(ItineraryRunService.class)
public class ItineraryRunController {

	private final ItineraryRunService service;

	public ItineraryRunController(ItineraryRunService service) {
		this.service = service;
	}

	/** 도착이 자동인지 손인지. 안 보내면 자동으로 본다 — 기본 경로가 GPS 라서다. */
	public record ArriveRequest(String how) {
	}

	public record ProgressResponse(String status, int currentStopIndex, String startedAt, List<StopResponse> stops) {

		public static ProgressResponse of(ItineraryRunService.View view) {
			return new ProgressResponse(
					view.run().status().name(),
					view.run().currentStopIndex(),
					view.run().startedAt() == null ? null : view.run().startedAt().toString(),
					view.stops().stream().map(StopResponse::of).toList());
		}
	}

	/**
	 * 정차지 하나의 지금.
	 *
	 * <p>🔴 {@code arrivedAt} 과 {@code skipped} 를 <b>한 칸으로 합치지 않는다.</b> 건너뛴 곳은
	 * 안 간 곳이다 — 화면이 둘을 같은 모습으로 그리면 나중에 「거기 갔었나?」를 기억으로만
	 * 풀어야 한다.
	 */
	public record StopResponse(String itemKey, int index, String arrivedAt, String arrivedHow, boolean skipped) {

		public static StopResponse of(ItineraryRunService.Stop stop) {
			return new StopResponse(stop.itemKey(), stop.index(),
					stop.arrivedAt() == null ? null : stop.arrivedAt().toString(), stop.arrivedHow(), stop.skipped());
		}
	}

	@GetMapping("/{itineraryId}/progress")
	public ApiResponse<ProgressResponse> get(@PathVariable String itineraryId, Authentication authentication) {
		String userId = AuthenticatedUsers.requireId(authentication).toString();
		return ok(this.service.get(itineraryId, userId));
	}

	@PostMapping("/{itineraryId}/progress/start")
	public ApiResponse<ProgressResponse> start(@PathVariable String itineraryId, Authentication authentication) {
		String userId = AuthenticatedUsers.requireId(authentication).toString();
		return ok(this.service.start(itineraryId, userId));
	}

	@PostMapping("/{itineraryId}/progress/pause")
	public ApiResponse<ProgressResponse> pause(@PathVariable String itineraryId, Authentication authentication) {
		String userId = AuthenticatedUsers.requireId(authentication).toString();
		return ok(this.service.pause(itineraryId, userId));
	}

	@PostMapping("/{itineraryId}/progress/stops/{itemKey}/arrive")
	public ApiResponse<ProgressResponse> arrive(@PathVariable String itineraryId, @PathVariable String itemKey,
			@RequestBody(required = false) ArriveRequest request, Authentication authentication) {
		String userId = AuthenticatedUsers.requireId(authentication).toString();
		return ok(this.service.arrive(itineraryId, userId, itemKey, request == null ? null : request.how()));
	}

	@PostMapping("/{itineraryId}/progress/stops/{itemKey}/skip")
	public ApiResponse<ProgressResponse> skip(@PathVariable String itineraryId, @PathVariable String itemKey,
			Authentication authentication) {
		String userId = AuthenticatedUsers.requireId(authentication).toString();
		return ok(this.service.skip(itineraryId, userId, itemKey));
	}

	private static ApiResponse<ProgressResponse> ok(ItineraryRunService.View view) {
		return ApiResponse.success(ProgressResponse.of(view), "req_" + UUID.randomUUID());
	}

	@ExceptionHandler(ItineraryRunService.UnknownStopException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<Void> handleUnknownStop(ItineraryRunService.UnknownStopException e) {
		return ApiResponse.failure(new ApiError("STOP_NOT_IN_ITINERARY", e.getMessage()), "req_" + UUID.randomUUID());
	}

	/**
	 * 🔴 409 다. 이건 잘못된 요청이 아니라 <b>순서가 안 맞는</b> 것이다 — 화면은 출발을
	 * 누르라고 안내하면 되고, 400 으로 주면 「보낸 값이 틀렸다」로 읽혀 엉뚱한 곳을 고친다.
	 */
	@ExceptionHandler(ItineraryRunService.NotRunningException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiResponse<Void> handleNotRunning(ItineraryRunService.NotRunningException e) {
		return ApiResponse.failure(new ApiError("ITINERARY_NOT_RUNNING", e.getMessage()), "req_" + UUID.randomUUID());
	}
}
