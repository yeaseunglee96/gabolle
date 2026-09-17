package com.gabolle.backend.story.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.story.domain.StoryView;

/**
 * 조회 낱개 — S15P21E201-1204.
 */
public interface StoryViewRepository extends JpaRepository<StoryView, UUID> {

	/**
	 * 오늘 이 사람의 조회를 <b>한 번만</b> 남긴다.
	 *
	 * <h2>🔴 「오늘 것이 있나」를 먼저 읽지 않는다</h2>
	 *
	 * 읽고-없으면-넣는 모양은 <b>같은 사람이 두 기기에서 동시에 열면 둘 다 통과한다.</b> 둘 다
	 * 읽는 시점에는 없기 때문이다. 그래서 넣어 보고 <b>돌아온 행 수로</b> 판정한다 —
	 * {@code 1} 이면 오늘 처음이고, {@code 0} 이면 이미 셌다. 경주가 아예 생기지 않는다.
	 *
	 * <p>막는 것은 {@code S15P21E201-1201} 이 만든 조건부 유일 색인 둘이다
	 * ({@code ux_story_view_member} · {@code ux_story_view_anonymous}).
	 * {@code StoryReactionRepository.upsert} 와 {@code SavedPlaceRepository.insertIfAbsent} 가
	 * 같은 방식을 먼저 썼다.
	 *
	 * <p>{@code ON CONFLICT} 에 대상을 안 적는다. 막는 색인이 <b>조건부</b>(부분 색인)라 대상을
	 * 적으려면 그 조건까지 똑같이 옮겨 적어야 하고, 그러면 <b>색인 정의와 이 문장이 두 벌</b>이
	 * 된다. 이 표에 걸린 제약은 그 둘과 기본키뿐이고 기본키는 새 UUID 라 부딪히지 않는다.
	 *
	 * <h2>🔴 {@code clearAutomatically} 를 쓰지 않는다 — 본보기와 다른 자리다</h2>
	 *
	 * {@code StoryReactionRepository.upsert} 는 {@code clearAutomatically = true} 를 쓴다.
	 * 여기서 그러면 <b>부르는 쪽이 들고 있던 {@code Story} 가 떨어져 나간다.</b> 그 객체의
	 * 누적 칸({@code viewCount})을 올려도 <b>저장이 안 되고, 오류도 안 난다.</b>
	 *
	 * <p>{@code flushAutomatically} 만 둔다 — 앞선 변경을 먼저 내보내되 컨텍스트는 비우지 않는다.
	 *
	 * @return 오늘 처음이면 1, 이미 셌으면 0
	 */
	@Transactional
	@Modifying(flushAutomatically = true)
	@Query(value = """
			INSERT INTO story_view
			    (story_view_id, story_id, user_id, anonymous_session_id, viewed_on, created_at)
			VALUES (:storyViewId, :storyId, :userId, :anonymousSessionId, :viewedOn, :now)
			ON CONFLICT DO NOTHING
			""", nativeQuery = true)
	int insertIfAbsent(@Param("storyViewId") UUID storyViewId, @Param("storyId") UUID storyId,
			@Param("userId") UUID userId, @Param("anonymousSessionId") UUID anonymousSessionId,
			@Param("viewedOn") LocalDate viewedOn, @Param("now") Instant now);

}
