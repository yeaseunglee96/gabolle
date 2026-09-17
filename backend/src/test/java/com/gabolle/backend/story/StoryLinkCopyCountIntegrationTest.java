package com.gabolle.backend.story;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.story.application.StoryService;
import com.gabolle.testslice.StorySliceApplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S15P21E201-1215 — 링크를 복사하면 인용수가 <b>규칙대로</b> 오르는가.
 *
 * <h2>재는 것 — 완료 기준 그대로</h2>
 *
 * <ul>
 *   <li>같은 사람이 같은 글을 하루에 여러 번 복사해도 <b>1만</b> 는다</li>
 *   <li>날이 바뀌면 <b>또 1</b> 늘어난다</li>
 *   <li><b>작성자 본인</b>이 복사하면 안 는다 — 2026-09-18 결정</li>
 *   <li><b>익명 세션</b>으로 복사하면 늘고, 같은 세션이 또 복사하면 안 는다</li>
 *   <li>회원도 익명 세션도 아니면 <b>안 는다</b></li>
 *   <li>🔴 <b>볼 수 없는 글은 404</b> 다 — 찔러서 수를 올릴 수 없다</li>
 *   <li>🔴 <b>복사가 조회로 세어지지 않는다</b> — 두 수가 서로 안 섞인다</li>
 * </ul>
 *
 * <h2>🔴 날이 바뀌는 것을 시계로 재지 않는다</h2>
 *
 * {@code StoryViewCountIntegrationTest} 와 같은 방법이다 — 시계를 하루 앞으로 돌리는 대신
 * <b>이미 남은 낱개의 날짜를 어제로 되돌린다.</b> 결과가 같고(오늘 것이 없는 상태가 된다)
 * 시계 빈을 바꾸지 않아도 된다. 시계를 바꾸면 그 컨텍스트가 따로 캐시돼 앱이 한 번 더 뜬다.
 *
 * <h2>DB 가 없으면 건너뛴다</h2>
 *
 * 🔴 {@code PostgresAvailableCondition} 이 붙으므로 도커가 꺼진 PC 에서는 건너뛴 채 초록이다.
 * <b>진짜 판정은 CI 다.</b>
 */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class StoryLinkCopyCountIntegrationTest {

	private static final ZoneId COUNTING_ZONE = ZoneId.of("Asia/Seoul");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private StoryService storyService;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID author;

	private UUID sharer;

	private UUID storyId;

	private UUID anonymousSessionId;

	@BeforeEach
	void setUp() {
		this.author = StoryFixture.insertUser(this.jdbc, "작성자");
		this.sharer = StoryFixture.insertUser(this.jdbc, "퍼뜨리는 사람");
		this.storyId = StoryFixture.insertStory(this.jdbc, this.author, "링크 복사를 세는 글", "PUBLIC",
				Instant.now().minusSeconds(3600));
		this.anonymousSessionId = insertAnonymousSession();
	}

	@AfterEach
	void tearDown() {
		// 🔴 표를 비우지 않는다. 내가 만든 것만 지운다 — 같은 DB 를 여러 검사가 함께 쓴다.
		this.jdbc.update("DELETE FROM story_link_copy WHERE story_id = ?", this.storyId);
		this.jdbc.update("DELETE FROM story_view WHERE story_id = ?", this.storyId);
		this.jdbc.update("DELETE FROM story WHERE story_id = ?", this.storyId);
		this.jdbc.update("DELETE FROM anonymous_session WHERE session_id = ?", this.anonymousSessionId);
		this.jdbc.update("DELETE FROM app_user WHERE user_id IN (?, ?)", this.author, this.sharer);
	}

	@Test
	@DisplayName("🔴 같은 사람이 하루에 여러 번 복사해도 1만 는다")
	void sameMemberSameDayCountsOnce() {
		this.storyService.recordLinkCopy(this.storyId, this.sharer, null);
		this.storyService.recordLinkCopy(this.storyId, this.sharer, null);
		this.storyService.recordLinkCopy(this.storyId, this.sharer, null);

		assertThat(linkCopyCount()).as("한 사람이 세 번 복사했는데 수가 1이 아니다").isEqualTo(1);
		assertThat(linkCopyRows()).as("낱개가 하루에 하나가 아니다").isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 날이 바뀌면 또 1 늘어난다 — 「하루 한 번」이지 「한 번뿐」이 아니다")
	void nextDayCountsAgain() {
		this.storyService.recordLinkCopy(this.storyId, this.sharer, null);
		assertThat(linkCopyCount()).isEqualTo(1);

		// 시계를 돌리는 대신 남은 낱개를 어제 것으로 만든다 — 오늘 것이 없는 상태가 된다.
		this.jdbc.update("UPDATE story_link_copy SET copied_on = copied_on - 1 WHERE story_id = ?", this.storyId);

		this.storyService.recordLinkCopy(this.storyId, this.sharer, null);

		assertThat(linkCopyCount()).as("날이 바뀌었는데 수가 안 늘었다").isEqualTo(2);
		assertThat(linkCopyRows()).as("낱개가 날마다 하나씩이 아니다").isEqualTo(2);
	}

	@Test
	@DisplayName("🔴 작성자 본인이 복사하면 안 는다 — 그 수가 「남이 퍼뜨렸다」를 뜻해야 한다")
	void authorDoesNotCount() {
		this.storyService.recordLinkCopy(this.storyId, this.author, null);
		this.storyService.recordLinkCopy(this.storyId, this.author, null);

		assertThat(linkCopyCount()).as("작성자가 자기 글 링크를 복사했는데 수가 올랐다").isZero();
		assertThat(linkCopyRows()).as("작성자 복사가 낱개로 남았다").isZero();
	}

	@Test
	@DisplayName("🔴 수가 안 올라도 실패가 아니다 — 지금 수를 그대로 돌려준다")
	void notCountingIsNotAnError() {
		assertThat(this.storyService.recordLinkCopy(this.storyId, this.author, null).linkCopyCount())
				.as("수가 안 오른 것을 실패로 답하면 앱이 사용자에게 오류를 보여준다").isZero();
	}

	@Test
	@DisplayName("🔴 익명 세션으로 복사하면 는다. 같은 세션이 또 복사하면 안 는다")
	void anonymousSessionCountsOnce() {
		this.storyService.recordLinkCopy(this.storyId, null, this.anonymousSessionId);
		this.storyService.recordLinkCopy(this.storyId, null, this.anonymousSessionId);

		assertThat(linkCopyCount()).as("비회원 복사가 안 세어졌다 — 세는 것이 정해진 규칙이다").isEqualTo(1);
		assertThat(this.jdbc.queryForObject(
				"SELECT count(*) FROM story_link_copy WHERE story_id = ? AND anonymous_session_id = ?",
				Long.class, this.storyId, this.anonymousSessionId))
				.as("같은 익명 세션이 하루에 두 줄 남겼다").isEqualTo(1L);
	}

	@Test
	@DisplayName("🔴 회원도 익명 세션도 아니면 안 는다 — 하루 한 번을 지킬 방법이 없다")
	void unidentifiedActorDoesNotCount() {
		this.storyService.recordLinkCopy(this.storyId, null, null);
		this.storyService.recordLinkCopy(this.storyId, null, null);

		assertThat(linkCopyCount()).as("식별할 수 없는 복사가 세어졌다 — 누를 때마다 오른다").isZero();
		assertThat(linkCopyRows()).isZero();
	}

	@Test
	@DisplayName("🔴 볼 수 없는 글은 404 다 — 찔러서 수를 올릴 수 없고, 있다는 사실도 안 샌다")
	void hiddenStoryIsNotFound() {
		UUID privateStory = StoryFixture.insertStory(this.jdbc, this.author, "나만 보기 글", "PRIVATE",
				Instant.now().minusSeconds(3600));
		try {
			assertThatThrownBy(() -> this.storyService.recordLinkCopy(privateStory, this.sharer, null))
					.as("남의 나만 보기 글에 복사를 셀 수 있었다")
					.isInstanceOf(StoryService.StoryNotFoundException.class);

			assertThat(this.jdbc.queryForObject("SELECT link_copy_count FROM story WHERE story_id = ?",
					Integer.class, privateStory))
					.as("못 보는 글의 수가 올랐다 — 그 수가 곧 「그 글이 있다」는 사실의 유출이다").isZero();
		}
		finally {
			this.jdbc.update("DELETE FROM story_link_copy WHERE story_id = ?", privateStory);
			this.jdbc.update("DELETE FROM story WHERE story_id = ?", privateStory);
		}
	}

	@Test
	@DisplayName("🔴 복사는 조회로 세어지지 않는다 — 두 수가 서로 안 섞인다")
	void copyingDoesNotCountAsAView() {
		this.storyService.recordLinkCopy(this.storyId, this.sharer, null);

		assertThat(viewCount()).as("링크를 복사했는데 조회수가 올랐다").isZero();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM story_view WHERE story_id = ?",
				Long.class, this.storyId)).as("복사가 조회 낱개로 남았다").isZero();
	}

	@Test
	@DisplayName("응답이 그 수를 그대로 싣는다 — 같은 트랜잭션에서 올리므로 바로 보인다")
	void responseCarriesTheCount() {
		assertThat(this.storyService.recordLinkCopy(this.storyId, this.sharer, null).linkCopyCount())
				.as("처음 복사한 사람에게 1이 안 보인다").isEqualTo(1);
	}

	@Test
	@DisplayName("낱개의 날짜가 KST 기준 오늘이다 — 서버 시간대가 아니다")
	void copiedOnUsesCountingZone() {
		this.storyService.recordLinkCopy(this.storyId, this.sharer, null);

		assertThat(this.jdbc.queryForObject("SELECT copied_on FROM story_link_copy WHERE story_id = ?",
				LocalDate.class, this.storyId))
				.as("낱개 날짜가 KST 기준 오늘이 아니다").isEqualTo(LocalDate.now(COUNTING_ZONE));
	}

	private int linkCopyCount() {
		Integer n = this.jdbc.queryForObject("SELECT link_copy_count FROM story WHERE story_id = ?",
				Integer.class, this.storyId);
		return n == null ? 0 : n;
	}

	private int viewCount() {
		Integer n = this.jdbc.queryForObject("SELECT view_count FROM story WHERE story_id = ?",
				Integer.class, this.storyId);
		return n == null ? 0 : n;
	}

	private long linkCopyRows() {
		Long n = this.jdbc.queryForObject("SELECT count(*) FROM story_link_copy WHERE story_id = ?",
				Long.class, this.storyId);
		return n == null ? 0 : n;
	}

	private UUID insertAnonymousSession() {
		UUID sessionId = UUID.randomUUID();
		this.jdbc.update("INSERT INTO anonymous_session (session_id, token_hash, created_at, last_seen_at) "
				+ "VALUES (?, ?, now(), now())", sessionId, "hash-" + UUID.randomUUID());
		return sessionId;
	}
}
