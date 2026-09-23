package com.gabolle.backend.itinerary.presentation.dto;

import java.util.List;

/**
 * {@code GET /api/v1/trips/{tripId}/recommendations} — 여행 전체를 통째로 견주는 코스 안들
 * (S15P21E201-1454). 모양은 화면이 먼저 정해 두었다({@code frontend/src/plan/tripCourses.ts}).
 *
 * <p>아직 추천이 끝나지 않은 여행은 빈 목록이다. 화면은 그때 「보여 드릴 코스가 없어요」를 그린다.
 */
public record TripCoursesResponse(List<Course> courses) {

	/**
	 * @param title 비워 보낸다. 화면이 사용자 언어로 「코스 A」를 붙인다 — 서버가 한국어 문장을 넣으면
	 *     다른 언어 화면에 그대로 새어 나간다
	 * @param status 언제나 {@code ESTIMATED} — 시각과 이동 시간이 어림값이 섞인 계산이다
	 * @param itineraryId 고르면 열릴 일정. 2안·3안은 아직 고르지 않았으면 {@code null} 이고, 그때
	 *     화면은 {@code POST .../course} 로 만든 뒤 연다
	 * @param preview {@code itineraryId} 가 없는 안을 그릴 재료 — 일정 조회({@code GET /itineraries/{id}})와
	 *     <b>같은 모양</b>이다. 화면은 코스를 그 모양으로만 그리므로(비용·이동 시간·지도), 이것이 없으면
	 *     2안·3안을 골라도 카드가 빈다. 일정이 있는 안은 {@code null} 이다
	 */
	public record Course(String id, String title, String tagline, List<Day> days, Summary summary, String status,
			String rationale, String itineraryId, ItineraryDetailResponse preview) {
	}

	/** @param day 1부터 센다. */
	public record Day(int day, List<Stop> stops) {
	}

	/**
	 * @param time 「09:30」. 시각을 못 정했으면 {@code null}
	 * @param lat 모르면 {@code null} 이지 {@code 0} 이 아니다 — 0 은 기니만 한가운데에 점을 찍는다
	 * @param photoUrl 싣지 않는다. 화면이 장소 번호로 사진을 따로 불러온다
	 */
	public record Stop(String placeId, String name, String time, String note, String photoUrl, String photoSource,
			Double lat, Double lng) {
	}

	/**
	 * 모르는 값은 {@code null} 이다. {@code 0} 으로 채우지 않는다 — 0 원은 「무료」, 0 분은 「붙어 있다」로
	 * 읽힌다.
	 *
	 * @param costKrw 값을 아는 곳만 더한 합. 그래서 언제나 「적어도 이만큼」이다
	 */
	public record Summary(Integer places, Integer moveMin, Double walkKm, Integer costKrw) {
	}

	/** {@code POST /api/v1/trips/{tripId}/course} 의 본문. */
	public record ChooseRequest(String courseId) {
	}

	/** 고른 안의 일정. 이미 있던 일정이면 그것을 돌려준다. */
	public record Chosen(String itineraryId) {
	}
}
