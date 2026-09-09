package com.gabolle.backend.trip.presentation.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 내 여행 목록 한 줄 — {@code GET /api/v1/trips} · S15P21E201-738.
 *
 * <p>🔴 {@link TripDto} 를 재사용하지 않았다. 그쪽은 출발지 좌표·예산·이동 시간대까지
 * 담는 상세 응답이고, 목록은 화면에 카드 한 장을 그리는 데 필요한 것만 있으면 된다.
 * 목록에 상세를 그대로 실으면 여행 50개를 부를 때 쓰지도 않는 값이 50배로 나간다.
 *
 * <p>🔴 <b>제목이 없다.</b> {@link Trip} 에 제목 칸이 자체가 없어서다 — 지금 앱 목록이
 * 보여 주는 제목은 여행이 아니라 일정에서 가져온 값이다. 여기에 제목을 넣으려면 여행에
 * 칸을 만들거나 일정을 함께 읽어야 하는데, 둘 다 이 티켓의 범위 밖이라 앱이 지금처럼
 * 일정에서 채우게 뒀다. 방문지 수를 뺀 것도 같은 이유다 — 그 값은 일정 판의 항목을
 * 세야 나오고, 목록 한 줄을 위해 표 셋을 더 훑는 것은 값이 안 맞는다.
 *
 * @param dayCount 여행 일수. 날짜에서 나오는 값이라 저장하지 않고 그때 센다
 * @param role     요청자가 이 여행에서 가진 역할. 화면이 편집 버튼을 켤지 정하는 데 쓴다
 */
public record TripSummaryResponse(
		String tripId,
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
