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

/**
 * S15P21E201-1204 — 글을 열면 조회수가 <b>규칙대로</b> 오르는가.
 *
 * <h2>재는 것 — 완료 기준 그대로</h2>
 *
 * <ul>
 *   <li>같은 사람이 같은 글을 하루에 여러 번 열어도 <b>1만</b> 는다</li>
 *   <li>날이 바뀌면 <b>또 1</b> 늘어난다</li>
 *   <li><b>작성자 본인</b>이 열면 안 는다</li>
 *   <li><b>익명 세션</b>으로 열면 늘고, 같은 세션이 또 열면 안 는다</li>
 *   <li>회원도 익명 세션도 아니면 <b>안 는다</b></li>
 * </ul>
 *
 * <h2>🔴 날이 바뀌는 것을 시계로 재지 않는다</h2>
 *
 * 시계를 하루 앞으로 돌리는 대신 <b>이미 남은 낱개의 날짜를 어제로 되돌린다.</b> 결과가 같고
 * (오늘 것이 없는 상태가 된다) 시계 빈을 바꾸지 않아도 된다 — 시계를 바꾸면 그 컨텍스트가
 * 따로 캐시돼 앱이 한 번 더 뜬다.
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
class StoryViewCountIntegrationTest {

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

	private UUID reader;

	private UUID storyId;

	private UUID anonymousSessionId;

	@BeforeEach
	void setUp() {
		this.author = StoryFixture.insertUser(this.jdbc, "작성자");
		this.reader = StoryFixture.insertUser(this.jdbc, "읽는 사람");
		this.storyId = StoryFixture.insertStory(this.jdbc, this.author, "조회수를 세는 글", "PUBLIC",
				Instant.now().minusSeconds(3600));
		this.anonymousSessionId = insertAnonymousSession();
	}

	@AfterEach
	void tearDown() {
		// 🔴 표를 비우지 않는다. 내가 만든 것만 지운다 — 같은 DB 를 여러 검사가 함께 쓴다.
		this.jdbc.update("DELETE FROM story_view WHERE story_id = ?", this.storyId);
		this.jdbc.update("DELETE FROM story WHERE story_id = ?", this.storyId);
		this.jdbc.update("DELETE FROM anonymous_session WHERE session_id = ?", this.anonymousSessionId);
		this.jdbc.update("DELETE FROM app_user WHERE user_id IN (?, ?)", this.author, this.reader);
	}

	@Test
	@DisplayName("🔴 같은 사람이 하루에 여러 번 열어도 1만 는다")
	void sameMemberSameDayCountsOnce() {
		this.storyService.get(this.storyId, this.reader, null);
		this.storyService.get(this.storyId, this.reader, null);
		this.storyService.get(this.storyId, this.reader, null);

		assertThat(viewCount()).as("한 사람이 세 번 열었는데 수가 1이 아니다").isEqualTo(1);
		assertThat(viewRows()).as("낱개가 하루에 하나가 아니다").isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 날이 바뀌면 또 1 늘어난다 — 「하루 한 번」이지 「한 번뿐」이 아니다")
	void nextDayCountsAgain() {
		this.storyService.get(this.storyId, this.reader, null);
		assertThat(viewCount()).isEqualTo(1);

		// 시계를 돌리는 대신 남은 낱개를 어제 것으로 만든다 — 오늘 것이 없는 상태가 된다.
		this.jdbc.update("UPDATE story_view SET viewed_on = viewed_on - 1 WHERE story_id = ?", this.storyId);

		this.storyService.get(this.storyId, this.reader, null);

		assertThat(viewCount()).as("날이 바뀌었는데 수가 안 늘었다").isEqualTo(2);
		assertThat(viewRows()).as("낱개가 날마다 하나씩이 아니다").isEqualTo(2);
	}

	@Test
	@DisplayName("🔴 작성자 본인이 열면 안 는다 — 그 수가 「남이 읽었다」를 뜻해야 한다")
	void authorDoesNotCount() {
		this.storyService.get(this.storyId, this.author, null);
		this.storyService.get(this.storyId, this.author, null);

		assertThat(viewCount()).as("작성자가 자기 글을 열었는데 수가 올랐다").isZero();
		assertThat(viewRows()).as("작성자 조회가 낱개로 남았다").isZero();
	}

	@Test
	@DisplayName("🔴 익명 세션으로 열면 는다. 같은 세션이 또 열면 안 는다")
	void anonymousSessionCountsOnce() {
		this.storyService.get(this.storyId, null, this.anonymousSessionId);
		this.storyService.get(this.storyId, null, this.anonymousSessionId);

		assertThat(viewCount()).as("비회원 조회가 안 세어졌다 — 세는 것이 정해진 규칙이다").isEqualTo(1);
		assertThat(this.jdbc.queryForObject(
				"SELECT count(*) FROM story_view WHERE story_id = ? AND anonymous_session_id = ?",
				Long.class, this.storyId, this.anonymousSessionId))
				.as("같은 익명 세션이 하루에 두 줄 남겼다").isEqualTo(1L);
	}

	@Test
	@DisplayName("🔴 회원도 익명 세션도 아니면 안 는다 — 하루 한 번을 지킬 방법이 없다")
	void unidentifiedViewerDoesNotCount() {
		this.storyService.get(this.storyId, null, null);
		this.storyService.get(this.storyId, null, null);

		assertThat(viewCount()).as("식별할 수 없는 조회가 세어졌다 — 새로고침마다 오른다").isZero();
		assertThat(viewRows()).isZero();
	}

	@Test
	@DisplayName("응답이 그 수를 그대로 싣는다 — 같은 트랜잭션에서 올리므로 바로 보인다")
	void responseCarriesTheCount() {
		assertThat(this.storyService.get(this.storyId, this.reader, null).viewCount())
				.as("처음 연 사람에게 1이 안 보인다").isEqualTo(1);
		assertThat(this.storyService.get(this.storyId, this.reader, null).linkCopyCount())
				.as("링크 복사 수는 아직 세는 코드가 없어 0이어야 한다").isZero();
	}

	private int viewCount() {
		Integer n = this.jdbc.queryForObject("SELECT view_count FROM story WHERE story_id = ?",
				Integer.class, this.storyId);
		return n == null ? 0 : n;
	}

	private long viewRows() {
		Long n = this.jdbc.queryForObject("SELECT count(*) FROM story_view WHERE story_id = ?",
				Long.class, this.storyId);
		return n == null ? 0 : n;
	}

	private UUID insertAnonymousSession() {
		UUID sessionId = UUID.randomUUID();
		this.jdbc.update("INSERT INTO anonymous_session (session_id, token_hash, created_at, last_seen_at) "
				+ "VALUES (?, ?, now(), now())", sessionId, "hash-" + UUID.randomUUID());
		return sessionId;
	}

	/** 오늘이 KST 기준 언제인지 — 낱개의 날짜가 그 값이어야 한다. */
	private LocalDate todayInCountingZone() {
		return LocalDate.now(COUNTING_ZONE);
	}

	@Test
	@DisplayName("낱개의 날짜가 KST 기준 오늘이다 — 서버 시간대가 아니다")
	void viewedOnUsesCountingZone() {
		this.storyService.get(this.storyId, this.reader, null);

		assertThat(this.jdbc.queryForObject("SELECT viewed_on FROM story_view WHERE story_id = ?",
				LocalDate.class, this.storyId))
				.as("낱개 날짜가 KST 기준 오늘이 아니다").isEqualTo(todayInCountingZone());
	}
}
