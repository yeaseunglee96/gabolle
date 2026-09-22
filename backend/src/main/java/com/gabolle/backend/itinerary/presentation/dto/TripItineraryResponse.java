package com.gabolle.backend.itinerary.presentation.dto;

import java.util.List;

/**
 * 한 여행의 일정 목록 응답. 내 여행 목록에서 여행 하나를 눌렀을 때 "무엇을 열지" 를 답한다.
 * 일정의 내용은 담지 않는다 — 그건 {@link ItineraryDetailResponse} 가 한다.
 * 순서를 계약으로 약속하지 않는다. 지금 여행 하나에는 일정이 하나뿐이라 목록이 사실상 한
 * 줄이고, 여럿이 되는 날 "어느 것이 최신인가" 를 정하려면 일정에 만든 시각을 도메인까지 올려야
 * 한다. 화면이 순서에 기대면 그 날 조용히 틀린다 — 하나일 때 그 하나를 열고, 여럿이면
 * 사용자에게 고르게 하는 편이 맞다.
 *
 * @param role 요청자가 이 여행에서 가진 역할. 여는 화면이 편집 버튼을 켤지 미리 안다
 */
public record TripItineraryResponse(String tripId, String role, List<Entry> itineraries) {

	/**
	 * @param latestVersion 이 일정의 최신 판 번호. 편집 요청이 판 번호를 함께 보내야 하므로
	 *     목록 단계에서 미리 준다
	 */
	public record Entry(String itineraryId, int latestVersion) {
	}
}
