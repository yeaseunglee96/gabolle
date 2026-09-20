package com.gabolle.backend.story.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.story.domain.StoryInvite;

public interface StoryInviteRepository extends JpaRepository<StoryInvite, UUID> {

	Optional<StoryInvite> findByToken(String token);
}
