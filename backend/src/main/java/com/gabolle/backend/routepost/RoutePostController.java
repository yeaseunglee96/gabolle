package com.gabolle.backend.routepost;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;

/**
 * 루트 포스트(-2027)와 「이 장소가 들어간 루트」(-2028).
 *
 * <p>읽기는 로그인 없이도 된다(익명이면 차단 판정 없이 공개 글만). 쓰기는 로그인한 사람만.
 */
@RestController
@Profile({ "db", "dev" })
public class RoutePostController {

	/**
	 * 올리기 본문.
	 *
	 * @param dayIndex 0부터 센 하루. 없으면 일정 전체
	 */
	public record PublishRequest(String itineraryId, Integer dayIndex, String title, String body) {
	}

	private final RoutePostService service;

	public RoutePostController(RoutePostService service) {
		this.service = service;
	}

	@PostMapping("/api/v1/route-posts")
	public ResponseEntity<ApiResponse<RoutePostResponse>> publish(@RequestBody PublishRequest request,
			Authentication authentication) {
		UUID author = AuthenticatedUsers.requireId(authentication);
		if (request == null || request.itineraryId() == null || request.itineraryId().isBlank()) {
			throw new RoutePostExceptions.InvalidRouteException("ITINERARY_REQUIRED");
		}
		RoutePostResponse body = this.service.publish(author, new RoutePostService.PublishCommand(
				request.itineraryId(), request.dayIndex(), request.title(), request.body()));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(body, requestId()));
	}

	@GetMapping("/api/v1/route-posts")
	public ApiResponse<RoutePostResponse.Page> feed(
			@RequestParam(value = "sort", required = false, defaultValue = "RECENT") RoutePostService.Sort sort,
			@RequestParam(value = "cursor", required = false) String cursor,
			@RequestParam(value = "limit", required = false) Integer limit, Authentication authentication) {
		return ApiResponse.success(this.service.feed(viewer(authentication), sort, cursor, limit), requestId());
	}

	@GetMapping("/api/v1/route-posts/{routePostId}")
	public ApiResponse<RoutePostResponse> get(@PathVariable UUID routePostId, Authentication authentication) {
		return ApiResponse.success(this.service.get(routePostId, viewer(authentication)), requestId());
	}

	@DeleteMapping("/api/v1/route-posts/{routePostId}")
	public ResponseEntity<Void> delete(@PathVariable UUID routePostId, Authentication authentication) {
		this.service.delete(routePostId, AuthenticatedUsers.requireId(authentication));
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/api/v1/route-posts/{routePostId}/like")
	public ApiResponse<RoutePostResponse> like(@PathVariable UUID routePostId, Authentication authentication) {
		return react(routePostId, authentication, RoutePostService.Kind.LIKE);
	}

	@DeleteMapping("/api/v1/route-posts/{routePostId}/like")
	public ApiResponse<RoutePostResponse> unlike(@PathVariable UUID routePostId, Authentication authentication) {
		return ApiResponse.success(this.service.unreact(routePostId, AuthenticatedUsers.requireId(authentication),
				RoutePostService.Kind.LIKE), requestId());
	}

	@PutMapping("/api/v1/route-posts/{routePostId}/save")
	public ApiResponse<RoutePostResponse> save(@PathVariable UUID routePostId, Authentication authentication) {
		return react(routePostId, authentication, RoutePostService.Kind.SAVE);
	}

	@DeleteMapping("/api/v1/route-posts/{routePostId}/save")
	public ApiResponse<RoutePostResponse> unsave(@PathVariable UUID routePostId, Authentication authentication) {
		return ApiResponse.success(this.service.unreact(routePostId, AuthenticatedUsers.requireId(authentication),
				RoutePostService.Kind.SAVE), requestId());
	}

	/**
	 * 「이 루트로 여행 만들기」를 눌렀다고 알린다. 여행을 만드는 일은 앱이 기존 경로(빈 일정 + 장소 더하기)로
	 * 한다 — 여기는 인기 점수에 넣을 수만 센다. 같은 사람은 한 번만 센다.
	 */
	@PostMapping("/api/v1/route-posts/{routePostId}/copies")
	public ApiResponse<RoutePostResponse> copied(@PathVariable UUID routePostId, Authentication authentication) {
		return react(routePostId, authentication, RoutePostService.Kind.COPY);
	}

	@GetMapping("/api/v1/me/saved-route-posts")
	public ApiResponse<List<RoutePostResponse>> saved(Authentication authentication) {
		return ApiResponse.success(this.service.saved(AuthenticatedUsers.requireId(authentication)), requestId());
	}

	/** 이 장소가 들어간 공개 루트 — 인기순, 기본 5개(-2028). */
	@GetMapping("/api/v1/places/{placeId}/route-posts")
	public ApiResponse<List<RoutePostResponse>> byPlace(@PathVariable UUID placeId,
			@RequestParam(value = "limit", required = false) Integer limit, Authentication authentication) {
		return ApiResponse.success(this.service.byPlace(placeId, viewer(authentication), limit), requestId());
	}

	private ApiResponse<RoutePostResponse> react(UUID id, Authentication authentication, RoutePostService.Kind kind) {
		return ApiResponse.success(this.service.react(id, AuthenticatedUsers.requireId(authentication), kind),
				requestId());
	}

	private static UUID viewer(Authentication authentication) {
		return AuthenticatedUsers.optionalId(authentication).orElse(null);
	}

	private static String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
