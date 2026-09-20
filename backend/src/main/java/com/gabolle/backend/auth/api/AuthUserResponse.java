package com.gabolle.backend.auth.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.UserStatus;
import java.util.UUID;

/**
 * 내 계정 정보 — {@code GET}·{@code PATCH /api/v1/auth/me} 와 로그인 응답이 함께 쓴다.
 *
 * <p>{@code avatarUrl} 이 {@code null} 이면 사진을 안 골랐다는 뜻이고 기본 그림은 화면이 그린다.
 * {@code coverUrl} 은 마이페이지 맨 위에 깔리는 사진이다.
 */
public record AuthUserResponse(UUID userId, String email, String displayName, String language, UserStatus status,
		String avatarUrl,

		/**
		 * 커버 사진 주소. 값이 없으면 키째 뺀다 — 빈 문자열이나 기본 사진 주소를 대신 보내지 않는다.
		 * 화면이 「사용자가 고른 사진」과 「기본 사진」을 구분해야 「기본 사진으로 되돌리기」 단추를
		 * 언제 보일지 정할 수 있기 때문이다.
		 *
		 * <p>{@code avatarUrl} 은 같은 상황에서 {@code null} 로 실려 나간다. 배포된 앱이 그것을
		 * 보고 있어 모양을 맞추지 않았다.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL) String coverUrl) {

	public static AuthUserResponse from(AppUser user, String email) {
		return new AuthUserResponse(user.getUserId(), email, user.getDisplayName(), user.getLanguage(), user.getStatus(),
				user.getAvatarUrl(), user.getCoverUrl());
	}
}
