package com.gabolle.backend.feed.domain;

/** 어느 화면의 피드인가. 표는 따로지만 세대 관리는 같은 방식으로 돈다. */
public enum FeedSurface {

	/** 앱을 켰을 때 첫 화면. 장소·일정을 추천한다. */
	HOME,

	/** 커뮤니티에 들어갔을 때. 글을 추천한다. */
	COMMUNITY
}
