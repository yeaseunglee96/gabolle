package com.gabolle.backend.story.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.story.domain.StoryVideo;

/**
 * 기록에 붙은 동영상. {@code findByStoryId} 가 {@code Optional} 인 것은
 * {@code uq_story_video_story} 가 한 기록에 0개 또는 1개를 강제하기 때문이다.
 */
public interface StoryVideoRepository extends JpaRepository<StoryVideo, UUID> {

	Optional<StoryVideo> findByStoryId(UUID storyId);

	/**
	 * 피드가 글 여러 개를 한 번에 그린다 — 글마다 따로 물으면 N+1 이 된다.
	 * {@code StoryResponseAssembler} 가 사진을 읽는 방식과 같다.
	 */
	List<StoryVideo> findByStoryIdIn(Collection<UUID> storyIds);

	/**
	 * 이미 다른 기록에 붙은 동영상인가. {@code uq_story_video_upload} 가 DB 에서 막지만, 제약에
	 * 부딪히면 500 에 제약 이름만 남으므로 오류 모양을 위해 미리 본다.
	 */
	boolean existsByUploadedVideoId(UUID uploadedVideoId);

	/** 이미 다른 동영상의 썸네일로 쓰인 사진인가. {@code uq_story_video_thumbnail} 과 같은 짝이다. */
	boolean existsByThumbnailUploadId(UUID thumbnailUploadId);
}
