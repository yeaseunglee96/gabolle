package com.gabolle.backend.notification.application;

import java.util.List;

/**
 * 알림을 실제로 내보내는 구멍 — S15P21E201-1391.
 *
 * <p>구현은 둘이다. 설정이 켜져 있으면 Expo 로 보내고
 * ({@code com.gabolle.backend.notification.adapter.ExpoPushSender}), 아니면 로그만 남긴다
 * ({@code LoggingPushSender}). 메일 쪽과 같은 짜임이다 — {@code EmailSender}.
 */
public interface PushSender {

	/**
	 * 같은 문구를 이 기기들에 보낸다.
	 *
	 * <p><b>던지지 않는다.</b> 부르는 자리가 이미 커밋된 뒤라 되돌릴 것이 없고, 알림이 안 갔다고
	 * 여행 편집을 실패로 만들 수는 없다. 실패는 구현이 로그로 남긴다.
	 *
	 * @param tokens 보낼 기기들. 비어 있으면 아무 일도 하지 않는다
	 * @return <b>이제 없는 기기의 토큰들.</b> 앱을 지웠거나 알림을 껐다. 부르는 쪽이 지운다 —
	 *     안 지우면 죽은 토큰이 쌓여 보낼 때마다 헛일을 하고, Expo 가 우리를 조인다.
	 *     지울 것이 없으면 빈 목록이다 (절대 {@code null} 이 아니다)
	 */
	List<String> send(List<String> tokens, PushMessage message);
}
