package com.gabolle.backend.feed.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.feed.application.FeedItem;
import com.gabolle.backend.feed.application.FeedPage;
import com.gabolle.backend.feed.application.FeedQueryService;
import com.gabolle.backend.feed.presentation.dto.FeedItemResponse;
import com.gabolle.backend.feed.presentation.dto.FeedPageResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 미리 만들어 둔 피드 조회 (S15P21E201-580).
 *
 * <pre>
 *   GET /api/v1/feed/home       앱을 켰을 때 볼 것
 *   GET /api/v1/feed/community  커뮤니티에 들어갔을 때 볼 것
 * </pre>
 *
 * <p>🔴 <b>이 컨트롤러는 계산하지 않는다.</b> 저장된 줄을 꺼내 모양만 바꿔 낸다.
 * 그것이 이 티켓의 전부다 — 무거운 일은 만들 때 이미 끝났다.
 *
 * <h2>🔴 없는 것: 만드는 쪽</h2>
 *
 * 피드를 채우는 API 도 배치도 이 MR 에 없다. 무엇을 어떤 순서로 넣을지는 추천 엔진이
 * 붙어야 정해지고, 지어낸 점수로 채워 두면 그 값이 계약처럼 굳어서 나중에 무엇이
 * 임시값이었는지 아무도 모른다. 그래서 <b>지금 이 API 는 모든 사용자에게
 * {@code NOT_BUILT_YET} 을 답한다.</b> 그게 정상이고, 앱은 그 상태를 먼저 그리면 된다.
 *
 * <p>🔴 인증 — 요청자를 {@code X-User-Id} 헤더가 아니라 {@link Authentication} 에서 정한다
 * (S15P21E201-610). 헤더는 부르는 쪽이 정하는 값이라, 그걸 믿으면 로그인한 사람이 남의
 * ID 를 실어 보내는 것만으로 <b>남의 피드를 볼 수 있다.</b>
 *
 * <p>🔴 {@code @Profile({"db","dev"})} — {@link FeedQueryService} 가 같은 조건이라 짝을
 * 맞춘다. {@code no-db} 프로필에서는 저장소 빈 자체가 없다.
 */
@RestController
@RequestMapping("/api/v1/feed")
@Profile({ "db", "dev" })
public class FeedController {

	private final FeedQueryService feedQueryService;

	private final ObjectMapper objectMapper;

	public FeedController(FeedQueryService feedQueryService, ObjectMapper objectMapper) {
		this.feedQueryService = feedQueryService;
		this.objectMapper = objectMapper;
	}

	/**
	 * @param limit  한 번에 받을 줄 수. 상한 50, 넘기면 50 으로 깎는다
	 * @param cursor 이 위치 <b>다음</b>부터. 첫 장은 안 보낸다
	 */
	@GetMapping("/home")
	public ApiResponse<FeedPageResponse> home(Authentication authentication,
			@RequestParam(required = false) Integer limit, @RequestParam(required = false) Integer cursor) {

		UUID userId = AuthenticatedUsers.requireId(authentication);
		return respond(this.feedQueryService.home(userId, limit, cursor));
	}

	@GetMapping("/community")
	public ApiResponse<FeedPageResponse> community(Authentication authentication,
			@RequestParam(required = false) Integer limit, @RequestParam(required = false) Integer cursor) {

		UUID userId = AuthenticatedUsers.requireId(authentication);
		return respond(this.feedQueryService.community(userId, limit, cursor));
	}

	private ApiResponse<FeedPageResponse> respond(FeedPage page) {
		List<FeedItemResponse> items = page.items().stream().map(this::toResponse).toList();

		FeedPageResponse body = new FeedPageResponse(page.buildId(), items, page.nextCursor(), page.stale(),
				page.builtAt(), (page.emptyReason() == null) ? null : page.emptyReason().name());

		// 공통 envelope — API 명세 2.1 의 data / error / meta.
		return ApiResponse.success(body, "req_" + UUID.randomUUID());
	}

	private FeedItemResponse toResponse(FeedItem item) {
		return new FeedItemResponse(item.position(), item.itemType(), item.itemId(), item.authorId(), item.score(),
				item.reasonCodes(), readPayload(item.payload()));
	}

	/**
	 * 저장된 JSON 문자열을 응답에 실을 수 있는 모양으로 바꾼다.
	 *
	 * <p>🔴 파싱에 실패하면 <b>줄을 버리지 않고 빈 객체를 준다.</b> 페이로드는 화면을
	 * 꾸미는 값이라, 그것 하나가 깨졌다고 추천 자체를 안 보여주는 것은 과하다.
	 * 다만 {@code null} 로 두지도 않는다 — 앱이 매번 없을 수 있음을 다뤄야 하기 때문이다.
	 *
	 * <p>DB 는 {@code payload} 가 JSON 객체임을 이미 검사한다
	 * ({@code ck_user_feed_payload_object}). 그래서 여기 걸리는 것은 사실상
	 * "DB 를 안 거치고 들어온 값" 뿐이고, 그건 테스트에서만 생긴다.
	 */
	private JsonNode readPayload(String raw) {
		if (raw == null || raw.isBlank()) {
			return this.objectMapper.createObjectNode();
		}
		try {
			return this.objectMapper.readTree(raw);
		}
		catch (RuntimeException ex) {
			return this.objectMapper.createObjectNode();
		}
	}
}
