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
}
