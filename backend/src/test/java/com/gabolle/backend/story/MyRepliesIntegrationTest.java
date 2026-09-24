package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.story.application.MyRepliesService;
import com.gabolle.backend.story.presentation.dto.MyRepliesResponse;
import com.gabolle.testslice.StorySliceApplication;

/**
 * 마이페이지 「내 댓글」 (S15P21E201-1600). 원글을 지우면 댓글은 남는데 어떤 목록에도 안 나와, 쓴 사람이 자기 댓글을
 * 찾을 길이 없었다(운영 2026-09-25: 살아 있는 댓글 15개 중 11개).
 */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class MyRepliesIntegrationTest {

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private MyRepliesService service;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	private UUID me;

	private UUID other;

	@BeforeEach
	void setUp() {
		this.me = StoryFixture.insertUser(this.jdbc, "나");
		this.other = StoryFixture.insertUser(this.jdbc, "원글쓴이");
	}

	private UUID post(UUID author, String body, Instant at) {
		return StoryFixture.insertStory(this.jdbc, author, body, "PUBLIC", at);
	}

	/** 댓글은 공개 시각이 만든 시각과 같다(Story#reply). */
	private UUID reply(UUID author, UUID parent, String body, Instant at) {
		UUID id = StoryFixture.insertStory(this.jdbc, author, body, "PUBLIC", at);
		this.jdbc.update("UPDATE story SET parent_story_id = ? WHERE story_id = ?", parent, id);
		return id;
	}

	private MyRepliesResponse.Item onlyItem() {
		List<MyRepliesResponse.Item> items = this.service.list(this.me, null, null).items();
		assertThat(items).hasSize(1);
		return items.get(0);
	}

	@Test
	@DisplayName("원글이 살아 있으면 VISIBLE 과 본문 앞부분·작성자 이름이 실린다")
	void aLiveParentIsVisible() {
		UUID parent = post(this.other, "부산 감천문화마을 다녀왔어요", Instant.now().minus(Duration.ofHours(2)));
		UUID mine = reply(this.me, parent, "저도 가 보고 싶네요", Instant.now().minus(Duration.ofHours(1)));

		MyRepliesResponse.Item item = onlyItem();

		assertThat(item.reply().id()).isEqualTo(mine.toString());
		assertThat(item.reply().parentId()).isEqualTo(parent.toString());
		assertThat(item.parent().id()).isEqualTo(parent.toString());
		assertThat(item.parent().state()).isEqualTo("VISIBLE");
		assertThat(item.parent().bodyPreview()).isEqualTo("부산 감천문화마을 다녀왔어요");
		assertThat(item.parent().authorName()).isEqualTo("원글쓴이");
	}

	@Test
	@DisplayName("🔴 원글이 지워져도 내 댓글이 나온다 — DELETED, 본문·작성자는 없다")
	void aDeletedParentStillListsMyReply() {
		UUID parent = post(this.other, "곧 지울 글", Instant.now().minus(Duration.ofHours(2)));
		reply(this.me, parent, "고아가 될 댓글", Instant.now().minus(Duration.ofHours(1)));
		this.jdbc.update("UPDATE story SET deleted_at = now() WHERE story_id = ?", parent);

		MyRepliesResponse.Item item = onlyItem();

		assertThat(item.parent().state()).isEqualTo("DELETED");
		assertThat(item.parent().bodyPreview()).isNull();
		assertThat(item.parent().authorName()).isNull();
	}

	@Test
	@DisplayName("🔴 신고로 가려진 원글은 HIDDEN — 본문·작성자를 이 목록으로 엿볼 수 없다")
	void aModeratedParentIsHidden() {
		UUID parent = post(this.other, "신고당한 글", Instant.now().minus(Duration.ofHours(2)));
		reply(this.me, parent, "댓글", Instant.now().minus(Duration.ofHours(1)));
		this.jdbc.update("UPDATE story SET moderation_state = 'UNDER_REVIEW' WHERE story_id = ?", parent);

		MyRepliesResponse.Item item = onlyItem();

		assertThat(item.parent().state()).isEqualTo("HIDDEN");
		assertThat(item.parent().bodyPreview()).isNull();
		assertThat(item.parent().authorName()).isNull();
	}

	@Test
	@DisplayName("🔴 원글이 나만 보기로 바뀌었거나 작성자가 나를 차단했으면 HIDDEN — 지금 볼 수 없는 글은 안 싣는다")
	void aParentINoLongerMaySeeIsHidden() {
		UUID madePrivate = post(this.other, "나만 보기로 바꾼 글", Instant.now().minus(Duration.ofHours(3)));
		reply(this.me, madePrivate, "댓글 하나", Instant.now().minus(Duration.ofHours(2)));
		this.jdbc.update("UPDATE story SET visibility = 'PRIVATE' WHERE story_id = ?", madePrivate);
		UUID blocker = StoryFixture.insertUser(this.jdbc, "나를차단한사람");
		UUID blockersPost = post(blocker, "차단한 사람의 글", Instant.now().minus(Duration.ofHours(3)));
		reply(this.me, blockersPost, "댓글 둘", Instant.now().minus(Duration.ofHours(1)));
		StoryFixture.insertBlock(this.jdbc, blocker, this.me);

		List<MyRepliesResponse.Item> items = this.service.list(this.me, null, null).items();

		assertThat(items).hasSize(2).allSatisfy((item) -> {
			assertThat(item.parent().state()).isEqualTo("HIDDEN");
			assertThat(item.parent().bodyPreview()).isNull();
		});
	}

	@Test
	@DisplayName("🔴 남의 댓글·내 원글·내가 지운 댓글은 안 나온다 — 내가 쓴 살아 있는 댓글만")
	void onlyMyLiveRepliesAreListed() {
		UUID parent = post(this.other, "원글", Instant.now().minus(Duration.ofHours(5)));
		UUID mine = reply(this.me, parent, "내 댓글", Instant.now().minus(Duration.ofHours(4)));
		reply(this.other, parent, "남의 댓글", Instant.now().minus(Duration.ofHours(3)));
		post(this.me, "내 원글", Instant.now().minus(Duration.ofHours(2)));
		UUID deleted = reply(this.me, parent, "지운 내 댓글", Instant.now().minus(Duration.ofHours(1)));
		this.jdbc.update("UPDATE story SET deleted_at = now() WHERE story_id = ?", deleted);

		assertThat(this.service.list(this.me, null, null).items())
				.extracting((item) -> item.reply().id())
				.containsExactly(mine.toString());
	}

	@Test
	@DisplayName("대댓글도 나오고 parent 는 바로 위 댓글이다")
	void aNestedReplyPointsAtTheReplyAboveIt() {
		UUID root = post(this.other, "원글", Instant.now().minus(Duration.ofHours(3)));
		UUID theirs = reply(this.other, root, "남의 댓글", Instant.now().minus(Duration.ofHours(2)));
		reply(this.me, theirs, "대댓글", Instant.now().minus(Duration.ofHours(1)));

		MyRepliesResponse.Item item = onlyItem();

		assertThat(item.parent().id()).isEqualTo(theirs.toString());
		assertThat(item.parent().bodyPreview()).isEqualTo("남의 댓글");
	}

	@Test
	@DisplayName("최신순이고 커서로 이어진다 — 겹치거나 빠지지 않는다")
	void pagesAreNewestFirstAndContinueByCursor() {
		UUID parent = post(this.other, "원글", Instant.now().minus(Duration.ofHours(10)));
		UUID oldest = reply(this.me, parent, "첫째", Instant.now().minus(Duration.ofHours(3)));
		UUID middle = reply(this.me, parent, "둘째", Instant.now().minus(Duration.ofHours(2)));
		UUID newest = reply(this.me, parent, "셋째", Instant.now().minus(Duration.ofHours(1)));

		MyRepliesResponse first = this.service.list(this.me, null, 2);
		MyRepliesResponse second = this.service.list(this.me, first.nextCursor(), 2);

		assertThat(first.items()).extracting((item) -> item.reply().id())
				.containsExactly(newest.toString(), middle.toString());
		assertThat(first.nextCursor()).isNotNull();
		assertThat(second.items()).extracting((item) -> item.reply().id()).containsExactly(oldest.toString());
		assertThat(second.nextCursor()).isNull();
	}

	@Test
	@DisplayName("미리보기는 앞 60자 — 이모지를 반으로 자르지 않는다")
	void previewKeepsSixtyCharacters() {
		String body = "🌊".repeat(70);
		UUID parent = post(this.other, body, Instant.now().minus(Duration.ofHours(2)));
		reply(this.me, parent, "댓글", Instant.now().minus(Duration.ofHours(1)));

		String preview = onlyItem().parent().bodyPreview();

		assertThat(preview.codePointCount(0, preview.length())).isEqualTo(60);
		assertThat(preview).isEqualTo("🌊".repeat(60));
	}

	@Test
	@DisplayName("🔴 값이 없어도 parent 의 bodyPreview·authorName 키는 빠지지 않는다 — 계약이 string | null 이다")
	void nullFieldsKeepTheirKeys() throws Exception {
		UUID parent = post(this.other, "지울 글", Instant.now().minus(Duration.ofHours(2)));
		reply(this.me, parent, "댓글", Instant.now().minus(Duration.ofHours(1)));
		this.jdbc.update("UPDATE story SET deleted_at = now() WHERE story_id = ?", parent);

		ObjectMapper json = new ObjectMapper();
		JsonNode page = json.readTree(json.writeValueAsString(this.service.list(this.me, null, null)));

		JsonNode parentNode = page.get("items").get(0).get("parent");
		assertThat(parentNode.has("bodyPreview")).isTrue();
		assertThat(parentNode.has("authorName")).isTrue();
		assertThat(page.has("nextCursor")).isTrue();
	}

	/** 원글 상태를 글마다 물으면 한 쪽 20건이 수십 번 왕복한다. {@code StoryReactionResponseIntegrationTest} 와 같은 잣대. */
	@Test
	@DisplayName("🔴 댓글이 하나든 셋이든 질의 수가 같다 — 원글 상태를 글마다 묻지 않는다")
	void queryCountDoesNotGrowWithPageSize() {
		UUID a = post(this.other, "원글 가", Instant.now().minus(Duration.ofHours(9)));
		UUID b = post(StoryFixture.insertUser(this.jdbc, "다른사람"), "원글 나", Instant.now().minus(Duration.ofHours(8)));
		this.jdbc.update("UPDATE story SET visibility = 'FOLLOWERS' WHERE story_id = ?", b);
		UUID c = post(this.other, "원글 다", Instant.now().minus(Duration.ofHours(7)));
		reply(this.me, a, "하나", Instant.now().minus(Duration.ofHours(3)));
		reply(this.me, b, "둘", Instant.now().minus(Duration.ofHours(2)));
		reply(this.me, c, "셋", Instant.now().minus(Duration.ofHours(1)));

		long forOne = statementsDuring(() -> this.service.list(this.me, null, 1));
		long forThree = statementsDuring(() -> this.service.list(this.me, null, 3));

		assertThat(forThree).as("댓글이 셋일 때 질의가 더 나갔다 — 원글마다 묻고 있다").isEqualTo(forOne);
	}

	private long statementsDuring(Runnable work) {
		Statistics statistics = this.entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		boolean wasEnabled = statistics.isStatisticsEnabled();
		statistics.setStatisticsEnabled(true);
		try {
			long before = statistics.getPrepareStatementCount();
			work.run();
			return statistics.getPrepareStatementCount() - before;
		}
		finally {
			statistics.setStatisticsEnabled(wasEnabled);
		}
	}
}
