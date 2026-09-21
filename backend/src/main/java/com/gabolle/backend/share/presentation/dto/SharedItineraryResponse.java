package com.gabolle.backend.share.presentation.dto;

import java.util.List;

/**
 * 읽기 전용 공유 조회 응답. 원본 응답에서 칸을 비운 것이 아니라 보낼 것만 담아 새로 만든
 * 모양이다. 출발지 좌표·연락처·예산·인원 칸은 이 record 에 아예 없다 — null 로 비우면 항목
 * 이름이 남아 이 서비스가 그 값을 갖고 있다는 사실이 드러나고, 나중에 채우는 실수도 쉽다.
 *
 * 칸을 더하면 SharedItineraryResponseWhitelistTest 가 빨개진다. 늘리는 것이 맞다면 그 허용
 * 목록도 함께 고친다.
 *
 * version 은 일정이 아직 없는 여행(PLANNING)이면 null 이다.
 * days 는 여행 기간의 날짜 전부이고, 항목이 0개인 날도 빈 items 로 들어간다.
 * notShared 는 공유되지 않는 항목의 이름이다 — 값이 아니라 이름이다.
 */
public record SharedItineraryResponse(
		String title,
		String startDate,
		String finishDate,
		Integer version,
		List<Day> days,
		String expiresAt,
		List<String> notShared) {

	/** 화면 고지문에 쓰는 고정 목록. 이 응답에 없는 것의 이름이다. */
	public static final List<String> NOT_SHARED = List.of("origin", "contact", "budget", "partySize");

	public record Day(String date, List<Item> items) {
	}

	public record Item(
			int sequence,
			String placeName,
			/** place.category. 값 목록이 아직 없어 자유 문자열이고, 없으면 null 이다. */
			String category,
			/** ISO-8601. start_time 이 없으면 null. */
			String startsAt,
			String endsAt,
			Integer stayMinutes) {
	}
}
