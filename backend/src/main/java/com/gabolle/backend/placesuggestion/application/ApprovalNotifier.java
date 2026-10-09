package com.gabolle.backend.placesuggestion.application;

import java.util.UUID;

/**
 * 제안이 승인됐다고 제안한 사람에게 알린다 — S15P21E201-2031.
 *
 * <p>구현은 폰 알림이다({@code placesuggestion.adapter.PushApprovalNotifier}). <b>던지지 않는다</b> — 승인은 이미
 * 커밋됐고, 알림이 안 갔다고 승인을 되돌릴 수는 없다.
 */
public interface ApprovalNotifier {

	/**
	 * @param language 받는 사람의 앱 언어({@code app_user.language}). 모르면 {@code null}
	 */
	void approved(UUID suggesterUserId, String language, String placeName, UUID placeId);
}
