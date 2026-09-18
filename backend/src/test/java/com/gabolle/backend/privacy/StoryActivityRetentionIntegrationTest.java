package com.gabolle.backend.privacy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.privacy.application.PrivacyCleanupProperties;
import com.gabolle.backend.privacy.application.PrivacyCleanupScheduler;
import com.gabolle.backend.privacy.application.PrivacyCleanupService;
import com.gabolle.backend.privacy.domain.PrivacyCleanupResult;
import com.gabolle.backend.privacy.domain.PrivacyCleanupRun;
import com.gabolle.backend.privacy.domain.PrivacyCleanupStatus;
import com.gabolle.backend.privacy.support.PrivacyPostgresIntegrationTest;
import com.gabolle.backend.story.StoryFixture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S15P21E201-1216 — 90일 지난 조회·복사 낱개를 개인정보 자동 정리 배치가 치우는가.
 *
 * <h2>재는 것 — 완료 기준 그대로</h2>
 *
 * <ul>
 *   <li>보관 기준선 <b>바깥</b>(91일 전) 낱개는 <b>지워진다</b></li>
 *   <li>기준선 <b>바로 안쪽</b>(딱 90일 전·89일 전·오늘) 낱개는 <b>남는다</b></li>
 *   <li>🔴 <b>누적 칸은 한 톨도 안 내려간다</b> — {@code story.view_count} · {@code link_copy_count}</li>
 *   <li>실행 기록 행({@code privacy_cleanup_run})에 두 카테고리 건수가 남는다</li>
 *   <li>조회 낱개와 복사 낱개가 <b>둘 다</b> 치워진다 — 한쪽만 하고 끝내는 실수를 잡는다</li>
 * </ul>
 *
 * <h2>🔴 하루의 경계가 한국 시각이라 날짜를 한국 시각으로 만든다</h2>
 *
 * 낱개의 날짜 칸({@code viewed_on} · {@code copied_on})은 <b>DATE</b> 이고, 그 값을 채우는
 * {@code StoryService} 가 한국 시각으로 계산해서 넣는다. 그래서 이 검사도 기준일을 한국 시각의
 * 「오늘」에서 뺀다. 서버 시각(UTC)으로 세면 한국 시각 오전 9시 전에 기준선이 하루 어긋난다.
 *
 * <h2>🔴 이 검사는 남의 행도 지운다 — 그래서 「내 것」만 센다</h2>
 *
 * 배치는 표 전체를 훑으므로, 같은 DB 를 쓰는 다른 검사가 남긴 오래된 낱개도 함께 지워진다.
 * 그래서 삭제 건수는 <b>{@code isGreaterThanOrEqualTo}</b> 로 보고, 정확한 판정은 <b>내가 심은
 * 행이 있는가/없는가</b>로 한다 — 기존 {@code PrivacyCleanupIntegrationTest} 와 같은 규칙이다.
 */
class StoryActivityRetentionIntegrationTest extends PrivacyPostgresIntegrationTest {

	/** 🔴 {@code PrivacyCleanupService.STORY_ACTIVITY_ZONE} 과 같아야 한다. */
	private static final ZoneId STORY_ACTIVITY_ZONE = ZoneId.of("Asia/Seoul");

	@Autowired
	private PrivacyCleanupService cleanupService;

	@Autowired
	private PrivacyCleanupScheduler cleanupScheduler;

	@Autowired
	private PrivacyCleanupProperties properties;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID author;

	private UUID reader;

	private UUID storyId;

	private UUID anonymousSessionId;

	private LocalDate today;

	@BeforeEach
	void setUp() {
		this.author = StoryFixture.insertUser(this.jdbc, "작성자");
		this.reader = StoryFixture.insertUser(this.jdbc, "읽는 사람");
		this.storyId = StoryFixture.insertStory(this.jdbc, this.author, "낱개를 치우는 글", "PUBLIC",
				Instant.now().minusSeconds(3600));
		this.anonymousSessionId = insertAnonymousSession();
		this.today = LocalDate.now(STORY_ACTIVITY_ZONE);
	}

