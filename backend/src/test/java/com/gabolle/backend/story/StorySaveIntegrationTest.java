package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.story.application.StorySaveService;
import com.gabolle.backend.story.presentation.StorySaveController;
import com.gabolle.backend.story.presentation.StorySaveExceptionHandler;
import com.gabolle.testslice.StorySliceApplication;

/** 기록 저장(북마크). 한 사람이 한 글을 두 번 저장할 수 없다는 것을 DB 의 UNIQUE 제약이 판정한다. */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class StorySaveIntegrationTest {

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private StorySaveService saves;

	@Autowired
	private StorySaveController controller;

	@Autowired
	private StorySaveExceptionHandler handler;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mockMvc;

	private UUID author;

	private UUID reader;

	private UUID storyId;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.controller).setControllerAdvice(this.handler).build();
		this.author = StoryFixture.insertUser(this.jdbc, "글쓴이");
		this.reader = StoryFixture.insertUser(this.jdbc, "읽는사람");
		this.storyId = StoryFixture.insertStory(this.jdbc, this.author, "부산 감천문화마을", "PUBLIC",
				Instant.now().minus(Duration.ofHours(1)));
	}

	@Test
	@DisplayName("🔴 저장하면 표에 한 행이 남는다")
	void savingWritesARow() {
		this.saves.save(this.reader, this.storyId);

		assertThat(saveRows()).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 두 번 저장해도 행은 하나다 — 연타·재시도로 목록에 같은 글이 중복되면 안 된다")
	void savingTwiceDoesNotDuplicate() {
		this.saves.save(this.reader, this.storyId);
		this.saves.save(this.reader, this.storyId);

		assertThat(saveRows()).isEqualTo(1);
	}

	@Test
	@DisplayName("내 글도 저장할 수 있다 — 반응과 달리 인기순에 반영되지 않는 개인 북마크다")
	void authorCanSaveOwnStory() {
		this.saves.save(this.author, this.storyId);

		assertThat(saveRows()).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 안 저장했던 것을 취소해도 204 다 — 재시도가 오류로 보이면 안 된다")
	void cancellingWhatWasNeverSavedSucceeds() throws Exception {
		this.mockMvc.perform(delete("/api/v1/stories/{id}/save", this.storyId).principal(StoryFixture.as(this.reader)))
				.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("저장을 취소하면 목록에서 빠진다")
	void removingDropsFromList() throws Exception {
		this.saves.save(this.reader, this.storyId);
		this.saves.remove(this.reader, this.storyId);

		this.mockMvc.perform(get("/api/v1/me/saved-stories").principal(StoryFixture.as(this.reader)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.count").value(0));
	}

	@Test
	@DisplayName("저장 목록은 최근 순으로 뜬다")
	void listReturnsSavedStory() throws Exception {
		this.saves.save(this.reader, this.storyId);

		this.mockMvc.perform(get("/api/v1/me/saved-stories").principal(StoryFixture.as(this.reader)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.count").value(1))
				.andExpect(jsonPath("$.data.items[0].storyId").value(this.storyId.toString()));
	}

	@Test
	@DisplayName("🔴 남의 나만 보기 글은 못 저장한다 — 404이고, 표에도 안 남는다")
	void privateStoryOfSomeoneElseIsNotFound() throws Exception {
		UUID hidden = StoryFixture.insertStory(this.jdbc, this.author, "혼자 보는 기록", "PRIVATE",
				Instant.now().minus(Duration.ofHours(1)));

		this.mockMvc.perform(put("/api/v1/stories/{id}/save", hidden).principal(StoryFixture.as(this.reader)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("STORY_NOT_FOUND"));

		assertThat(rowsFor(hidden)).isZero();
	}

	@Test
	@DisplayName("🔴 나를 차단한 사람의 글은 못 저장한다 — 403")
	void blockedByAuthorIsForbidden() throws Exception {
		StoryFixture.insertBlock(this.jdbc, this.author, this.reader);

		this.mockMvc.perform(put("/api/v1/stories/{id}/save", this.storyId).principal(StoryFixture.as(this.reader)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("BLOCKED_BY_USER"));

		assertThat(saveRows()).isZero();
	}

	private int saveRows() {
		return rowsFor(this.storyId);
	}

	private int rowsFor(UUID storyId) {
		return this.jdbc.queryForObject("SELECT count(*) FROM story_save WHERE story_id = ?", Integer.class,
				storyId);
	}
}
