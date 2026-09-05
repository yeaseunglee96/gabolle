package com.gabolle.backend.feed.domain;

/**
 * 홈 피드 줄이 가리키는 것의 종류.
 *
 * <p>🔴 값 목록이 DB 의 {@code ck_user_feed_item_type} 과 같아야 한다. 종류를 늘릴 때는
 * 마이그레이션과 이 열거형을 <b>같은 MR 에서</b> 고친다.
 */
public enum FeedItemType {

	/** 장소 한 곳. {@code place} 표를 가리킨다. */
	PLACE,

	/** 완성된 일정 하나. {@code itineraries} 표를 가리킨다. */
	ITINERARY,

	/**
	 * 여러 장소를 묶은 코스.
	 *
	 * <p>🔴 코스를 담는 표는 아직 없다. 종류만 먼저 열어 둔 것이 아니라, 화면 설계에
	 * "추천 코스" 자리가 이미 있어서 넣었다. 표가 생기기 전에는 이 값이 안 쓰인다.
	 */
	COURSE
}
