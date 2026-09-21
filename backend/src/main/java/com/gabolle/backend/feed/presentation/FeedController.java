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
 * 미리 만들어 둔 피드 조회. 여기서 계산하지 않고 저장된 줄을 꺼내 모양만 바꿔 낸다.
 *
 * <p>피드를 채우는 쪽(API·배치)이 아직 없어서, 지금 이 API 는 모든 사용자에게
 * {@code NOT_BUILT_YET} 을 답한다. 그게 정상이다.
 *
 * <p>요청자를 {@code X-User-Id} 헤더가 아니라 {@link Authentication} 에서 정한다.
 * 헤더는 부르는 쪽이 정하는 값이라, 믿으면 로그인한 사람이 남의 피드를 볼 수 있다.
 *
 * <p>{@code @Profile} 은 {@link FeedQueryService} 와 짝을 맞춘 것이다 — {@code no-db}
 * 프로필에서는 저장소 빈 자체가 없다.
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
	 * @param limit  상한 50. 넘기면 50 으로 깎는다
	 * @param cursor 이 위치 다음부터. 첫 장은 안 보낸다
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

		return ApiResponse.success(body, "req_" + UUID.randomUUID());
	}

	private FeedItemResponse toResponse(FeedItem item) {
		return new FeedItemResponse(item.position(), item.itemType(), item.itemId(), item.authorId(), item.score(),
				item.reasonCodes(), readPayload(item.payload()));
	}

	/**
	 * 파싱에 실패해도 줄을 버리지 않고 빈 객체를 준다. 페이로드는 화면을 꾸미는 값이라
	 * 그것 하나가 깨졌다고 추천 자체를 감추는 것은 과하다. {@code null} 로도 두지 않는다 —
	 * 앱이 매번 없을 수 있음을 다뤄야 하기 때문이다.
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
