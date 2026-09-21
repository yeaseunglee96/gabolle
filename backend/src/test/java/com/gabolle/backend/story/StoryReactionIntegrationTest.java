package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.story.application.StoryReactionService;
import com.gabolle.backend.story.domain.ReactionType;
import com.gabolle.backend.story.repository.StoryReactionRepository;
import com.gabolle.backend.story.presentation.StoryExceptionHandler;
import com.gabolle.backend.story.presentation.StoryReactionController;
import com.gabolle.testslice.StorySliceApplication;

/**
 * 글에 좋아요·싫어요. 목이 아니라 진짜 PostgreSQL 이 필요하다 — 한 사람에 하나(PK), 종류가 둘뿐
 * (CHECK), 이벤트가 {@code event_outbox} 에 실제로 닿는 것은 자바가 아니라 DB 가 판정한다.
 */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class StoryReactionIntegrationTest {

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private StoryReactionService reactions;

	@Autowired
	private StoryReactionRepository repo;

	@Autowired
	private StoryReactionController controller;

	@Autowired
	private StoryExceptionHandler handler;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mockMvc;

	private UUID author;

	/** 행동 개인화를 켜 둔다. 꺼져 있으면 이벤트가 안 남는다. */
	private UUID reader;

	private UUID storyId;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.controller).setControllerAdvice(this.handler).build();
		this.author = StoryFixture.insertUser(this.jdbc, "글쓴이");
		this.reader = StoryFixture.insertUser(this.jdbc, "읽는사람");
		enableBehavior(this.reader);
		this.storyId = StoryFixture.insertStory(this.jdbc, this.author, "부산 감천문화마을", "PUBLIC",
				Instant.now().minus(Duration.ofHours(1)));
	}

	@Test
	@DisplayName("🔴 좋아요를 누르면 표에 한 행, event_outbox 에 story_like 가 남는다")
	void likeWritesRowAndEvent() {
		this.reactions.react(this.reader, this.storyId, ReactionType.LIKE);

		assertThat(reactionRows()).as("반응이 표에 안 남았다").isEqualTo(1);

		List<Map<String, Object>> events = this.jdbc.queryForList(
				"SELECT aggregate_type, aggregate_id, producer, payload::text AS payload "
						+ "FROM event_outbox WHERE event_type = ? AND user_id = ?",
				"story_like", this.reader);

		assertThat(events).as("눌렸는데 이벤트가 없다 — 배관이 끊긴 것이다").hasSize(1);
		// 축은 글이 아니라 누른 사람이다. 글을 축으로 삼으면 한 사람의 행동 이력을 한 줄로 못 읽는다.
		assertThat(events.get(0).get("aggregate_type")).isEqualTo("user");
		assertThat(events.get(0).get("aggregate_id")).hasToString(this.reader.toString());
		assertThat(events.get(0).get("producer")).hasToString("SERVER");
		assertThat((String) events.get(0).get("payload")).contains(this.storyId.toString());
	}

	@Test
	@DisplayName("🔴 싫어요도 남는다 — story_dislike 가 실제로 적히는 첫 경로다")
	void dislikeWritesEvent() {
		this.reactions.react(this.reader, this.storyId, ReactionType.DISLIKE);

		assertThat(eventCount("story_dislike", this.reader)).isEqualTo(1);
		assertThat(this.jdbc.queryForObject(
				"SELECT reaction FROM story_reaction WHERE story_id = ? AND user_id = ?", String.class,
				this.storyId, this.reader)).isEqualTo("DISLIKE");
	}

	@Test
	@DisplayName("🔴 좋아요를 싫어요로 바꾸면 행은 그대로 하나다 — 늘면 인기순이 부푼다")
	void switchingChangesTheRowInsteadOfAddingOne() {
		this.reactions.react(this.reader, this.storyId, ReactionType.LIKE);
		this.reactions.react(this.reader, this.storyId, ReactionType.DISLIKE);

		assertThat(reactionRows()).as("바꾼 것이 행을 늘렸다 — 「좋아요도 눌렀고 싫어요도 눌렀다」로 읽힌다").isEqualTo(1);
		// 마음을 두 번 정한 것은 두 사건이다. 이벤트는 둘 다 남는다.
		assertThat(eventCount("story_like", this.reader)).isEqualTo(1);
		assertThat(eventCount("story_dislike", this.reader)).isEqualTo(1);
	}

	/** 판정하는 것은 진짜 {@code ON CONFLICT} 다. 저장소를 흉내 내면 이 성질을 증명할 수 없다. */
	@Test
	@DisplayName("🔴 같은 값을 두 번 보내면 이벤트가 하나다 — 재시도가 신호를 부풀리면 안 된다")
	void resendingTheSameValueDoesNotDuplicateTheEvent() {
		this.reactions.react(this.reader, this.storyId, ReactionType.LIKE);
		this.reactions.react(this.reader, this.storyId, ReactionType.LIKE);

		assertThat(reactionRows()).isEqualTo(1);
		assertThat(eventCount("story_like", this.reader))
				.as("재시도마다 신호가 늘면 손가락 빠른 사람의 글이 인기순 위로 간다").isEqualTo(1);
	}

	/** {@code findVisibleById} 는 지워짐·감춰짐만 거르고 공개 범위는 모른다. 공개 범위 검사가 따로 필요하다. */
	@Test
	@DisplayName("🔴 남의 나만 보기 글에는 못 단다 — 404 이고, 표에도 안 남는다")
	void privateStoryOfSomeoneElseIsNotFound() throws Exception {
		UUID hidden = StoryFixture.insertStory(this.jdbc, this.author, "혼자 보는 기록", "PRIVATE",
				Instant.now().minus(Duration.ofHours(1)));

		this.mockMvc.perform(put("/api/v1/stories/{id}/reaction", hidden)
				.principal(StoryFixture.as(this.reader))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reaction\":\"LIKE\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("STORY_NOT_FOUND"));

		assertThat(rowsFor(hidden)).as("막았다면서 행이 남으면 막은 것이 아니다").isZero();
	}

	@Test
	@DisplayName("🔴 아직 공개 시각이 안 된 글도 404 다 — 예약 발행이 새면 안 된다")
	void unpublishedStoryIsNotFound() throws Exception {
		UUID future = StoryFixture.insertStory(this.jdbc, this.author, "내일 올라갈 기록", "PUBLIC",
				Instant.now().plus(Duration.ofDays(1)));

		this.mockMvc.perform(put("/api/v1/stories/{id}/reaction", future)
				.principal(StoryFixture.as(this.reader))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reaction\":\"LIKE\"}"))
				.andExpect(status().isNotFound());

		assertThat(rowsFor(future)).isZero();
	}

	@Test
	@DisplayName("🔴 내 글에는 못 단다 — 409. 인기순이 붙으면 자기 글을 올리는 길이 된다")
	void ownStoryIsRejected() throws Exception {
		this.mockMvc.perform(put("/api/v1/stories/{id}/reaction", this.storyId)
				.principal(StoryFixture.as(this.author))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reaction\":\"LIKE\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("STORY_REACTION_OWN"));

		assertThat(reactionRows()).isZero();
	}

	@Test
	@DisplayName("🔴 공동 작성자도 못 단다 — 함께 쓰는 글은 내 글이다")
	void coauthorIsRejected() {
		UUID coauthor = StoryFixture.insertUser(this.jdbc, "함께쓴사람");
		this.jdbc.update(
				"INSERT INTO story_coauthor (story_id, user_id, invited_by, joined_at) VALUES (?, ?, ?, now())",
				this.storyId, coauthor, this.author);

		assertThatThrownBy(() -> this.reactions.react(coauthor, this.storyId, ReactionType.LIKE))
				.isInstanceOf(StoryReactionService.OwnReactionNotAllowedException.class);

		assertThat(reactionRows()).isZero();
	}

	@Test
	@DisplayName("🔴 개인화를 끈 사람은 이벤트가 안 남지만 반응은 저장된다")
	void optedOutUserReactsWithoutEvent() {
		UUID optedOut = StoryFixture.insertUser(this.jdbc, "개인화끈사람");   // fixture 기본값이 EXPLICIT_ONLY 다

		this.reactions.react(optedOut, this.storyId, ReactionType.LIKE);

		assertThat(eventCount("story_like", optedOut)).as("껐는데 남으면 동의를 어긴 것이다").isZero();
		assertThat(this.jdbc.queryForObject(
				"SELECT count(*) FROM story_reaction WHERE story_id = ? AND user_id = ?", Integer.class,
				this.storyId, optedOut)).as("안 모으는 것이 기능을 막는 것이 되면 안 된다").isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 취소하면 종류만 비고 행은 남는다 — 이벤트는 그대로다")
	void cancellingClearsTheReactionButKeepsTheRow() throws Exception {
		this.reactions.react(this.reader, this.storyId, ReactionType.LIKE);

		this.mockMvc.perform(delete("/api/v1/stories/{id}/reaction", this.storyId)
				.principal(StoryFixture.as(this.reader)))
				.andExpect(status().isNoContent());

		// 행이 남아야 좋아요를 남긴 적이 있다는 사실이 살아남는다.
		assertThat(reactionRows()).as("행까지 지우면 껐다 켠 것과 처음 누른 것을 못 가른다").isEqualTo(1);
		assertThat(this.jdbc.queryForObject(
				"SELECT reaction FROM story_reaction WHERE story_id = ? AND user_id = ?", String.class,
				this.storyId, this.reader)).as("취소했으면 종류는 비어야 한다").isNull();
		assertThat(likesInWindow()).as("취소한 것이 인기 집계에 남으면 안 된다").isZero();
		assertThat(eventCount("story_like", this.reader))
				.as("취소까지 신호로 남기면 눌렀다 취소한 사람이 안 누른 사람보다 신호가 많아진다").isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 안 눌렀던 것을 취소해도 204 다 — 재시도가 오류로 보이면 안 된다")
	void cancellingWhatWasNeverPressedSucceeds() throws Exception {
		this.mockMvc.perform(delete("/api/v1/stories/{id}/reaction", this.storyId)
				.principal(StoryFixture.as(this.reader)))
				.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("🔴 최근 좋아요 집계는 창 밖의 것을 안 센다")
	void recentLikeCountRespectsTheWindow() {
		this.reactions.react(this.reader, this.storyId, ReactionType.LIKE);
		this.jdbc.update("UPDATE story_reaction SET reacted_at = now() - interval '2 days' "
				+ "WHERE story_id = ? AND user_id = ?", this.storyId, this.reader);

		assertThat(likesInWindow()).as("창 밖의 좋아요가 세지면 「실시간」이 아니다").isZero();
	}

	/**
	 * 집계는 {@code reacted_at} 을 본다. {@code created_at} 하나로 처음 손댄 때와 반응을 고른 때를
	 * 겸하면, 마음을 바꾼 사람의 오늘 좋아요가 창 밖으로 밀려 집계에서 빠진다.
	 */
	@Test
	@DisplayName("🔴 예전에 싫어요였던 사람이 오늘 좋아요로 바꾸면 24시간 집계에 잡힌다")
	void switchingToLikeTodayCountsEvenIfTheFirstTouchWasOld() {
		this.reactions.react(this.reader, this.storyId, ReactionType.DISLIKE);
		// 처음 손댄 때를 창 밖(사흘 전)으로 되돌린다.
		this.jdbc.update("UPDATE story_reaction SET created_at = now() - interval '3 days', "
				+ "reacted_at = now() - interval '3 days' WHERE story_id = ? AND user_id = ?",
				this.storyId, this.reader);

		this.reactions.react(this.reader, this.storyId, ReactionType.LIKE);

		assertThat(likesInWindow()).as("오늘 눌린 좋아요가 안 잡히면 인기순이 아니라 「처음 손댄 순」이다").isEqualTo(1);
		// created_at 까지 갱신하면 마음을 바꾸는 것만으로 처음 손댄 이력이 지워진다.
		assertThat(this.jdbc.queryForObject(
				"SELECT created_at < now() - interval '2 days' FROM story_reaction "
						+ "WHERE story_id = ? AND user_id = ?", Boolean.class, this.storyId, this.reader))
				.as("created_at 은 처음 손댄 때로 남아야 한다").isTrue();
	}

	@Test
	@DisplayName("🔴 껐다 켰다를 다섯 번 해도 이벤트는 하나다 — 이벤트는 (글,사람,종류)당 하나다")
	void togglingDoesNotInflateTheEventCount() {
		for (int i = 0; i < 5; i++) {
			this.reactions.react(this.reader, this.storyId, ReactionType.LIKE);
			this.reactions.remove(this.reader, this.storyId);
		}
		this.reactions.react(this.reader, this.storyId, ReactionType.LIKE);

		assertThat(eventCount("story_like", this.reader))
				.as("껐다 켜는 것으로 신호를 늘릴 수 있으면 인기순도 개인화도 그만큼 틀린다").isEqualTo(1);
		assertThat(likesInWindow()).as("마지막에 켜 뒀으므로 집계에는 잡혀야 한다").isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 종류가 다르면 각각 하나씩 남는다 — 좋아요 하나, 싫어요 하나")
	void eachReactionTypeGetsItsOwnSingleEvent() {
		this.reactions.react(this.reader, this.storyId, ReactionType.LIKE);
		this.reactions.react(this.reader, this.storyId, ReactionType.DISLIKE);
		this.reactions.react(this.reader, this.storyId, ReactionType.LIKE);
		this.reactions.react(this.reader, this.storyId, ReactionType.DISLIKE);

		assertThat(eventCount("story_like", this.reader)).isEqualTo(1);
		assertThat(eventCount("story_dislike", this.reader)).isEqualTo(1);
	}

	/** 자바 열거형만으로는 SQL 로 직접 들어오는 값을 못 막는다. */
	@Test
	@DisplayName("🔴 표에 LIKE·DISLIKE 말고는 안 들어간다 — CHECK 가 막는다")
	void databaseRejectsUnknownReaction() {
		assertThatThrownBy(() -> this.jdbc.update(
				"INSERT INTO story_reaction (story_id, user_id, reaction, created_at, reacted_at, updated_at) "
						+ "VALUES (?, ?, 'HEART', now(), now(), now())",
				this.storyId, this.reader))
				.hasMessageContaining("ck_story_reaction_value");
	}

	private void enableBehavior(UUID userId) {
		this.jdbc.update("UPDATE app_user SET personalization_mode = ? WHERE user_id = ?", "BEHAVIOR_ENABLED", userId);
	}

	private int reactionRows() {
		return rowsFor(this.storyId);
	}

	private int rowsFor(UUID storyId) {
		return this.jdbc.queryForObject("SELECT count(*) FROM story_reaction WHERE story_id = ?", Integer.class,
				storyId);
	}

	/** 24시간 창 안의 이 글 좋아요 수. */
	private long likesInWindow() {
		return this.repo.countRecentLikes(OffsetDateTime.now().minusHours(24)).stream()
				.filter(r -> r.getStoryId().equals(this.storyId))
				.mapToLong(StoryReactionRepository.StoryLikeCount::getLikes)
				.sum();
	}

	private int eventCount(String wireName, UUID userId) {
		return this.jdbc.queryForObject("SELECT count(*) FROM event_outbox WHERE event_type = ? AND user_id = ?",
				Integer.class, wireName, userId);
	}
}
