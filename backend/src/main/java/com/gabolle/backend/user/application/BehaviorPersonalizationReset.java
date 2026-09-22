package com.gabolle.backend.user.application;

import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.event.domain.EventType;

/**
 * 행동 기반 개인화를 껐을 때 이미 만들어 둔 것을 되돌린다 — 취향 벡터와 성분, 그 벡터로
 * 미리 골라 둔 피드, 개인 속도 계수, 행동 관찰 원본을 함께 지운다. 스위치만 내리면 추천이
 * 어제 프로필 그대로 나간다.
 *
 * <p>행동 이벤트는 탈퇴와 달리 사람만 떼지 않고 지운다. 행동 이벤트는 대부분 축이 여행이라
 * {@code aggregate_id} 에 {@code trip_id} 가 들어 있고 개인화를 끈 사람의 여행은 남으므로,
 * {@code user_id} 를 비워도 여행을 거쳐 그 사람으로 되돌아갈 수 있다. 대가로 이 사람 몫의
 * 과거 집계가 줄어든다.
 *
 * <p>다시 켜도 아무것도 복구하지 않는다. 벡터는 다음 배치가 그 이후의 행동으로 새로 접고,
 * 껐던 기간의 행동은 쓰지 않는다.
 */
@Component
@Profile({ "db", "dev" })
public class BehaviorPersonalizationReset {

	private static final Logger log = LoggerFactory.getLogger(BehaviorPersonalizationReset.class);

	@PersistenceContext
	private EntityManager entityManager;

	/**
	 * 이 사람의 행동으로 만든 것을 전부 지운다.
	 *
	 * <p>부르는 쪽의 트랜잭션 안에서 돈다. 여기에 {@code @Transactional} 을 새로 열면
	 * ({@code REQUIRES_NEW}) 동의를 바꾸는 것과 지우는 것이 갈라져, 껐다고 나오는데 벡터는
	 * 남아 있는 상태가 생긴다.
	 *
	 * <p>순서가 곧 정확성이다. 외래키가 셋 걸려 있다.
	 * <ul>
	 * <li>{@code user_feed} · {@code community_feed} → {@code feed_build}</li>
	 * <li>{@code feed_build.taste_vector_id} → {@code user_taste_vector}</li>
	 * <li>{@code recommendation_job.taste_vector_id} → {@code user_taste_vector}</li>
	 * </ul>
	 * {@code user_taste_weight} 만 {@code ON DELETE CASCADE} 인데, JPQL 벌크 삭제는 DB 의
	 * CASCADE 를 타지 않으므로 그것도 직접 지운다({@code AccountDeletionService} 와 같은 규칙).
	 */
	public void forget(UUID userId) {
		// 1. 미리 만들어 둔 피드 — 줄을 먼저, 세대를 나중에.
		int feedRows = execute("""
				DELETE FROM UserFeedEntry e WHERE e.id.buildId IN
				(SELECT b.buildId FROM FeedBuild b WHERE b.userId = :userId)
				""", userId)
				+ execute("""
						DELETE FROM CommunityFeedEntry e WHERE e.id.buildId IN
						(SELECT b.buildId FROM FeedBuild b WHERE b.userId = :userId)
						""", userId);
		execute("DELETE FROM FeedBuild b WHERE b.userId = :userId", userId);

		// 2. 추천 기록이 가리키는 벡터 연결을 끊는다.
		//
		// 추천 기록 자체는 지우지 않는다 — 사람이 직접 요청한 일의 운영 기록이지 행동
		// 관찰이 아니다. 그래서 연결만 끊는다(그 칸은 NULL 을 허용한다).
		//
		// 여기만 native SQL 이다. recommendation_job.taste_vector_id 는 컬럼은 있는데
		// RecommendationJob 엔티티에 매핑돼 있지 않아 JPQL 로 닿을 수 없다.
		// TODO 그 칸을 엔티티에 매핑하고 이 문장을 JPQL 로 바꾼다.
		//
		// 이 문장은 spring.datasource.hikari.schema 가 연결에 gabolle schema 를 걸어
		// 두는 것에 기대고 있다.
		this.entityManager.createNativeQuery("""
				UPDATE recommendation_job SET taste_vector_id = NULL
				 WHERE taste_vector_id IN (SELECT taste_vector_id FROM user_taste_vector WHERE user_id = :userId)
				""").setParameter("userId", userId).executeUpdate();

		// 3. 접어 둔 취향 — 성분을 먼저, 판을 나중에.
		execute("""
				DELETE FROM UserTasteWeight w WHERE w.id.tasteVectorId IN
				(SELECT v.tasteVectorId FROM UserTasteVector v WHERE v.userId = :userId)
				""", userId);
		int vectors = execute("DELETE FROM UserTasteVector v WHERE v.userId = :userId", userId);

		// 4. 실제 방문 시각으로 만든 개인 속도 계수. 행동으로 만든 사람별 프로필이라
		// 벡터와 같은 스위치가 지운다.
		//
		// 판 체인 전체를 지운다 — superseded_at 이 찍힌 옛 판까지. 현재 판만 지우면
		// 지금은 안 쓰지만 남아 있는 프로필이 된다.
		//
		// user_pace_factor 는 app_user 에 ON DELETE CASCADE 로 묶여 있지만 여기서는
		// 계정이 살아 있으므로 그것에 기대지 않는다.
		int paceVersions = execute("DELETE FROM PaceFactorJpaEntity p WHERE p.userId = :userId", userId);

		// 5. 행동 관찰 원본.
		Set<String> behaviorSignals = EventType.behaviorSignalWireNames();
		int events = this.entityManager
				.createQuery("DELETE FROM EventOutbox e WHERE e.userId = :userId AND e.eventType IN :types")
				.setParameter("userId", userId)
				.setParameter("types", behaviorSignals)
				.executeUpdate();

		log.info("행동 기반 개인화를 껐다 — 파생값을 지웠다 user={} 취향판={} 피드줄={} 속도계수판={} 행동이벤트={}", userId, vectors,
				feedRows, paceVersions, events);
	}

	private int execute(String jpql, UUID userId) {
		return this.entityManager.createQuery(jpql).setParameter("userId", userId).executeUpdate();
	}
}
