package com.gabolle.backend.story.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.story.domain.StoryImage;

public interface StoryImageRepository extends JpaRepository<StoryImage, UUID> {

	List<StoryImage> findByStoryIdOrderByPositionAsc(UUID storyId);

	/** 피드 한 페이지의 사진을 한 번에 읽는다 — 기록마다 질의하지 않는다. */
	List<StoryImage> findByStoryIdInOrderByStoryIdAscPositionAsc(Collection<UUID> storyIds);

	void deleteByStoryId(UUID storyId);

	/** 한 업로드는 한 기록에만 붙는다. DB 의 uq_story_image_upload 가 마지막 방어선이고 이것은 먼저 400 으로 답하는 용도다. */
	boolean existsByUploadedImageId(UUID uploadedImageId);
}
