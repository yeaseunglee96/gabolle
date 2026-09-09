package com.gabolle.backend.story.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.story.domain.StoryCoauthor;

public interface StoryCoauthorRepository extends JpaRepository<StoryCoauthor, StoryCoauthor.Key> {

	/** 한 기록의 참여자 전부. */
	List<StoryCoauthor> findByKeyStoryId(UUID storyId);

	/** (기록, 사람) 이 이미 참여자인가 — 초대 수락·직접 추가 전 중복 방지에 쓴다. */
	boolean existsByKey(StoryCoauthor.Key key);

	/** 한 기록의 참여자 수. */
	long countByKeyStoryId(UUID storyId);

	/** (기록, 사람) 한 쌍을 내보낸다 — 참여자를 뺄 때. */
	void deleteByKey(StoryCoauthor.Key key);
}
