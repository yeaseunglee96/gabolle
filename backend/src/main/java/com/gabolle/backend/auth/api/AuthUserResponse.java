package com.gabolle.backend.auth.api;

import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.UserStatus;
import java.util.UUID;

/**
 * 내 계정 정보 — {@code GET}·{@code PATCH /api/v1/auth/me} 와 로그인 응답이 함께 쓴다.
 *
 * <p>{@code avatarUrl} 은 S15P21E201-844 에서 끝에 붙였다. 칸을 <b>끝에</b> 더하는 것이 중요하다 —
 * 이 record 는 JSON 이름으로 직렬화되므로 순서가 계약은 아니지만, 배포된 앱이 읽는 이름들 사이에
 * 끼워 넣으면 diff 를 읽는 사람이 기존 칸이 바뀐 줄로 오해한다. 값이 {@code null} 이면 사진을
 * 안 골랐다는 뜻이고 화면은 기본 그림을 그린다.
 */
public record AuthUserResponse(UUID userId, String email, String displayName, String language, UserStatus status,
		String avatarUrl) {

	public static AuthUserResponse from(AppUser user, String email) {
		return new AuthUserResponse(user.getUserId(), email, user.getDisplayName(), user.getLanguage(), user.getStatus(),
				user.getAvatarUrl());
	}
}
