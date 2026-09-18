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
 * 개인정보 자동 정리 배치(S15P21E201-357 · -166)가 실제로 지우는 곳.
 *
 * <h2>🔴 지우는 세 가지와, 지우지 않는 것</h2>
 *
 * <ol>
 * <li>만료된 지 {@code sessionGraceDays} 가 지난 {@code auth_session} — DB 의
 * {@code fk_auth_refresh_token_session ... ON DELETE CASCADE} 가 그 세션에 남아 있던
 * 리프레시 토큰도 함께 지운다({@code V20260902_1__create_auth_schema.sql}).</li>
 * <li>세션과 별개로 자기 자신이 만료된 지 유예 기간이 지난 {@code auth_refresh_token} — 로테이션으로
 * 이미 못 쓰게 된 옛 토큰처럼, 세션 자체는 아직 살아 있어도 토큰만 만료된 경우다.</li>
 * <li>발행이 끝난({@code publishedAt IS NOT NULL}) 지 {@code eventRetentionDays} 가 지난
 * {@code event_outbox} 행.</li>
 * <li>🔴 <b>S15P21E201-1216</b> — {@code storyActivityRetentionDays} 가 지난 조회 낱개
 * ({@code story_view})와 링크 복사 낱개({@code story_link_copy}).</li>
 * </ol>
 *
 * <p>🔴 <b>{@code publishedAt IS NULL} 인 이벤트는 절대 지우지 않는다.</b>
 * {@code EventOutboxRepository.findByPublishedAtIsNullOrderBySeqAsc} 의 자체 주석이 말하듯
 * {@code publishStatus} 가 FAILED 여도 {@code publishedAt} 이 비어 있으면 릴레이가 다시
 * 보내야 하는 이벤트다 — 이 배치가 먼저 지우면 그 이벤트는 영영 나가지 않는다.
 *
 * <p>🔴 <b>조회·복사 낱개를 지우면서 누적 칸을 같이 내리지 않는다.</b> {@code story.view_count}
 * 와 {@code story.link_copy_count} 는 손대지 않는다 — 낱개는 「사람 × 글 × 하루 한 번」을 지키려고
 * 두는 것이고 누적은 누적이다. 같이 내리면 <b>어제까지의 조회가 사라진다.</b>
 * {@code V20260918010000} 이 건 {@code CHECK (view_count >= 0)} 이 정확히 이 실수를 막으려고
 * 있다 — 같이 내리면 조용히 음수가 되기 때문이다.
 *
 * <p>위치 정보는 이 배치가 다루지 않는다 — {@code PlaceVisitVerification}(S15P21E201-279)이
 * 애초에 좌표 칸 자체를 두지 않게 설계되어 있어 지울 좌표가 없다.
 *
 * <h2>세 삭제가 왜 JPQL 벌크 삭제인가</h2>
 *
 * {@link com.gabolle.backend.auth.service.AccountDeletionService} 와 같은 이유다 — 대상이
 * 몇만 건이 될 수 있는 정기 배치에서 엔티티를 하나씩 읽어 지우면(영속성 컨텍스트에 전부 올라가며)
 * 메모리와 시간이 행 수에 비례해 늘어난다. JPQL {@code DELETE} 는 SQL 한 줄로 끝난다.
 */
@Service
@Profile({ "db", "dev" })
public class PrivacyCleanupService {

	private static final Logger log = LoggerFactory.getLogger(PrivacyCleanupService.class);

	/**
	 * 조회·복사 낱개의 날짜 칸이 재고 있는 시간대 — S15P21E201-1216.
	 *
	 * <p>🔴 {@code story_view.viewed_on} 과 {@code story_link_copy.copied_on} 은 <b>DATE</b> 이고,
	 * 그 값을 채우는 {@code StoryService} 가 <b>한국 시각으로 계산해서</b> 넣는다. 지우는 쪽이
	 * 서버 시간대(UTC)로 기준선을 잡으면 <b>쓰는 쪽과 읽는 쪽이 서로 다른 하루를 쓰게 된다</b> —
	 * 한국 시각 오전 9시 전에는 기준선이 하루 어긋난다.
	 *
	 * <p>🔴 이 값은 {@code StoryService.COUNTING_ZONE} 과 <b>반드시 같아야 한다.</b> 이 저장소는
	 * 시간대를 쓰는 클래스마다 따로 선언하는 관례를 쓰므로(상수를 공유하지 않는다) 한쪽을 바꾸면
	 * 다른 쪽도 바꾼다.
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
	 * <p>🔴 한 트랜잭션이다. {@link com.gabolle.backend.auth.service.AccountDeletionService} 와
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

		// 🔴 S15P21E201-1216 — 낱개의 날짜 칸은 DATE 이고 한국 시각으로 채워진다. Instant 에서
		// 며칠을 빼는 것이 아니라 한국 시각의 「오늘」에서 날짜로 뺀다. 이 기준선보다 **앞선**
		// 날짜만 지운다 — 딱 90일 된 것은 남는다.
		LocalDate storyActivityCutoff = LocalDate.ofInstant(now, STORY_ACTIVITY_ZONE)
				.minusDays(this.properties.getStoryActivityRetentionDays());

		// 🔴 누적 칸(story.view_count · story.link_copy_count)은 여기서 손대지 않는다.
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
