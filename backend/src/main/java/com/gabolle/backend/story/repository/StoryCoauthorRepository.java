package com.gabolle.backend.story.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.story.domain.StoryCoauthor;

public interface StoryCoauthorRepository extends JpaRepository<StoryCoauthor, StoryCoauthor.Key> {

	List<StoryCoauthor> findByKeyStoryId(UUID storyId);

	/** 초대 수락·직접 추가 전 중복을 막는 자리. */
	boolean existsByKey(StoryCoauthor.Key key);

	long countByKeyStoryId(UUID storyId);

	void deleteByKey(StoryCoauthor.Key key);
}
