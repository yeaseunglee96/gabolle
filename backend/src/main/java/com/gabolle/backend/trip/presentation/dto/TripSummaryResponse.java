package com.gabolle.backend.trip.presentation.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 내 여행 목록 한 줄 — {@code GET /api/v1/trips}.
 *
 * <p>{@link TripDto} 를 재사용하지 않는다. 그쪽은 출발지 좌표·예산까지 담는 상세 응답이라,
 * 목록에 그대로 실으면 여행 50개를 부를 때 쓰지도 않는 값이 50배로 나간다. 방문지 수를 뺀 것도
 * 같은 이유다 — 일정 판의 항목을 세야 나오는 값이라 목록 한 줄에 표 셋을 더 훑게 된다.
 *
 * @param title    사용자가 붙인 이름. {@code null} 이면 아직 이름이 없고, 화면이 날짜를 제목으로
 *     그린다. 서버가 날짜 문자열을 대신 채워 보내지 않는다 — 그러면 사용자가 붙인 이름과
 *     서버가 만든 이름이 같은 칸에서 구분이 안 된다
 * @param dayCount 여행 일수. 날짜에서 나오는 값이라 저장하지 않고 그때 센다
 * @param role     요청자가 이 여행에서 가진 역할. 화면이 편집 버튼을 켤지 정하는 데 쓴다
 */
public record TripSummaryResponse(
		String tripId,
		String title,
		LocalDate startDate,
		LocalDate endDate,
		int dayCount,
		int partySize,
		Trip.Status status,
		TripMember.Role role,
		Instant createdAt,
		Instant updatedAt) {

	public static TripSummaryResponse of(TripRepository.MemberTrip row) {
		Trip t = row.trip();
		return new TripSummaryResponse(
				t.tripId(),
				t.title(),
				t.startDate(),
				t.finishDate(),
				t.days(),
				t.partySize(),
				t.status(),
				row.role(),
				t.createdAt(),
				t.updatedAt());
	}
}
