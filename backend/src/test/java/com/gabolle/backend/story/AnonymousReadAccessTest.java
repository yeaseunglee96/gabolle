package com.gabolle.backend.story;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.common.security.GlobalAuthExceptionHandler;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.story.presentation.StoryController;
import com.gabolle.backend.story.presentation.StoryExceptionHandler;
import com.gabolle.backend.story.presentation.UserSocialController;
import com.gabolle.testslice.StorySliceApplication;

/**
 * 로그인하지 않은 사람이 작성자를 눌렀을 때 — S15P21E201-1373.
 *
 * <p>🔴 2026-09-21 배포본 실측(jinmiri). 피드와 기록 상세는 익명에게 열려 있는데
 * ({@code S15P21E201-974}·{@code -995}), <b>작성자 이름을 누르면 401</b> 이었다.
 *
 * <pre>
 *   GET /api/v1/users/{id}/profile   401
 *   GET /api/v1/users/{id}/stories   401
 * </pre>
 *
 * 여행 전에 둘러보는 사람이 가장 먼저 하는 일이 「이 사람 누구지」인데 그 자리에서 막혔다.
 *
 * <h2>🔴 이 시험이 지키는 두 가지</h2>
 *
 * <b>연 것은 읽기뿐이다.</b> 팔로우·차단은 그대로 로그인이 필요하다 — 아래 마지막 시험이
 * 그것을 잡는다. 읽기를 열면서 쓰기까지 열리는 것이 이 변경에서 가장 위험한 실수다.
 *
 * <p><b>익명에게는 공개 기록만 나간다.</b> 팔로워 공개 글이 익명에게 보이면 「팔로워에게만」이
 * 아무 뜻도 없는 말이 된다.
 */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class AnonymousReadAccessTest {

	@TempDir
	static Path storageRoot;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
		registry.add("gabolle.storage.root", () -> storageRoot.toString());
		registry.add("gabolle.storage.public-base-path", () -> StoryFixture.IMAGE_BASE);
	}

	@Autowired
	private StoryController storyController;

	@Autowired
	private UserSocialController userSocialController;

	@Autowired
	private StoryExceptionHandler handler;

	/** AuthException 을 401 로 번역하는 쪽. 이것이 없으면 「로그인 필요」가 500 으로 샌다. */
	@Autowired
	private GlobalAuthExceptionHandler authHandler;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mockMvc;

	private UUID author;
	private UUID follower;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.storyController, this.userSocialController)
				.setControllerAdvice(this.authHandler, this.handler).build();
		this.author = StoryFixture.insertUser(this.jdbc, "글쓴이");
		this.follower = StoryFixture.insertUser(this.jdbc, "팔로워");
	}

	@Test
	@DisplayName("🔴 로그인 없이 작성자 프로필을 읽는다 — 관계는 전부 «없음»으로 나간다")
	void anonymousReadsProfile() throws Exception {
		this.mockMvc.perform(get("/api/v1/users/{id}/profile", this.author))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.displayName").value("글쓴이"))
				// 관계가 없는 사람이다 — 나도 아니고, 팔로우도 차단도 없다.
				.andExpect(jsonPath("$.data.me").value(false))
				.andExpect(jsonPath("$.data.following").value(false))
				.andExpect(jsonPath("$.data.blocked").value(false));
	}

	@Test
	@DisplayName("🔴 로그인 없이 작성자의 기록을 읽되 «공개 글만» 본다")
	void anonymousReadsOnlyPublicStories() throws Exception {
		Instant hourAgo = Instant.now().minus(Duration.ofHours(1));
		UUID open = StoryFixture.insertStory(this.jdbc, this.author, "공개 글", "PUBLIC", hourAgo);
		StoryFixture.insertStory(this.jdbc, this.author, "팔로워 전용", "FOLLOWERS", hourAgo);

		this.mockMvc.perform(get("/api/v1/users/{id}/stories", this.author))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].id").value(open.toString()));
	}

	@Test
	@DisplayName("이 시험이 실제로 무언가를 가른다 — 팔로워는 팔로워 전용 글을 본다")
	void theRuleActuallySeparates() throws Exception {
		Instant hourAgo = Instant.now().minus(Duration.ofHours(1));
		StoryFixture.insertStory(this.jdbc, this.author, "공개 글", "PUBLIC", hourAgo);
		StoryFixture.insertStory(this.jdbc, this.author, "팔로워 전용", "FOLLOWERS", hourAgo);
		this.mockMvc.perform(put("/api/v1/users/{id}/follow", this.author).principal(StoryFixture.as(this.follower)))
				.andExpect(status().isOk());

		this.mockMvc.perform(get("/api/v1/users/{id}/stories", this.author).principal(StoryFixture.as(this.follower)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(2));
	}

	@Test
	@DisplayName("🔴 연 것은 읽기뿐이다 — 팔로우는 로그인 없이 못 한다")
	void writingStillNeedsAnAccount() throws Exception {
		this.mockMvc.perform(put("/api/v1/users/{id}/follow", this.author))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
	}
}
