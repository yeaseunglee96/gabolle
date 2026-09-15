package com.gabolle.backend.trip.presentation.dto;

import java.time.Instant;

import com.gabolle.backend.trip.domain.Trip;

/**
 * 이름을 바꾼 결과 — {@code PUT /api/v1/trips/{tripId}/title} · S15P21E201-1023.
 *
 * <p>여행 전체를 돌려주지 않는다. 이 요청이 바꾼 것은 칸 하나뿐이고, 화면은 카드 한 장의
 * 제목만 고쳐 그리면 된다 — 전체를 돌려주면 화면이 «응답으로 통째로 갈아끼우기» 를 하게
 * 되고, 그때 이 요청과 상관없는 칸이 조용히 옛 값으로 되돌아간다.
 *
 * @param title 바뀐 이름. 🔴 {@code null} 이면 이름이 <b>지워진 것</b>이다
 * @param updatedAt 화면이 목록의 정렬(최근에 손댄 순)을 고칠 수 있게 함께 준다
 */
public record TripTitleResponse(String tripId, String title, Instant updatedAt) {

	public static TripTitleResponse of(Trip trip) {
		return new TripTitleResponse(trip.tripId(), trip.title(), trip.updatedAt());
	}
}
