package com.gabolle.backend.feed.domain;

/**
 * 홈 피드 줄이 가리키는 것의 종류. 값 목록이 DB 의 {@code ck_user_feed_item_type} 과
 * 같아야 해서, 종류를 늘릴 때는 마이그레이션과 이 열거형을 같이 고친다.
 */
public enum FeedItemType {

	/** 장소 한 곳. {@code place} 표를 가리킨다. */
	PLACE,

	/** 완성된 일정 하나. {@code itineraries} 표를 가리킨다. */
	ITINERARY,

	/** 여러 장소를 묶은 코스. 담는 표가 아직 없어서 표가 생기기 전에는 안 쓰인다. */
	COURSE
}
