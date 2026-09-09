package com.gabolle.backend.auth.api;

import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.UserStatus;
import java.util.UUID;

public record AuthUserResponse(UUID userId, String email, String displayName, String language, UserStatus status) {

	public static AuthUserResponse from(AppUser user, String email) {
		return new AuthUserResponse(user.getUserId(), email, user.getDisplayName(), user.getLanguage(), user.getStatus());
	}
}
