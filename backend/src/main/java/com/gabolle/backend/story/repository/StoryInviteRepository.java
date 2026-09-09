package com.gabolle.backend.story.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.story.domain.StoryInvite;

public interface StoryInviteRepository extends JpaRepository<StoryInvite, UUID> {

	/** 링크에 실린 표(token)로 한 건 찾는다 — 초대 링크를 눌렀을 때의 유일한 진입점이다. */
	Optional<StoryInvite> findByToken(String token);
}
