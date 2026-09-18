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
 * 글에 좋아요·싫어요 — 진짜 PostgreSQL 위에서.
 *
 * <h2>🔴 왜 목이 아니라 진짜 DB 인가</h2>
 *
 * 이 기능이 지키기로 한 것 가운데 <b>자바가 아니라 DB 가 판정하는 것</b>이 셋이다 —
 * 한 사람이 한 글에 하나뿐이라는 것(PK), 종류가 둘뿐이라는 것(CHECK), 그리고 이벤트가
 * {@code event_outbox} 에 실제로 닿는다는 것({@code MANDATORY} 트랜잭션 · {@code aggregate_id}
 * 가 {@code UUID NOT NULL}). 목은 셋 다 건너뛴다 — {@code SavedPlaceEventIntegrationTest}
 * 가 같은 이유로 먼저 생겼다(S15P21E201-1080).
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

	/** 글을 쓴 사람. */
	private UUID author;

	/** 반응을 누르는 사람. 행동 개인화를 켜 둔다 — 이벤트가 남는지 보는 것이 주제이므로. */
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

	// ── 눌린 것이 표와 이벤트에 닿는가 ────────────────────────────────

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
		// 🔴 축은 누른 사람이다. 글을 축으로 삼으면 한 사람의 행동 이력을 한 줄로 못 읽는다.
		assertThat(events.get(0).get("aggregate_type")).isEqualTo("user");
		assertThat(events.get(0).get("aggregate_id")).hasToString(this.reader.toString());
		assertThat(events.get(0).get("producer")).hasToString("SERVER");
		assertThat((String) events.get(0).get("payload")).contains(this.storyId.toString());
	}

	/**
	 * 🔴 이 저장소에서 {@code dislike} 가 {@code event_outbox} 에 실제로 적히는 첫 자리다.
	 * {@code PLACE_DISLIKE} 는 종류만 있고 쓰는 곳이 없어 아무도 이 경로를 안 밟아 봤다.
	 */
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

	/**
	 * 🔴 앱의 재시도와 사람이 두 번 마음을 정한 것은 다르다.
	 *
	 * <p>단위 검사로는 이것을 증명할 수 없다 — 거기서는 저장소가 무엇을 돌려줄지 우리가
	 * 정해 준다. 여기서는 진짜 {@code ON CONFLICT} 가 판정한다.
	 */
	@Test
	@DisplayName("🔴 같은 값을 두 번 보내면 이벤트가 하나다 — 재시도가 신호를 부풀리면 안 된다")
	void resendingTheSameValueDoesNotDuplicateTheEvent() {
		this.reactions.react(this.reader, this.storyId, ReactionType.LIKE);
		this.reactions.react(this.reader, this.storyId, ReactionType.LIKE);

		assertThat(reactionRows()).isEqualTo(1);
		assertThat(eventCount("story_like", this.reader))
				.as("재시도마다 신호가 늘면 손가락 빠른 사람의 글이 인기순 위로 간다").isEqualTo(1);
	}

	// ── 막아야 하는 것 ────────────────────────────────────────────────

	/**
	 * 🔴 이 검사가 실제로 있었던 구멍을 막는다.
	 *
	 * <p>처음 구현은 {@code findVisibleById} 만 불렀다. 그 질의는 <b>지워졌거나 신고로 감춰진
	 * 글</b>만 거르고 공개 범위는 모른다. 그래서 남의 나만 보기 글 번호를 아는 사람이 반응을 눌러
	 * 보고 <b>204 냐 404 냐로 그 글의 존재를 알아낼 수 있었다.</b>
	 */
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

	/**
	 * 🔴 작성자만 막으면 <b>둘이 서로를 초대해 놓고 서로의 글에 누르는 것</b>과 구분이 안 된다.
	 * 그건 사실상 같은 사람이 자기 글을 올리는 것이다.
	 */
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

	// ── 취소 ──────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 취소하면 종류만 비고 행은 남는다 — 이벤트는 그대로다")
	void cancellingClearsTheReactionButKeepsTheRow() throws Exception {
		this.reactions.react(this.reader, this.storyId, ReactionType.LIKE);

		this.mockMvc.perform(delete("/api/v1/stories/{id}/reaction", this.storyId)
				.principal(StoryFixture.as(this.reader)))
				.andExpect(status().isNoContent());

		// 🔴 행이 남아야 「좋아요를 남긴 적이 있다」가 살아남는다 — 아래 검사가 그것에 기댄다.
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

	// ── 인기순이 읽을 자리 ────────────────────────────────────────────

	/**
	 * 🔴 「실시간 인기순」이 이 집계를 읽는다. 창 밖의 좋아요가 새면 인기순이 아니라
	 * 누적 순위가 된다.
	 */
	@Test
	@DisplayName("🔴 최근 좋아요 집계는 창 밖의 것을 안 센다")
	void recentLikeCountRespectsTheWindow() {
		this.reactions.react(this.reader, this.storyId, ReactionType.LIKE);
		this.jdbc.update("UPDATE story_reaction SET reacted_at = now() - interval '2 days' "
				+ "WHERE story_id = ? AND user_id = ?", this.storyId, this.reader);

		assertThat(likesInWindow()).as("창 밖의 좋아요가 세지면 「실시간」이 아니다").isZero();
	}

	/**
	 * 🔴 <b>이 검사가 실제로 있었던 결함을 막는다</b> — 리뷰에서 진짜 PostgreSQL 로 재 봤을 때
	 * 「현재 반응=LIKE, 24시간 집계=0」이 나왔다.
	 *
	 * <p>{@code created_at} 하나로 「처음 손댄 때」와 「지금 반응을 고른 때」를 같이 쓰고 있었다.
	 * 그래서 사흘 전에 싫어요를 눌렀던 사람이 오늘 좋아요로 바꾸면 행은 {@code LIKE} 인데
	 * 시각이 사흘 전이라 <b>오늘 눌린 좋아요가 인기 집계에서 통째로 빠졌다.</b> 마음을 바꾼
	 * 사람이 많은 글일수록 조용히 아래로 밀린다.
	 */
	@Test
	@DisplayName("🔴 예전에 싫어요였던 사람이 오늘 좋아요로 바꾸면 24시간 집계에 잡힌다")
	void switchingToLikeTodayCountsEvenIfTheFirstTouchWasOld() {
		this.reactions.react(this.reader, this.storyId, ReactionType.DISLIKE);
		// 사흘 전에 처음 손댄 것으로 되돌린다 — 그때가 창 밖이라는 것이 핵심이다.
		this.jdbc.update("UPDATE story_reaction SET created_at = now() - interval '3 days', "
				+ "reacted_at = now() - interval '3 days' WHERE story_id = ? AND user_id = ?",
				this.storyId, this.reader);

		this.reactions.react(this.reader, this.storyId, ReactionType.LIKE);

		assertThat(likesInWindow()).as("오늘 눌린 좋아요가 안 잡히면 인기순이 아니라 「처음 손댄 순」이다").isEqualTo(1);
		// 처음 손댄 때는 그대로다 — 그 칸까지 갱신하면 마음을 바꾸는 것만으로 이력이 지워진다.
		assertThat(this.jdbc.queryForObject(
				"SELECT created_at < now() - interval '2 days' FROM story_reaction "
						+ "WHERE story_id = ? AND user_id = ?", Boolean.class, this.storyId, this.reader))
				.as("created_at 은 처음 손댄 때로 남아야 한다").isTrue();
	}

	/**
	 * 🔴 <b>이 검사도 실제로 있었던 결함을 막는다</b> — 리뷰에서 5번 껐다 켜니 이벤트가 5건이었다.
	 *
	 * <p>취소가 행을 지웠기 때문에 다음 좋아요가 언제나 「처음 넣는 것」이 됐다. 표의 행은
	 * 0개인데 {@code event_outbox} 만 쌓였고, 그 신호를 개인화가 행동 이력으로 읽으므로
	 * <b>손가락질 몇 번으로 자기 이력을 임의로 부풀릴 수 있었다.</b>
	 */
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

	/** 🔴 껐다 켜도 이벤트가 하나인 것이 「싫어요는 따로 센다」를 망가뜨리지 않는지. */
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

	/** 🔴 종류를 DB 가 막는다 — 자바 열거형만 믿으면 다른 경로로 오타가 들어온다. */
	@Test
	@DisplayName("🔴 표에 LIKE·DISLIKE 말고는 안 들어간다 — CHECK 가 막는다")
	void databaseRejectsUnknownReaction() {
		assertThatThrownBy(() -> this.jdbc.update(
				"INSERT INTO story_reaction (story_id, user_id, reaction, created_at, reacted_at, updated_at) "
						+ "VALUES (?, ?, 'HEART', now(), now(), now())",
				this.storyId, this.reader))
				.hasMessageContaining("ck_story_reaction_value");
	}

	// ── 거들기 ────────────────────────────────────────────────────────

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

	/** 24시간 창 안의 이 글 좋아요 수 — 진짜 저장소 질의로 센다. */
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