	@AfterEach
	void tearDown() {
		// 🔴 표를 비우지 않는다. 이 검사가 만든 것만 지운다 — 같은 DB 를 여러 검사가 함께 쓴다.
		this.jdbc.update("DELETE FROM privacy_cleanup_run");
		this.jdbc.update("DELETE FROM story_view WHERE story_id = ?", this.storyId);
		this.jdbc.update("DELETE FROM story_link_copy WHERE story_id = ?", this.storyId);
		this.jdbc.update("DELETE FROM story WHERE story_id = ?", this.storyId);
		this.jdbc.update("DELETE FROM anonymous_session WHERE session_id = ?", this.anonymousSessionId);
		this.jdbc.update("DELETE FROM app_user WHERE user_id IN (?, ?)", this.author, this.reader);
	}

	@Test
	@DisplayName("완료 기준 — 보관 기준선 바깥의 조회 낱개는 지워지고 바로 안쪽은 남는다")
	void deletesViewsPastRetentionAndKeepsTheRest() {
		int retention = this.properties.getStoryActivityRetentionDays();
		UUID tooOld = insertMemberView(this.today.minusDays(retention + 1L));
		UUID onTheLine = insertAnonymousView(this.today.minusDays(retention));
		UUID recent = insertAnonymousView(this.today.minusDays(1L));

		this.cleanupService.cleanup();

		assertThat(viewExists(tooOld)).as("보관 기간이 지난 낱개가 안 지워졌다 — 이 표는 끝없이 자란다").isFalse();
		assertThat(viewExists(onTheLine)).as("딱 기준선인 낱개가 지워졌다 — 기준선보다 앞선 것만 지운다").isTrue();
		assertThat(viewExists(recent)).as("어제 낱개가 지워졌다 — 「하루 한 번」을 못 지키게 된다").isTrue();
	}

	@Test
	@DisplayName("완료 기준 — 링크 복사 낱개도 같이 치워진다. 조회만 하고 끝내지 않는다")
	void deletesLinkCopiesToo() {
		int retention = this.properties.getStoryActivityRetentionDays();
		UUID tooOld = insertMemberLinkCopy(this.today.minusDays(retention + 1L));
		UUID recent = insertAnonymousLinkCopy(this.today.minusDays(1L));

		this.cleanupService.cleanup();

		assertThat(linkCopyExists(tooOld)).as("복사 낱개는 안 치워졌다 — 두 표가 같은 규칙이다").isFalse();
		assertThat(linkCopyExists(recent)).as("어제 복사 낱개가 지워졌다").isTrue();
	}

	@Test
	@DisplayName("🔴 완료 기준 — 낱개를 지워도 누적 칸은 한 톨도 안 내려간다. 어제까지의 조회가 사라지면 안 된다")
	void neverTouchesTheRunningTotals() {
		this.jdbc.update("UPDATE story SET view_count = 17, link_copy_count = 5 WHERE story_id = ?", this.storyId);
		int retention = this.properties.getStoryActivityRetentionDays();
		insertMemberView(this.today.minusDays(retention + 1L));
		insertMemberLinkCopy(this.today.minusDays(retention + 1L));

		this.cleanupService.cleanup();

		assertThat(storyColumn("view_count")).as("낱개를 지우면서 누적 조회수를 같이 내렸다").isEqualTo(17);
		assertThat(storyColumn("link_copy_count")).as("낱개를 지우면서 누적 인용수를 같이 내렸다").isEqualTo(5);
	}

