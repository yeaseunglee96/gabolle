package com.gabolle.backend.story.repository;

import java.util.Collection;
import java.util.List;
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

	/**
	 * 피드가 글 여러 개를 한 번에 그린다 — 글마다 따로 물으면 N+1 이 된다.
	 * {@code StoryResponseAssembler} 가 사진을 읽는 방식과 같다.
	 */
	List<StoryVideo> findByStoryIdIn(Collection<UUID> storyIds);

	/**
	 * 이미 다른 기록에 붙은 동영상인가 — S15P21E201-1282.
	 *
	 * <p>🔴 {@code uq_story_video_upload}(UNIQUE)가 <b>이미 DB 에서 막는다.</b> 그래도 여기서 미리
	 * 보는 이유는 <b>오류의 모양</b>이다 — 제약에 부딪히면 500 에 제약 이름만 남고, 여기서 보면
	 * 400 에 「이미 다른 기록에 붙은 동영상입니다」와 그 주소가 나간다.
	 * 사진이 {@code existsByUploadedImageId} 로 같은 일을 먼저 했다.
	 */
	boolean existsByUploadedVideoId(UUID uploadedVideoId);

	/** 이미 다른 동영상의 썸네일로 쓰인 사진인가. {@code uq_story_video_thumbnail} 과 같은 짝이다. */
	boolean existsByThumbnailUploadId(UUID thumbnailUploadId);
}
