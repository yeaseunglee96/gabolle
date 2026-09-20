package com.gabolle.backend.moderation.domain;

/** 운영자의 처리 결과. */
public enum StoryReportResolution {

	/** 기록과 저장소 사진을 지웠다. */
	REMOVED,

	/** 신고를 기각했다. 기록이 다시 보인다. */
	DISMISSED
}
