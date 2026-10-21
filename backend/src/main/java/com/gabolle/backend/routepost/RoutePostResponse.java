package com.gabolle.backend.routepost;

import java.util.List;

/**
 * 루트 포스트 한 건(-2027). 피드 카드·상세·장소별 목록이 같은 모양을 쓴다.
 *
 * @param dayIndex 0부터 센 하루. {@code null} 이면 일정 전체
 * @param path 지도 썸네일 선 — {@code [lat, lng]} 점들
 * @param mine 내가 올린 글인가 — 지우기 버튼을 보일지
 */
public record RoutePostResponse(String routePostId, Author author, String title, String body, Integer dayIndex,
		int dayCount, Integer durationMin, Integer distanceM, List<double[]> path, List<Stop> stops, int likeCount,
		int saveCount, int copyCount, boolean likedByMe, boolean savedByMe, boolean mine, String createdAt) {

	public record Author(String userId, String displayName, String avatarUrl) {
	}

	/** 들른 곳. {@code name} 은 지금 장소 이름(없으면 올릴 때 이름), {@code nameEn} 은 영어 이름. */
	public record Stop(int seq, int dayIndex, String placeId, String name, String nameEn, double lat, double lng,
			String photoUrl, Integer stayMinutes) {
	}

	/** 커서 한 쪽. {@code nextCursor} 가 {@code null} 이면 끝이다. */
	public record Page(List<RoutePostResponse> items, String nextCursor) {
	}
}
