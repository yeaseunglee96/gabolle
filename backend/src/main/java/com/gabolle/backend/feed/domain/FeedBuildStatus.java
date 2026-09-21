package com.gabolle.backend.feed.domain;

/**
 * 피드 한 세대의 상태. 정상 흐름은 BUILDING → READY → SUPERSEDED 이고,
 * BUILDING 에서 FAILED 로 빠질 수 있다. 실패해도 옛 READY 세대는 그대로 살아 있어서
 * 화면이 안 빈다.
 */
public enum FeedBuildStatus {

	/** 만드는 중. 아직 아무도 읽지 않는다. */
	BUILDING,

	/**
	 * 지금 읽히는 세대. 한 사용자·한 화면에 최대 하나이고, DB 의 조건부 UNIQUE 색인
	 * ({@code uq_feed_build_ready})이 막는다.
	 */
	READY,

	/** 다음 세대에 자리를 내줬다. 지우기 전까지 남아 있다. */
	SUPERSEDED,

	/** 만들다 실패했다. 이유가 반드시 붙는다 — DB 가 요구한다. */
	FAILED
}
