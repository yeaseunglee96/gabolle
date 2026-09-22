package com.gabolle.backend.trip.presentation.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.gabolle.backend.trip.application.port.TripCoverPort;
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
 * <p>🔴 <b>표지 세 칸은 그 원칙의 예외다 (S15P21E201-1370).</b> 「값 하나를 더 실으려고 줄마다
 * 표를 더 훑지 않는다」는 위 문단이 금지하는 것은 <b>줄마다 부르는 것</b>이지 값 자체가 아니다.
 * 표지는 {@link TripCoverPort} 가 여행 몇 개든 <b>질의 한 번</b>으로 한꺼번에 가져오므로 그
 * 금지에 걸리지 않는다. 줄마다 부르는 방식으로 되돌리면 그때는 걸린다.
 *
 * @param title    사용자가 붙인 이름. {@code null} 이면 아직 이름이 없고, 화면이 날짜를 제목으로
 *     그린다. 서버가 날짜 문자열을 대신 채워 보내지 않는다 — 그러면 사용자가 붙인 이름과
 *     서버가 만든 이름이 같은 칸에서 구분이 안 된다
 * @param dayCount 여행 일수. 날짜에서 나오는 값이라 저장하지 않고 그때 센다
 * @param role     요청자가 이 여행에서 가진 역할. 화면이 편집 버튼을 켤지 정하는 데 쓴다
 * @param coverImageUrl 첫 일정 첫 방문지의 대표 사진. <b>{@code null} 이 정상이고 오히려 흔하다</b> —
 *     2026-09-21 실서버에서 여행 59건 중 사진까지 있는 것은 10건이었다. 화면은 사진 없는 카드를
 *     기본으로 그리고 사진을 덤으로 얹어야 한다. 반대로 만들면 대부분의 카드가 빈 칸이 된다
 * @param firstStopNameKo 첫 방문지의 한국어 이름. 일정이 아직 없으면 {@code null}
 * @param firstStopNameEn 첫 방문지의 영어 이름. 없을 수 있다.
 *     <p>🔴 <b>이름을 서버가 한 언어로 골라 보내지 않는다.</b> 이 저장소의 장소 응답은
 *     {@code PlaceDetailResponse}·{@code PlaceSummaryResponse} 모두 {@code nameKo}·{@code nameEn}
 *     을 언어와 무관하게 둘 다 싣고 고르는 것은 화면의 몫이다. 앱의 언어 전환은 서버를 다시
 *     부르지 않고 그 자리에서 일어나므로({@code tx(ko, en)}), 서버가 하나만 고르면 영어로 바꾼
 *     화면에 한국어 이름이 남는다
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
		Instant updatedAt,
		String coverImageUrl,
		String firstStopNameKo,
		String firstStopNameEn) {

	/** @param cover 표지를 못 구했으면 {@code null}. 그때 표지 세 칸이 전부 {@code null} 이다 */
	public static TripSummaryResponse of(TripRepository.MemberTrip row, TripCoverPort.Cover cover) {
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
				t.updatedAt(),
				cover == null ? null : cover.imageUrl(),
				cover == null ? null : cover.stopNameKo(),
				cover == null ? null : cover.stopNameEn());
	}
}
