package com.gabolle.backend.moderation.domain;

/**
 * 기록의 신고 검토 상태. {@code deleted_at} 과 별도의 칸이다 — 기각하면 다시 보여야 하는데
 * 작성자가 지운 것은 되살리지 않으므로, 한 칸에 담으면 둘을 구분할 수 없다.
 *
 * 대가로 조회 경로가 전부 이 값을 봐야 한다. 피드·상세·프로필 피드 중 하나라도 빠뜨리면 신고된
 * 기록이 그 화면에만 계속 보인다.
 */
public enum StoryModerationState {

	/** 정상. 피드와 상세에 보인다. */
	VISIBLE,

	/** 신고를 받아 검토 대기 중. 운영자가 보기 전에 즉시 피드와 상세에서 빠진다. */
	UNDER_REVIEW,

	/** 운영자가 삭제로 처리했다. 되돌리지 않는다. */
	REMOVED;

	public boolean visibleToOthers() {
		return this == VISIBLE;
	}
}
