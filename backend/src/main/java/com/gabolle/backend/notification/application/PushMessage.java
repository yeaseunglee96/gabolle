package com.gabolle.backend.notification.application;

/**
 * 폰에 뜰 알림 한 통 — S15P21E201-1391.
 *
 * <p>받는 사람(토큰)은 담지 않는다. 같은 문구를 여러 기기에 보내므로
 * {@link PushSender#send} 가 토큰 목록과 이것을 따로 받는다.
 *
 * @param title 굵게 뜨는 줄. 잠금화면에서는 이것만 보일 때가 있다
 * @param body 그 아래 줄
 * @param href 누르면 갈 앱 안 주소 (예: {@code /trips/123/itinerary}).
 *     🔴 앱이 읽는 칸 이름이 {@code href} 다 — {@code frontend/src/notifications/pushToken.ts} 의
 *     {@code hrefFromNotification}. 티켓 본문에는 {@code itineraryId} 라고 적혀 있지만 앱 코드에는
 *     그것을 읽는 자리가 없다. 그래서 <b>앱을 기준으로 맞추고</b> {@code itineraryId} 도 함께 싣는다
 * @param itineraryId 눌러서 갈 일정. 여행 단위 알림(초대 수락·자격 변경)에는 없어서 {@code null} 이다
 */
public record PushMessage(String title, String body, String href, String itineraryId) {

	public PushMessage {
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("title 이 비어 있습니다.");
		}
		if (href == null || !href.startsWith("/")) {
			// 앱은 '/' 로 시작하지 않는 href 를 버린다(같은 파일의 hrefFromNotification).
			// 여기서 막지 않으면 알림은 뜨는데 눌러도 아무 일이 없다 — 가장 찾기 어려운 고장이다.
			throw new IllegalArgumentException("href 는 '/' 로 시작하는 앱 안 주소여야 합니다: " + href);
		}
	}
}
