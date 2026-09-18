package com.gabolle.backend.story.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.story.domain.StoryVideo;

/**
 * 기록에 붙은 동영상 — S15P21E201-1275.
 *
 * <p>🔴 {@code findByStoryId} 가 {@code Optional} 인 것은 <b>표가 그렇게 강제</b>하기 때문이다
 * ({@code uq_story_video_story}). 한 기록에 동영상은 0개 또는 1개다 — 목록이 아니다.
 */
public interface StoryVideoRepository extends JpaRepository<StoryVideo, UUID> {

	Optional<StoryVideo> findByStoryId(UUID storyId);
}
