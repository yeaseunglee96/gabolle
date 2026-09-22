package com.gabolle.backend.itinerary.domain;

/**
 * 일정에 새 판이 붙었다 — S15P21E201-1391.
 *
 * <p>같은 여행을 쓰는 다른 사람에게 알리기 위한 사건이다. 듣는 쪽은
 * {@code com.gabolle.backend.notification.application.TripPushNotifier} 하나뿐이고,
 * {@code AFTER_COMMIT} 에서만 받는다 — 되돌려진 편집으로 "수민님이 순서를 바꿨어요" 가 뜨면
 * 사람은 앱을 열어 보고 아무것도 안 바뀐 것을 본다.
 *
 * <p>여행 번호를 안 담는다. 내는 쪽 넷 가운데 셋은 여행을 손에 들고 있지 않고
 * ({@link ItineraryRepository#appendVersion} 만 부른다), 담으려고 한 번 더 읽으면
 * <b>듣는 사람이 없을 때도</b> 그 질의가 돈다. 받는 쪽은 어차피 참여자 목록을 읽어야 하므로
 * 거기서 함께 푼다.
 *
 * @param itineraryId 바뀐 일정
 * @param version 새로 생긴 판 번호
 * @param operation 무엇을 해서 생긴 판인가 — 알림 문구가 이것으로 갈린다
 * @param actorUserId 바꾼 사람. <b>이 사람에게는 안 보낸다</b> — 자기가 방금 누른 것이다.
 *     탈퇴 등으로 비어 있을 수 있다
 */
public record ItineraryChangedByMember(String itineraryId, int version, ItineraryVersion.Operation operation,
		String actorUserId) {
}
