package com.gabolle.backend.share.presentation.dto;

import java.util.List;

/**
 * 읽기 전용 공유 조회 응답 — S15P21E201-330 · -332 (F-COL-03).
 *
 * <p>🔴 <b>이 응답은 원본({@code ItineraryDetailResponse}·{@code TripDetailResponse})에서 칸을 비운 것이
 * 아니라, 보낼 것만 담아 새로 만든 모양이다.</b> 공유 링크는 로그인 없이 아무나 연다 — 여기 담아
 * 보내는 것은 곧 공개하는 것이다. 값을 {@code null} 로 비우는 것으로는 부족하다. 항목 이름이 남으면
 * 이 서비스가 출발지 좌표를 갖고 있다는 사실이 드러나고, 나중에 누가 그 칸을 채우는 실수를 하기도
 * 쉽다. 그래서 출발지 좌표·연락처·예산·인원 칸은 이 record 에 <b>없다</b>.
 *
 * <p>날짜·시간·장소 이름·분류·머무는 시간은 그대로 보낸다 — 공유의 목적이 그것이다.
 *
 * <p>🔴 칸을 더하기 전에 {@code SharedItineraryResponseWhitelistTest} 를 본다. 그 테스트가 이 record 와
 * 안쪽 record 의 필드 이름 전부를 허용 목록과 대조한다 — 칸이 하나 늘면 빨개진다. 그것이 완료 기준
 * "나중에 응답에 칸이 하나 늘어도 잡아내는 자동 검사" 다. 늘리는 것이 맞다면 허용 목록도 함께 고친다.
 *
 * @param title 여행 기간으로 지어낸 제목({@code ItineraryQueryService.buildTitle} 과 같은 규칙)
 * @param version 공유 시점의 최신 판. 일정이 아직 없는 여행(PLANNING)이면 {@code null}
 * @param days 여행 기간의 날짜 전부. 항목이 0개인 날도 빈 {@code items} 로 들어간다
 * @param expiresAt 이 공유 주소의 만료 시각(ISO-8601). 화면이 "언제까지 볼 수 있나" 를 알린다
 * @param notShared 공유되지 않는 항목의 이름 — 화면 위쪽 고지문이 쓴다. 값이 아니라 이름이다
 */
public record SharedItineraryResponse(
		String title,
		String startDate,
		String finishDate,
		Integer version,
		List<Day> days,
		String expiresAt,
		List<String> notShared) {

	/** 화면 고지문에 쓰는 고정 목록. 이 응답에 <b>없는</b> 것의 이름이다. */
	public static final List<String> NOT_SHARED = List.of("origin", "contact", "budget", "partySize");

	public record Day(String date, List<Item> items) {
	}

	public record Item(
			int sequence,
			String placeName,
			/** {@code place.category}. 값 목록이 아직 없어 자유 문자열이다. 없으면 {@code null}. */
			String category,
			/** ISO-8601. {@code start_time} 이 없으면 {@code null}. */
			String startsAt,
			String endsAt,
			Integer stayMinutes) {
	}
}
