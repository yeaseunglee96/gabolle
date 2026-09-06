package com.gabolle.backend.story.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.story.domain.UserFollow;

public interface UserFollowRepository extends JpaRepository<UserFollow, UserFollow.Key> {

	boolean existsByKey(UserFollow.Key key);

	/** 팔로워 수 — 이 사람을 팔로우하는 사람의 수. */
	long countByKeyFolloweeUserId(UUID followeeUserId);

	/** 팔로잉 수 — 이 사람이 팔로우하는 사람의 수. */
	long countByKeyFollowerUserId(UUID followerUserId);
}
