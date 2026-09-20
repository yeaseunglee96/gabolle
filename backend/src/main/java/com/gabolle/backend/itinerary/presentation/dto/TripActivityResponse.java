package com.gabolle.backend.itinerary.presentation.dto;

import java.util.List;

/**
 * 여행의 최근 변경 — 협업 화면의 "참여자와 최근 변경 내용".
 * 새 이력 표를 만들지 않았다. 판({@code itinerary_versions})이 곧 이력이다 — 누가·언제·무엇을
 * 했는지를 판마다 이미 갖고 있다. 감사 로그 표를 따로 두면 같은 사실이 두 곳에 적히고 둘이
 * 어긋날 자리가 생긴다.
 *
 * @param entries 최신이 먼저. {@code limit} 개까지
 * @param myRole 요청자의 역할 — 화면이 편집 버튼을 그릴지 정한다
 */
public record TripActivityResponse(String tripId, List<Entry> entries, int limit, String myRole) {

	/**
	 * @param operation {@code ItineraryVersion.Operation} 의 이름 그대로. 화면이 "고정함"·
	 *     "다시 계산함" 으로 번역한다
	 * @param actorName {@code app_user.display_name}. 사용자 행이 없으면(탈퇴) {@code null}
	 * @param isMe 요청자 자신의 변경인가
	 */
	public record Entry(
			String itineraryId,
			int version,
			String operation,
			String actorId,
			String actorName,
			boolean isMe,
			/** ISO-8601. */
			String at,
			Integer baseVersion,
			Integer revertedFromVersion,
			List<String> warningCodes) {
	}
}
