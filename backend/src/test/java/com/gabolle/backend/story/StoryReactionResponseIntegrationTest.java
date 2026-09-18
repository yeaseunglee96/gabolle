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

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.story.application.StoryReactionService;
import com.gabolle.backend.story.application.StoryResponseAssembler;
import com.gabolle.backend.story.domain.ReactionType;
import com.gabolle.backend.story.domain.Story;
import com.gabolle.backend.story.presentation.dto.StoryResponse;
import com.gabolle.backend.story.repository.StoryRepository;
import com.gabolle.testslice.StorySliceApplication;

/**
 * 반응 수와 「내가 눌러 둔 것」이 응답까지 닿는가 — S15P21E201-1174.
 *
 * <h2>🔴 목록과 상세 <b>둘 다</b>가 이 검사의 주제다</h2>
 *
 * 한쪽에만 실으면 <b>목록에서 하트를 못 그리거나, 상세를 갔다 오며 숫자가 어긋난다.</b> 이
 * 저장소는 같은 병을 네 번 겪었고(-1120 · -1194 · -1205 · -1158) 그래서 장소 쪽에는
 * {@code PlaceListDetailFieldGapTest} 가 생겼다. 기록 쪽에는 아직 그 검사가 없으므로 여기서
 * 두 경로를 <b>같은 자료로</b> 불러 값이 같은지 본다.
 *
 * <p>{@code StoryResponseAssembler.one} 이 {@code many} 를 그대로 부르므로 지금은 갈라질 수가
 * 없다 — 그 사실 자체를 검사로 묶어 둔다. 나중에 상세만 따로 만드는 사람이 생기면 여기가 빨개진다.
 */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class StoryReactionResponseIntegrationTest {

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private StoryReactionService reactions;

	@Autowired
	private StoryResponseAssembler assembler;

	@Autowired
	private StoryRepository stories;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	private UUID author;

	private UUID liker;

	private UUID disliker;

	private UUID storyId;

	@BeforeEach
	void setUp() {
		this.author = StoryFixture.insertUser(this.jdbc, "글쓴이");
		this.liker = StoryFixture.insertUser(this.jdbc, "좋아요누른사람");
		this.disliker = StoryFixture.insertUser(this.jdbc, "싫어요누른사람");
		this.storyId = StoryFixture.insertStory(this.jdbc, this.author, "부산 감천문화마을", "PUBLIC",
				Instant.now().minus(Duration.ofHours(1)));
	}

	@Test
	@DisplayName("🔴 좋아요·싫어요 수와 내 반응이 응답에 실린다")
	void countsAndMyReactionReachTheResponse() {
		this.reactions.react(this.liker, this.storyId, ReactionType.LIKE);
		this.reactions.react(this.disliker, this.storyId, ReactionType.DISLIKE);

		StoryResponse seenByLiker = assemble(this.liker);

		assertThat(seenByLiker.likeCount()).isEqualTo(1);
		assertThat(seenByLiker.dislikeCount()).isEqualTo(1);
		assertThat(seenByLiker.myReaction()).as("내가 누른 것을 화면이 알아야 토글이 자기 상태를 그린다")
				.isEqualTo("LIKE");
	}

	/**
	 * 🔴 <b>불리언이었으면 이 검사를 쓸 수 없다.</b> 싫어요를 누른 사람과 아무것도 안 누른
	 * 사람이 똑같이 {@code false} 로 보이기 때문이다. 세 값으로 낸 이유가 이것이다.
	 */
	@Test
	@DisplayName("🔴 싫어요를 누른 사람과 아무것도 안 누른 사람이 구분된다")
	void dislikerAndBystanderAreDistinguishable() {
		this.reactions.react(this.disliker, this.storyId, ReactionType.DISLIKE);
		UUID bystander = StoryFixture.insertUser(this.jdbc, "구경만한사람");

		assertThat(assemble(this.disliker).myReaction()).isEqualTo("DISLIKE");
		assertThat(assemble(bystander).myReaction()).as("안 누른 사람은 null 이다").isNull();
	}

	/** 🔴 취소해도 반응 행은 남는다(-1173 후속). 행을 그냥 세면 취소한 사람까지 들어간다. */
	@Test
	@DisplayName("🔴 취소한 사람은 수에서 빠지고 내 반응도 null 이 된다")
	void cancelledReactionsAreNotCounted() {
		this.reactions.react(this.liker, this.storyId, ReactionType.LIKE);
		this.reactions.remove(this.liker, this.storyId);

		// 행은 남아 있어야 한다 — 그 사실이 이 검사의 전제다.
		assertThat(this.jdbc.queryForObject(
				"SELECT count(*) FROM story_reaction WHERE story_id = ? AND user_id = ?", Integer.class,
				this.storyId, this.liker)).isEqualTo(1);

		StoryResponse response = assemble(this.liker);

		assertThat(response.likeCount()).as("취소한 사람이 세지면 화면의 숫자가 실제보다 크다").isZero();
		assertThat(response.myReaction()).as("취소한 것은 「안 누른 것」이지 「취소를 누른 것」이 아니다").isNull();
	}

	@Test
	@DisplayName("🔴 아무도 안 누른 글은 0 이다 — null 이 아니다")
	void untouchedStoryReportsZeroNotNull() {
		StoryResponse response = assemble(this.liker);

		assertThat(response.likeCount()).isZero();
		assertThat(response.dislikeCount()).isZero();
		assertThat(response.myReaction()).isNull();
	}

	@Test
	@DisplayName("🔴 비회원(viewer 없음)도 수는 보고, 내 반응만 null 이다")
	void anonymousSeesCountsButNoOwnReaction() {
		this.reactions.react(this.liker, this.storyId, ReactionType.LIKE);

		StoryResponse response = assemble(null);

		assertThat(response.likeCount()).isEqualTo(1);
		assertThat(response.myReaction()).isNull();
	}

	/**
	 * 🔴 목록과 상세가 <b>같은 값</b>을 내는가 — 이 티켓이 막으려는 병이 바로 이것이다.
	 *
	 * <p>상세에만 실으면 목록에서 하트를 못 그리고, 목록에만 실으면 상세를 갔다 오며 숫자가
	 * 어긋난다. 이 저장소가 같은 자리를 네 번 겪었다.
	 */
	@Test
	@DisplayName("🔴 목록과 상세가 같은 수를 낸다")
	void listAndDetailAgree() {
		this.reactions.react(this.liker, this.storyId, ReactionType.LIKE);
		this.reactions.react(this.disliker, this.storyId, ReactionType.DISLIKE);
		Story story = this.stories.findVisibleById(this.storyId).orElseThrow();
		Instant now = Instant.now();

		StoryResponse detail = this.assembler.one(story, this.liker, now);
		StoryResponse inList = this.assembler.many(List.of(story), this.liker, now).get(0);

		assertThat(inList.likeCount()).isEqualTo(detail.likeCount());
		assertThat(inList.dislikeCount()).isEqualTo(detail.dislikeCount());
		assertThat(inList.myReaction()).isEqualTo(detail.myReaction());
	}

	/**
	 * 🔴 <b>글마다 세면 안 된다.</b> 한 쪽이 최대 50건이라 글마다 세면 50번의 왕복이 된다.
	 *
	 * <h2>재는 방법</h2>
	 *
	 * 같은 조립을 <b>글 하나</b>와 <b>글 셋</b>으로 각각 돌리고, 그때 나간 JDBC 문장 수를
	 * 견준다. 두 수가 <b>같아야</b> 한다 — 글 수에 따라 늘면 그게 곧 N+1 이다.
	 *
	 * <p>🔴 절대값을 재지 않는 이유: 사진·작성자·장소 질의가 함께 나가고 그 수는 이 티켓과
	 * 무관하게 바뀔 수 있다. <b>늘어나는가</b>만 보면 다른 사람의 변경에 이 검사가 엉뚱하게
	 * 빨개지지 않는다.
	 *
	 * <p>{@code pg_stat_user_tables} 로도 재 봤는데 그 통계는 <b>비동기로 갱신</b>되어 방금
	 * 나간 질의가 안 잡힌다(0 이 나왔다). Hibernate 통계는 그 자리에서 오른다.
	 */
	@Test
	@DisplayName("🔴 글이 하나든 셋이든 질의 수가 같다 — 글마다 세지 않는다")
	void queryCountDoesNotGrowWithPageSize() {
		UUID second = StoryFixture.insertStory(this.jdbc, this.author, "두 번째", "PUBLIC",
				Instant.now().minus(Duration.ofHours(2)));
		UUID third = StoryFixture.insertStory(this.jdbc, this.author, "세 번째", "PUBLIC",
				Instant.now().minus(Duration.ofHours(3)));
		this.reactions.react(this.liker, this.storyId, ReactionType.LIKE);
		this.reactions.react(this.liker, second, ReactionType.LIKE);

		Story one = this.stories.findVisibleById(this.storyId).orElseThrow();
		List<Story> three = List.of(one,
				this.stories.findVisibleById(second).orElseThrow(),
				this.stories.findVisibleById(third).orElseThrow());

		long forOne = statementsDuring(() -> this.assembler.many(List.of(one), this.liker, Instant.now()));
		long forThree = statementsDuring(() -> this.assembler.many(three, this.liker, Instant.now()));

		assertThat(forThree)
				.as("글이 셋일 때 질의가 더 나갔다 — 글마다 세고 있다. 50건 피드가 50번 왕복한다")
				.isEqualTo(forOne);

		// 값이 실제로 붙는지도 같이 본다 — 질의 수만 맞고 값이 비면 의미가 없다.
		List<StoryResponse> responses = this.assembler.many(three, this.liker, Instant.now());
		assertThat(responses.get(0).likeCount()).isEqualTo(1);
		assertThat(responses.get(2).likeCount()).isZero();
	}

	/**
	 * 이 일을 하는 동안 나간 JDBC 문장 수.
	 *
	 * <h2>🔴 {@code hibernate.generate_statistics} 를 속성으로 켜지 않는 이유</h2>
	 *
	 * 처음에 {@code @SpringBootTest(properties = ...)} 로 켰다가 <b>CI 가 heap 부족으로
	 * 죽었다.</b> 스프링은 속성이 한 글자라도 다르면 <b>별도 컨텍스트</b>를 띄우고 그것을 JVM
	 * 이 끝날 때까지 캐시한다. 이 검사 하나 때문에 애플리케이션 컨텍스트가 통째로 하나 더
	 * 살아 있게 되고, 그 무게가 한계를 넘겼다. 로컬에서 일부만 돌릴 때는 안 드러난다.
	 *
	 * <p>그래서 켜는 자리를 <b>런타임</b>으로 옮겼다. 이 클래스의 {@code properties} 는 이제
	 * 다른 기록 검사들과 <b>글자까지 같고</b>, 같은 컨텍스트를 함께 쓴다. 통계는 그 컨텍스트의
	 * {@code SessionFactory} 에서 켜고 끄기만 한다.
	 *
	 * <p>({@code TestDatabase} 가 S15P21E201-662 에서 컨텍스트마다 연결 풀이 하나씩 생기는
	 * 것을 두고 같은 종류의 경고를 적어 뒀다 — 컨텍스트를 늘리는 것은 공짜가 아니다.)
	 */
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

	// ── 거들기 ────────────────────────────────────────────────────────

	private StoryResponse assemble(UUID viewer) {
		Story story = this.stories.findVisibleById(this.storyId).orElseThrow();
		return this.assembler.one(story, viewer, Instant.now());
	}

}
