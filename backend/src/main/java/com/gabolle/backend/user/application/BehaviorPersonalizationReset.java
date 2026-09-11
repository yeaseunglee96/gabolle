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
 * 행동 기반 개인화를 껐을 때 <b>이미 만들어 둔 것을 되돌린다</b> — S15P21E201-549.
 *
 * <h2>🔴 스위치만으로는 끈 것이 아니다</h2>
 *
 * {@code ConsentUpdateService}(S15P21E201-735)가 동의 기록과 {@code personalization_mode} 를
 * 같은 트랜잭션에서 고치는 자리를 이미 만들어 뒀다. 그런데 그때까지 <b>지워지는 것은
 * 아무것도 없었다.</b> 껐다고 눌러도
 *
 * <ul>
 * <li>접어 둔 취향 벡터({@code user_taste_vector} · {@code user_taste_weight})가 그대로 있고,</li>
 * <li>그 벡터로 미리 골라 둔 피드({@code feed_build} · {@code user_feed} ·
 *     {@code community_feed})가 계속 화면에 나가고,</li>
 * <li>행동 관찰 원본({@code event_outbox})이 남아서 다시 켜거나 배치가 backfill 할 때
 *     <b>되살아난다.</b></li>
 * </ul>
 *
 * 사용자가 보기에 껐는데 추천은 어제 프로필 그대로다. 그건 안 껴진 것이고, 개인정보
 * 처리방침이 말하는 "철회하면 그 처리를 멈춥니다" 와도 다르다.
 *
 * <h2>🔴 행동 이벤트는 왜 <b>지우는가</b> — 탈퇴와 다른 점</h2>
 *
 * 탈퇴는 사람만 떼고 사건은 남긴다({@code AccountDeletionService.detachEvents}). 여기서 같은
 * 방법을 쓸 수 없다. 행동 이벤트는 대부분 축이 여행이라 {@code aggregate_id} 에 {@code trip_id}
 * 가 들어 있고, 개인화를 끈 사람의 <b>여행은 남는다.</b> {@code user_id} 를 비워도 여행을 거쳐
 * 그 사람으로 되돌아갈 수 있다. 탈퇴는 여행 자체가 함께 지워져서 그 길이 끊긴다.
 *
 * <p>대가가 있다 — <b>이 사람 몫의 과거 집계가 줄어든다.</b> 알고 지운다. 동의를 거둔 데이터로
 * 만든 집계를 계속 갖고 있는 것이 더 나쁘고, <b>되돌릴 수 있는 가리기는 가린 것이 아니다.</b>
 *
 * <h2>다시 켜면</h2>
 *
 * 아무것도 복구하지 않는다. 벡터는 다음 배치가 <b>그 이후의</b> 행동으로 새로 접는다.
 * 껐던 기간의 행동은 영영 안 쓴다 — 그것이 껐다는 말의 뜻이다.
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
	 * <p>🔴 부르는 쪽의 트랜잭션 <b>안에서</b> 돈다 — 동의를 바꾸는 것과 지우는 것이 갈라지면
	 * "껐다고 나오는데 벡터는 남아 있는" 상태가 생긴다. 그래서 여기에 {@code @Transactional} 을
	 * 새로 열지 않는다({@code REQUIRES_NEW} 금지). {@code ConsentUpdateService.update} 가 이미
	 * 트랜잭션이다.
	 *
	 * <p>🔴 <b>순서가 곧 정확성이다.</b> 외래키가 셋 걸려 있다.
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
		//    🔴 추천 기록 자체는 지우지 않는다 — 사람이 직접 요청한 일의 운영 기록이지 행동
		//       관찰이 아니다. 그래서 연결만 끊는다(그 칸은 NULL 을 허용한다).
		//
		//    🔴 여기만 native SQL 이다. recommendation_job.taste_vector_id 는 컬럼은 있는데
		//       RecommendationJob 엔티티에 매핑돼 있지 않아 JPQL 로 닿을 수 없다
		//       (V20260905140000 이 나중에 붙인 칸이다). 매핑하는 편이 낫지만 그건 추천
		//       담당자의 파일이라 이 MR 에서 건드리지 않는다.
		//
		//    🔴 native SQL 이 gabolle schema 를 찾는 것은 spring.datasource.hikari.schema 가
		//       연결 자체에 스키마를 걸어 주기 때문이다 (docs/DB-STANDARD.md 2절,
		//       S15P21E201-546). 그 한 줄이 지워지면 이 문장이 먼저 죽는다.
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

		// 4. 행동 관찰 원본.
		Set<String> behaviorSignals = EventType.behaviorSignalWireNames();
		int events = this.entityManager
				.createQuery("DELETE FROM EventOutbox e WHERE e.userId = :userId AND e.eventType IN :types")
				.setParameter("userId", userId)
				.setParameter("types", behaviorSignals)
				.executeUpdate();

		log.info("행동 기반 개인화를 껐다 — 파생값을 지웠다 user={} 취향판={} 피드줄={} 행동이벤트={}", userId, vectors, feedRows,
				events);
	}

	private int execute(String jpql, UUID userId) {
		return this.entityManager.createQuery(jpql).setParameter("userId", userId).executeUpdate();
	}
}