	@Test
	@DisplayName("완료 기준 — 지운 건수가 결과와 실행 기록 행에 함께 남는다")
	void recordsTheCountsInTheRunRow() {
		int retention = this.properties.getStoryActivityRetentionDays();
		insertMemberView(this.today.minusDays(retention + 1L));
		insertMemberLinkCopy(this.today.minusDays(retention + 1L));

		PrivacyCleanupResult result = this.cleanupService.cleanup();
		assertThat(result.storyViewsDeleted()).as("결과에 조회 낱개 건수가 안 담겼다").isGreaterThanOrEqualTo(1);
		assertThat(result.storyLinkCopiesDeleted()).as("결과에 복사 낱개 건수가 안 담겼다").isGreaterThanOrEqualTo(1);

		// 🔴 지운 뒤에 한 번 더 돌린다 — 앞 실행이 이미 치웠으므로 이번 실행의 건수는 0 이어야
		//    하고, 그래야 기록 행의 숫자가 「이번에 지운 것」인지 확인된다.
		insertMemberView(this.today.minusDays(retention + 1L));
		insertMemberLinkCopy(this.today.minusDays(retention + 1L));

		PrivacyCleanupRun run = this.cleanupScheduler.run();

		assertThat(run.getStatus()).isEqualTo(PrivacyCleanupStatus.SUCCEEDED);
		assertThat(run.getStoryViewsDeleted()).as("실행 기록에 조회 낱개 건수가 안 남았다").isGreaterThanOrEqualTo(1);
		assertThat(run.getStoryLinkCopiesDeleted()).as("실행 기록에 복사 낱개 건수가 안 남았다").isGreaterThanOrEqualTo(1);

		// 표에도 실제로 저장됐는가 — 엔티티만 채우고 DB 에 안 내려가는 실수를 잡는다.
		assertThat(this.jdbc.queryForObject(
				"SELECT story_views_deleted FROM privacy_cleanup_run WHERE run_id = ?", Integer.class,
				run.getRunId())).as("기록 행이 엔티티에만 있고 표에는 안 내려갔다").isGreaterThanOrEqualTo(1);
	}

	// ---- 시드 ----

	private UUID insertMemberView(LocalDate viewedOn) {
		UUID id = UUID.randomUUID();
		this.jdbc.update("INSERT INTO story_view (story_view_id, story_id, user_id, viewed_on, created_at) "
				+ "VALUES (?, ?, ?, ?, now())", id, this.storyId, this.reader, viewedOn);
		return id;
	}

	private UUID insertAnonymousView(LocalDate viewedOn) {
		UUID id = UUID.randomUUID();
		this.jdbc.update(
				"INSERT INTO story_view (story_view_id, story_id, anonymous_session_id, viewed_on, created_at) "
						+ "VALUES (?, ?, ?, ?, now())",
				id, this.storyId, this.anonymousSessionId, viewedOn);
		return id;
	}

	private UUID insertMemberLinkCopy(LocalDate copiedOn) {
		UUID id = UUID.randomUUID();
		this.jdbc.update("INSERT INTO story_link_copy (story_link_copy_id, story_id, user_id, copied_on, created_at) "
				+ "VALUES (?, ?, ?, ?, now())", id, this.storyId, this.reader, copiedOn);
		return id;
	}

	private UUID insertAnonymousLinkCopy(LocalDate copiedOn) {
		UUID id = UUID.randomUUID();
		this.jdbc.update("INSERT INTO story_link_copy "
				+ "(story_link_copy_id, story_id, anonymous_session_id, copied_on, created_at) "
				+ "VALUES (?, ?, ?, ?, now())", id, this.storyId, this.anonymousSessionId, copiedOn);
		return id;
	}

	private UUID insertAnonymousSession() {
		UUID sessionId = UUID.randomUUID();
		this.jdbc.update("INSERT INTO anonymous_session (session_id, token_hash, created_at, last_seen_at) "
				+ "VALUES (?, ?, now(), now())", sessionId, "hash-" + UUID.randomUUID());
		return sessionId;
	}

	// ---- 확인 ----

	private boolean viewExists(UUID storyViewId) {
		Long n = this.jdbc.queryForObject("SELECT count(*) FROM story_view WHERE story_view_id = ?", Long.class,
				storyViewId);
		return n != null && n > 0;
	}

	private boolean linkCopyExists(UUID storyLinkCopyId) {
		Long n = this.jdbc.queryForObject("SELECT count(*) FROM story_link_copy WHERE story_link_copy_id = ?",
				Long.class, storyLinkCopyId);
		return n != null && n > 0;
	}

	private int storyColumn(String column) {
		Integer n = this.jdbc.queryForObject("SELECT " + column + " FROM story WHERE story_id = ?", Integer.class,
				this.storyId);
		return n == null ? 0 : n;
	}
}
