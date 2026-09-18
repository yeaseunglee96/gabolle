package com.gabolle.backend.story.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.story.domain.StorySave;

/** {@code SavedPlaceRepository}와 같은 패턴 — 근거는 그쪽 주석 참고. */
public interface StorySaveRepository extends JpaRepository<StorySave, UUID> {

	List<StorySave> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

	void deleteByUserIdAndStoryId(UUID userId, UUID storyId);

	/**
	 * 없으면 넣고, 이미 있으면 아무것도 안 한다.
	 *
	 * <p>연타·재시도로 두 요청이 동시에 오면 한쪽만 실제로 넣는다 — {@code
	 * SavedPlaceRepository#insertIfAbsent}와 같은 이유(DB 한 문장 안에서 판정을 끝내
	 * {@code uk_story_save} 위반 500을 막는다).
	 *
	 * @return 실제로 넣었으면 1, 이미 있어서 아무것도 안 했으면 0
	 */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			INSERT INTO story_save (story_save_id, user_id, story_id, created_at)
			VALUES (:storySaveId, :userId, :storyId, :createdAt)
			ON CONFLICT (user_id, story_id) DO NOTHING
			""", nativeQuery = true)
	int insertIfAbsent(@Param("storySaveId") UUID storySaveId, @Param("userId") UUID userId,
			@Param("storyId") UUID storyId, @Param("createdAt") OffsetDateTime createdAt);
}
