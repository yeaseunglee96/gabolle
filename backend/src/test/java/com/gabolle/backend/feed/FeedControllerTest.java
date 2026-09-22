package com.gabolle.backend.feed;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.feed.application.FeedItem;
import com.gabolle.backend.feed.application.FeedPage;
import com.gabolle.backend.feed.application.FeedQueryService;
import com.gabolle.backend.feed.presentation.FeedController;
import com.gabolle.backend.feed.presentation.dto.FeedPageResponse;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 조회 API 가 내보내는 모양만 본다. Spring 컨텍스트를 안 띄우는 것은, 띄우면 보안 설정과
 * DB 까지 딸려 와 남의 미완성 코드 때문에 빨개지기 때문이다. DB 계약은
 * {@code FeedBuildConstraintIntegrationTest} 가 따로 본다.
 */
class FeedControllerTest {

	private final FeedQueryService feedQueryService = mock(FeedQueryService.class);

	private final FeedController controller = new FeedController(this.feedQueryService, new ObjectMapper());

	private final UUID userId = UUID.randomUUID();

	private final Authentication authentication = new UsernamePasswordAuthenticationToken(this.userId.toString(), null,
			List.of());

	@Test
	@DisplayName("저장된 페이로드가 따옴표에 갇힌 문자열이 아니라 진짜 객체로 나간다")
	void 페이로드는_객체로_나간다() {
		UUID buildId = UUID.randomUUID();
		UUID placeId = UUID.randomUUID();
		when(this.feedQueryService.home(eq(this.userId), any(), any()))
			.thenReturn(new FeedPage(buildId, List.of(new FeedItem(0, "PLACE", placeId, null, 0.87,
					List.of("TASTE_MATCH"), "{\"name\":\"광안리\",\"walkMinutes\":12}")), null, false,
					OffsetDateTime.now(), null));

		ApiResponse<FeedPageResponse> response = this.controller.home(this.authentication, null, null);

		var payload = response.data().items().get(0).payload();
		assertThat(payload.isObject()).isTrue();
		assertThat(payload.get("name").asString()).isEqualTo("광안리");
		assertThat(payload.get("walkMinutes").asInt()).isEqualTo(12);
	}

	@Test
	@DisplayName("아직 안 만들어진 피드는 빈 목록에 이유를 함께 담아 답한다")
	void 빈_피드에는_이유가_붙는다() {
		when(this.feedQueryService.home(eq(this.userId), any(), any())).thenReturn(FeedPage.notBuiltYet());

		ApiResponse<FeedPageResponse> response = this.controller.home(this.authentication, null, null);

		assertThat(response.data().items()).isEmpty();
		assertThat(response.data().buildId()).isNull();
		assertThat(response.data().emptyReason()).isEqualTo("NOT_BUILT_YET");
	}

	@Test
	@DisplayName("낡은 피드도 내용은 그대로 주되 낡았다고 표시한다")
	void 낡음을_숨기지_않는다() {
		when(this.feedQueryService.community(eq(this.userId), any(), any()))
			.thenReturn(new FeedPage(UUID.randomUUID(),
					List.of(new FeedItem(0, "POST", UUID.randomUUID(), UUID.randomUUID(), 1.0, List.of("FOLLOWED"),
							"{}")),
					null, true, OffsetDateTime.now().minusDays(1), null));

		ApiResponse<FeedPageResponse> response = this.controller.community(this.authentication, null, null);

		assertThat(response.data().stale()).isTrue();
		assertThat(response.data().items()).hasSize(1);
	}

	@Test
	@DisplayName("로그인하지 않았으면 피드를 주지 않는다")
	void 로그인_없이는_안_준다() {
		assertThatThrownBy(() -> this.controller.home(null, null, null)).isInstanceOf(AuthException.class);
	}
}
