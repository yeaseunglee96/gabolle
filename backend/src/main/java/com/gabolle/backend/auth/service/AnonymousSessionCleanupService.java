package com.gabolle.backend.auth.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 버려진 익명 세션을 그 세션이 만든 여행과 함께 지운다. 개인정보 정리 배치가 하루 한 번 부른다.
 *
 * <p>세션만 지우면 안 된다. {@code trip.owner_user_id} 에는 외래키가 없어서 DB 가 막지 않고, 여행·출발지
 * 좌표·숙소가 누구도 열 수 없는 채로 남는다. 반대로 여행만 지우고 세션을 남기면 지울 대상을 다음 날
 * 다시 고른다. 그래서 둘을 한 트랜잭션에서 지운다.
 *
 * <p>로그인해서 승계된 여행은 {@code owner_type} 이 USER 로 바뀌어 대상에서 빠진다
 * ({@link AnonymousSessionHandoverService} 는 승계와 함께 세션도 지운다).
 */
@Service
@Profile({"db", "dev"})
public class AnonymousSessionCleanupService {

	/** 여행 종료일은 한국 날짜다. 서버 시간대(UTC)로 오늘을 잡으면 오전 9시 전에 하루 어긋난다. */
	private static final ZoneId TRIP_ZONE = ZoneId.of("Asia/Seoul");

	@PersistenceContext
	private EntityManager entityManager;

	private final AccountDeletionService deletionService;
	private final Clock clock;

	public AnonymousSessionCleanupService(AccountDeletionService deletionService, Clock clock) {
		this.deletionService = deletionService;
		this.clock = clock;
	}

	/**
	 * @return 지운 세션 수
	 */
	@Transactional
	public int cleanup(int idleDays, int tripGraceDays, int maxAgeDays, int batchSize) {
		Instant now = clock.instant();
		LocalDate stillRunningFrom = LocalDate.ofInstant(now, TRIP_ZONE).minusDays(tripGraceDays);

		List<UUID> expired = entityManager.createQuery("""
				SELECT s.sessionId FROM AnonymousSession s
				WHERE s.createdAt < :tooOld
				   OR (s.lastSeenAt < :idleSince AND NOT EXISTS (
						SELECT t.tripId FROM TripJpaEntity t
						WHERE t.ownerType = 'ANONYMOUS' AND t.ownerUserId = s.sessionId
						  AND t.deletedAt IS NULL AND t.endDate >= :stillRunningFrom))
				ORDER BY s.lastSeenAt
				""", UUID.class)
				.setParameter("tooOld", now.minus(Duration.ofDays(maxAgeDays)))
				.setParameter("idleSince", now.minus(Duration.ofDays(idleDays)))
				.setParameter("stillRunningFrom", stillRunningFrom)
				.setMaxResults(batchSize)
				.getResultList();

		for (UUID sessionId : expired) {
			deletionService.deleteAnonymousOwnerData(sessionId);
		}
		if (!expired.isEmpty()) {
			entityManager.createQuery("DELETE FROM AnonymousSession s WHERE s.sessionId IN :ids")
					.setParameter("ids", expired)
					.executeUpdate();
		}
		return expired.size();
	}
}
