package com.gabolle.backend.story.domain;

/**
 * 기록의 공개 범위 — {@code story.visibility} 의 CHECK 값과 같다.
 *
 * <p>{@code PRIVATE} 기록에 남이 접근하면 403 이 아니라 404 다. 403 은 "있는데 못 본다" 를 알려 주는
 * 셈이라 존재 사실이 샌다. {@code FOLLOWERS} 도 같다 — 팔로우하지 않은 사람에게는 없는 기록이다.
 */
public enum StoryVisibility {
	/** 누구나. 전체 피드에 나온다. */
	PUBLIC,
	/** 나를 팔로우한 사람만. 팔로잉 피드에만 나온다. */
	FOLLOWERS,
	/** 나만. 어느 피드에도 안 나온다. */
	PRIVATE
}
