package com.gabolle.backend.feed.domain;

/**
 * 피드 한 세대의 상태.
 *
 * <p>이 네 값이 "다시 만드는 동안 사람이 반쪽을 보지 않게" 하는 장치의 전부다.
 *
 * <pre>
 *   BUILDING ──▶ READY ──▶ SUPERSEDED   (정상)
 *      │
 *      └──────▶ FAILED                  (만들다 실패 — 옛 READY 는 그대로 살아 있다)
 * </pre>
 *
 * <p>🔴 실패해도 사람은 옛 피드를 계속 본다. 그것이 이 상태를 나눈 이유다 —
 * 실패가 화면을 비우면 안 된다.
 */
public enum FeedBuildStatus {

	/** 만드는 중. 아직 아무도 이 세대를 읽지 않는다. */
	BUILDING,

	/**
	 * 지금 읽히는 세대.
	 *
	 * <p>🔴 한 사용자·한 화면에 <b>최대 하나</b>다. DB 의 조건부 UNIQUE 색인
	 * ({@code uq_feed_build_ready})이 막는다 — 애플리케이션이 실수해도 둘이 될 수 없다.
	 */
	READY,

	/** 다음 세대에 자리를 내줬다. 지우기 전까지 남아 있다. */
	SUPERSEDED,

	/** 만들다 실패했다. 이유가 반드시 붙는다 — DB 가 요구한다. */
	FAILED
}
