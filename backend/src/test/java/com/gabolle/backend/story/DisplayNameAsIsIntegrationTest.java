package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

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
import com.gabolle.backend.story.application.FollowService;
import com.gabolle.backend.story.application.StoryResponseAssembler;
import com.gabolle.backend.story.presentation.dto.StoryResponse;
import com.gabolle.backend.story.repository.StoryRepository;
import com.gabolle.testslice.StorySliceApplication;

/**
 * 같은 사람의 이름이 기록 응답과 프로필 응답에서 같다 (S15P21E201-1655).
 *
 * <p>기록 응답은 이름을 HTML 인코딩해서 냈고 프로필 응답은 원문을 냈다 — 「A&B」 가 한 화면에서는 「A&amp;B」 였다. 이름은
 * 이제 어느 응답에서나 원문이고, 기록 본문은 인코딩을 유지한다(S15P21E201-835 — ZAP 이 잡은 자리의 방어층).
 */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class DisplayNameAsIsIntegrationTest {

	/** 인코딩되면 모양이 바뀌는 글자 다섯을 다 넣은 이름. */
	private static final String NAME = "A&B's \"공방\" <부산>";

	private static final String COAUTHOR_NAME = "C&D <동행>";

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private StoryResponseAssembler assembler;

	@Autowired
	private StoryRepository stories;

	@Autowired
	private FollowService followService;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	@DisplayName("🔴 기록의 작성자·공동 작성자 이름과 프로필 이름이 같다 — 셋 다 원문, 본문은 인코딩 유지")
	void theSameNameReadsTheSameEverywhere() {
		UUID author = StoryFixture.insertUser(this.jdbc, NAME);
		UUID coauthor = StoryFixture.insertUser(this.jdbc, COAUTHOR_NAME);
		UUID viewer = StoryFixture.insertUser(this.jdbc, "보는 사람");
		UUID storyId = StoryFixture.insertStory(this.jdbc, author, "A&B 가 다녀온 <광안리>", "PUBLIC",
				Instant.now().minus(Duration.ofHours(1)));
		this.jdbc.update("INSERT INTO story_coauthor (story_id, user_id, invited_by, joined_at) VALUES (?, ?, ?, ?)",
				storyId, coauthor, author, Instant.now().minus(Duration.ofMinutes(5)).atOffset(ZoneOffset.UTC));

		StoryResponse story = this.assembler.one(this.stories.findVisibleById(storyId).orElseThrow(), viewer,
				Instant.now());

		assertThat(story.author().displayName()).isEqualTo(NAME);
		assertThat(this.followService.profile(viewer, author).displayName())
				.as("프로필 응답도 같은 원문이다 — 화면마다 이름이 달라지지 않는다")
				.isEqualTo(story.author().displayName());
		assertThat(story.coauthors()).singleElement()
				.satisfies((c) -> assertThat(c.displayName()).isEqualTo(COAUTHOR_NAME));
		assertThat(this.followService.profile(viewer, coauthor).displayName()).isEqualTo(COAUTHOR_NAME);
		assertThat(story.body()).as("본문은 인코딩을 유지한다(S15P21E201-835)")
				.isEqualTo("A&amp;B 가 다녀온 &lt;광안리&gt;");
	}
}
