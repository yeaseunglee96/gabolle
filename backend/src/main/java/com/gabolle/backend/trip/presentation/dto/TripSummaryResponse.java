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
 * <p>🔴 <b>2026-09-16 정정 — 제목이 생겼다 (S15P21E201-1023).</b> 이 자리에 이렇게 적혀
 * 있었다: <i>"제목이 없다. {@link Trip} 에 제목 칸 자체가 없어서다."</i> 그 말은 그때 사실이었고
 * 지금은 아니다. 여행 카드의 제목이 날짜뿐이라 <b>같은 날짜로 두 번 계획하면 두 카드가 글자
 * 하나 다르지 않았다</b> — 「다시 짜 보기」가 기본 동작인 서비스에서 그건 드문 일이 아니다.
 *
 * <p>방문지 수를 뺀 것은 여전히 같은 이유다 — 그 값은 일정 판의 항목을 세야 나오고,
 * 목록 한 줄을 위해 표 셋을 더 훑는 것은 값이 안 맞는다.
 *
 * @param title    사용자가 붙인 이름. 🔴 {@code null} 이면 <b>아직 이름이 없다</b> — 화면은
 *     지금까지 하던 대로 날짜를 제목으로 그린다. 서버가 날짜 문자열을 대신 채워 보내지
 *     않는다: 그러면 「사용자가 붙인 이름」과 「서버가 만든 이름」이 같은 칸에서 구분이
 *     안 되고, 화면이 이름 있는 카드를 다르게 그릴 방법이 없어진다
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
