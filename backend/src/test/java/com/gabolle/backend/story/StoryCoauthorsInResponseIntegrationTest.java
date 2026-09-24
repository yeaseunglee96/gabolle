package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
import com.gabolle.backend.story.application.StoryResponseAssembler;
import com.gabolle.backend.story.domain.Story;
import com.gabolle.backend.story.presentation.dto.StoryResponse;
import com.gabolle.backend.story.repository.StoryRepository;
import com.gabolle.testslice.StorySliceApplication;

/**
 * 공동 작성자가 기록 응답까지 닿는가. 피드 카드가 글마다 참여자 목록을 따로 부르지 않게 하려고 더한 칸이다.
 *
 * <p>기록 응답은 목록·상세·댓글·여행 기록 모두 {@link StoryResponseAssembler} 한 곳에서만 만들어진다 —
 * 그래서 조립기를 재면 모든 경로를 잰 것이다.
 */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class StoryCoauthorsInResponseIntegrationTest {

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private StoryResponseAssembler assembler;

	@Autowired
	private StoryRepository stories;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	private UUID author;

	private UUID storyId;

	@BeforeEach
	void setUp() {
		this.author = StoryFixture.insertUser(this.jdbc, "글쓴이");
		this.storyId = StoryFixture.insertStory(this.jdbc, this.author, "부산 감천문화마을", "PUBLIC",
				Instant.now().minus(Duration.ofHours(1)));
	}

	@Test
	@DisplayName("🔴 합류한 사람이 합류 순서대로 실리고, 만든 사람은 안 들어간다")
	void coauthorsReachTheResponseInJoinOrder() {
		UUID early = StoryFixture.insertUser(this.jdbc, "먼저온사람");
		UUID late = StoryFixture.insertUser(this.jdbc, "나중온사람");
		Instant base = Instant.now().minus(Duration.ofMinutes(30));
		// 늦게 합류한 사람을 먼저 넣는다 — 넣은 순서가 아니라 합류 시각으로 줄 세우는지 보려고.
		insertCoauthor(this.storyId, late, base.plus(Duration.ofMinutes(10)));
		insertCoauthor(this.storyId, early, base);

		StoryResponse response = assemble(this.storyId, this.author);

		assertThat(response.coauthors())
				.extracting(StoryResponse.Coauthor::id, StoryResponse.Coauthor::displayName)
				.containsExactly(
						tuple(early.toString(), "먼저온사람"),
						tuple(late.toString(), "나중온사람"));
		assertThat(response.coauthors()).extracting(StoryResponse.Coauthor::id)
				.as("만든 사람은 author 칸에 있다 — 두 번 그리면 카드에 같은 이름이 둘 뜬다")
				.doesNotContain(this.author.toString());
	}

	@Test
	@DisplayName("🔴 공동 작성자가 없으면 빈 배열이다 — null 이 아니다")
	void noCoauthorsIsEmptyArrayNotNull() throws Exception {
		StoryResponse response = assemble(this.storyId, null);

		assertThat(response.coauthors()).isNotNull().isEmpty();
		JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(response));
		assertThat(json.get("coauthors").isArray()).as("화면이 .map 을 바로 부를 수 있어야 한다").isTrue();
	}

	@Test
	@DisplayName("🔴 탈퇴한 공동 작성자는 이름이 null 로 실린다 — 키는 빠지지 않는다")
	void withdrawnCoauthorHasNullName() throws Exception {
		UUID gone = StoryFixture.insertUser(this.jdbc, "떠난사람");
		insertCoauthor(this.storyId, gone, Instant.now().minus(Duration.ofMinutes(5)));
		this.jdbc.update("UPDATE app_user SET deleted_at = now(), status = 'DELETED' WHERE user_id = ?", gone);

		StoryResponse response = assemble(this.storyId, null);

		assertThat(response.coauthors()).singleElement().satisfies(c -> {
			assertThat(c.id()).isEqualTo(gone.toString());
			assertThat(c.displayName()).as("탈퇴 전 이름이 새어 나가면 안 된다").isNull();
		});
		JsonNode coauthor = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(response))
				.get("coauthors").get(0);
		assertThat(coauthor.has("displayName")).as("계약은 string | null — 키가 없으면 undefined 가 된다").isTrue();
	}

	@Test
	@DisplayName("🔴 공동 작성자 이름도 자유 입력이라 작성자 이름처럼 인코딩된다")
	void coauthorNameIsHtmlEncoded() {
		UUID sneaky = StoryFixture.insertUser(this.jdbc, "<script>alert(1)</script>");
		insertCoauthor(this.storyId, sneaky, Instant.now().minus(Duration.ofMinutes(5)));

		assertThat(assemble(this.storyId, null).coauthors().get(0).displayName()).doesNotContain("<script>");
	}

	/**
	 * 절대값이 아니라 글 하나와 글 셋의 질의 수가 같은지만 본다 — {@code StoryReactionResponseIntegrationTest}
	 * 와 같은 방식이다. 글마다 참여자를 따로 읽으면 20건 피드가 20번 더 왕복한다.
	 */
	@Test
	@DisplayName("🔴 글이 하나든 셋이든 질의 수가 같다 — 글마다 공동 작성자를 읽지 않는다")
	void queryCountDoesNotGrowWithPageSize() {
		UUID second = StoryFixture.insertStory(this.jdbc, this.author, "두 번째", "PUBLIC",
				Instant.now().minus(Duration.ofHours(2)));
		UUID third = StoryFixture.insertStory(this.jdbc, this.author, "세 번째", "PUBLIC",
				Instant.now().minus(Duration.ofHours(3)));
		UUID friendA = StoryFixture.insertUser(this.jdbc, "친구A");
		UUID friendB = StoryFixture.insertUser(this.jdbc, "친구B");
		Instant joined = Instant.now().minus(Duration.ofMinutes(10));
		insertCoauthor(this.storyId, friendA, joined);
		insertCoauthor(second, friendA, joined);
		insertCoauthor(second, friendB, joined.plusSeconds(1));

		Story one = this.stories.findVisibleById(this.storyId).orElseThrow();
		List<Story> three = List.of(one,
				this.stories.findVisibleById(second).orElseThrow(),
				this.stories.findVisibleById(third).orElseThrow());

		long forOne = statementsDuring(() -> this.assembler.many(List.of(one), null, Instant.now()));
		long forThree = statementsDuring(() -> this.assembler.many(three, null, Instant.now()));

		assertThat(forThree)
				.as("글이 셋일 때 질의가 더 나갔다 — 글마다 공동 작성자를 읽고 있다")
				.isEqualTo(forOne);

		// 질의 수만 맞고 값이 비거나 섞이면 의미가 없다.
		List<StoryResponse> responses = this.assembler.many(three, null, Instant.now());
		assertThat(responses.get(0).coauthors()).extracting(StoryResponse.Coauthor::displayName)
				.containsExactly("친구A");
		assertThat(responses.get(1).coauthors()).extracting(StoryResponse.Coauthor::displayName)
				.containsExactly("친구A", "친구B");
		assertThat(responses.get(2).coauthors()).isEmpty();
	}

	@Test
	@DisplayName("🔴 목록과 상세가 같은 공동 작성자를 낸다")
	void listAndDetailAgree() {
		UUID friend = StoryFixture.insertUser(this.jdbc, "친구");
		insertCoauthor(this.storyId, friend, Instant.now().minus(Duration.ofMinutes(5)));
		Story story = this.stories.findVisibleById(this.storyId).orElseThrow();
		Instant now = Instant.now();

		assertThat(this.assembler.many(List.of(story), null, now).get(0).coauthors())
				.isEqualTo(this.assembler.one(story, null, now).coauthors());
	}

	private void insertCoauthor(UUID story, UUID user, Instant joinedAt) {
		this.jdbc.update("INSERT INTO story_coauthor (story_id, user_id, invited_by, joined_at) VALUES (?, ?, ?, ?)",
				story, user, this.author, joinedAt.atOffset(ZoneOffset.UTC));
	}

	private StoryResponse assemble(UUID story, UUID viewer) {
		return this.assembler.one(this.stories.findVisibleById(story).orElseThrow(), viewer, Instant.now());
	}

	/** {@code StoryReactionResponseIntegrationTest} 와 같다 — 통계는 속성이 아니라 런타임에만 켠다. */
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
