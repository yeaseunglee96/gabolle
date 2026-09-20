package com.gabolle.backend.privacy.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.privacy.domain.PrivacyCleanupResult;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 개인정보 자동 정리 배치가 실제로 지우는 곳. 지우는 것은 넷이다.
 *
 * <ol>
 * <li>만료된 지 {@code sessionGraceDays} 가 지난 {@code auth_session}. 그 세션의 리프레시
 * 토큰은 DB 의 {@code ON DELETE CASCADE} 가 함께 지운다.</li>
 * <li>자기 자신이 만료된 지 유예 기간이 지난 {@code auth_refresh_token} — 세션은 살아 있는데
 * 로테이션으로 못 쓰게 된 옛 토큰이다.</li>
 * <li>발행이 끝난({@code publishedAt IS NOT NULL}) 지 {@code eventRetentionDays} 가 지난
 * {@code event_outbox} 행.</li>
 * <li>{@code storyActivityRetentionDays} 가 지난 조회 낱개({@code story_view})와 링크 복사
 * 낱개({@code story_link_copy}).</li>
 * </ol>
 *
 * <p>{@code publishedAt IS NULL} 인 이벤트는 지우지 않는다. {@code publishStatus} 가 FAILED 여도
 * {@code publishedAt} 이 비어 있으면 릴레이가 다시 보내야 하는 이벤트다.
 *
 * <p>낱개를 지우면서 누적 칸({@code story.view_count}·{@code story.link_copy_count})은 손대지
 * 않는다. 낱개는 「사람 × 글 × 하루 한 번」을 지키려고 두는 것이고 누적은 누적이다.
 *
 * <p>삭제는 전부 JPQL 벌크 삭제다. 대상이 몇만 건이 될 수 있어 엔티티를 하나씩 읽어 지우면
 * 메모리와 시간이 행 수에 비례해 늘어난다.
 */
@Service
@Profile({ "db", "dev" })
public class PrivacyCleanupService {

	private static final Logger log = LoggerFactory.getLogger(PrivacyCleanupService.class);

	/**
	 * 조회·복사 낱개의 날짜 칸이 재고 있는 시간대.
	 *
	 * <p>{@code story_view.viewed_on} 과 {@code story_link_copy.copied_on} 은 DATE 이고 그
	 * 값을 채우는 {@code StoryService} 가 한국 시각으로 계산해 넣는다. 서버 시간대(UTC)로
	 * 기준선을 잡으면 한국 시각 오전 9시 전에는 기준선이 하루 어긋난다.
	 *
	 * <p>{@code StoryService.COUNTING_ZONE} 과 반드시 같아야 한다. 이 저장소는 시간대를
	 * 클래스마다 따로 선언하므로 한쪽을 바꾸면 다른 쪽도 바꾼다.
	 */
	private static final ZoneId STORY_ACTIVITY_ZONE = ZoneId.of("Asia/Seoul");

	@PersistenceContext
	private EntityManager entityManager;

	private final PrivacyCleanupProperties properties;

	private final Clock clock;

	public PrivacyCleanupService(PrivacyCleanupProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * 세 카테고리를 순서대로 지우고 건수를 합쳐 돌려준다.
	 *
	 * <p> 한 트랜잭션이다. {@link com.gabolle.backend.auth.service.AccountDeletionService} 와
	 * 같은 이유 — 세 삭제 중 하나가 실패했는데 앞의 것만 반영되면, 다음날 배치가 그 반쯤 지운
	 * 상태를 기준으로 또 계산해야 하는 애매한 상태가 남는다.
	 */
	@Transactional
	public PrivacyCleanupResult cleanup() {
		Instant now = this.clock.instant();

		Instant sessionCutoff = now.minus(Duration.ofDays(this.properties.getSessionGraceDays()));
		Instant eventCutoff = now.minus(Duration.ofDays(this.properties.getEventRetentionDays()));

		// 리프레시 토큰을 먼저 지운다 — 세션 삭제가 발생시키는 CASCADE 와 겹치는 행이 있어도
		// 순서와 무관하게 최종 결과는 같지만, 토큰을 먼저 지우면 이어지는 세션 삭제가 지울 자식
		// 행이 줄어 있다(이미 지워졌으므로).
		int refreshTokensDeleted = this.entityManager
				.createQuery("DELETE FROM AuthRefreshToken t WHERE t.expiresAt < :cutoff")
				.setParameter("cutoff", sessionCutoff)
				.executeUpdate();

		int sessionsDeleted = this.entityManager
				.createQuery("DELETE FROM AuthSession s WHERE s.expiresAt < :cutoff")
				.setParameter("cutoff", sessionCutoff)
				.executeUpdate();

		int eventsDeleted = this.entityManager
				.createQuery(
						"DELETE FROM EventOutbox e WHERE e.publishedAt IS NOT NULL AND e.occurredAt < :cutoff")
				.setParameter("cutoff", eventCutoff.atOffset(ZoneOffset.UTC))
				.executeUpdate();

		// 낱개의 날짜 칸은 DATE 이고 한국 시각으로 채워지므로, Instant 에서 며칠을 빼지 않고
		// 한국 시각의 오늘에서 날짜로 뺀다. 이 기준선보다 앞선 날짜만 지운다 —
		// 딱 보관 일수만큼 된 것은 남는다.
		LocalDate storyActivityCutoff = LocalDate.ofInstant(now, STORY_ACTIVITY_ZONE)
				.minusDays(this.properties.getStoryActivityRetentionDays());

		// 누적 칸(story.view_count · story.link_copy_count)은 여기서 손대지 않는다.
		// 지우는 것은 낱개뿐이다 — 이 클래스 머리말에 그 이유가 있다.
		int storyViewsDeleted = this.entityManager
				.createQuery("DELETE FROM StoryView v WHERE v.viewedOn < :cutoff")
				.setParameter("cutoff", storyActivityCutoff)
				.executeUpdate();

		int storyLinkCopiesDeleted = this.entityManager
				.createQuery("DELETE FROM StoryLinkCopy c WHERE c.copiedOn < :cutoff")
				.setParameter("cutoff", storyActivityCutoff)
				.executeUpdate();

		warnIfUnexpectedlyLarge("auth_session", sessionsDeleted);
		warnIfUnexpectedlyLarge("auth_refresh_token", refreshTokensDeleted);
		warnIfUnexpectedlyLarge("event_outbox", eventsDeleted);
		warnIfUnexpectedlyLarge("story_view", storyViewsDeleted);
		warnIfUnexpectedlyLarge("story_link_copy", storyLinkCopiesDeleted);

		return new PrivacyCleanupResult(sessionsDeleted, refreshTokensDeleted, eventsDeleted, storyViewsDeleted,
				storyLinkCopiesDeleted);
	}

	/**
	 * 한 카테고리가 설정된 상한을 넘겨 지워지면 경고 로그를 남긴다.
	 *
	 * <p>실패는 아니다 — 며칠 배치가 안 돌다가 밀린 것일 수 있다. 그래도 이례적인 규모는 사람이
	 * 한 번 보는 것이 안전하다(예: 유예 기간 설정이 잘못 들어간 경우 등).
	 */
	private void warnIfUnexpectedlyLarge(String category, int deletedCount) {
		if (deletedCount >= this.properties.getLargeDeletionWarningThreshold()) {
			log.warn("event=PRIVACY_CLEANUP_LARGE_DELETION category={} deletedCount={} threshold={}", category,
					deletedCount, this.properties.getLargeDeletionWarningThreshold());
		}
	}
}
