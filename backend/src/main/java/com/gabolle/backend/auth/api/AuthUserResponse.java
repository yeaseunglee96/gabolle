package com.gabolle.backend.auth.api;

import com.fasterxml.jackson.annotation.JsonInclude;

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
 *
 * <p>{@code coverUrl} 은 S15P21E201-1297 에서 같은 이유로 그 뒤에 붙였다 — 마이페이지 맨 위에
 * 깔리는 사진이다.
 */
public record AuthUserResponse(UUID userId, String email, String displayName, String language, UserStatus status,
		String avatarUrl,

		/**
		 * 커버 사진 주소. 🔴 <b>없으면 키째 뺀다</b>({@code @JsonInclude(NON_NULL)}).
		 *
		 * <p>빈 문자열도 기본 사진 주소도 보내지 않는다. 보내면 화면이 <b>「사용자가 고른 사진」과
		 * 「기본 사진」을 구분할 수 없다</b> — 그러면 「기본 사진으로 되돌리기」 단추를 언제 보여줄지
		 * 정할 수 없다. <b>기본 사진을 고르는 것은 화면의 몫</b>이고, 서버는 「안 골랐다」만 말한다.
		 *
		 * <p>🔴 <b>{@code avatarUrl} 에는 같은 표시를 안 붙인다.</b> 그 칸은 이미 배포된 앱이
		 * {@code null} 이 오는 것을 보고 있고, 지금 키를 빼면 <b>이 티켓과 상관없는 화면이 바뀐다.</b>
		 * 두 칸의 모양이 다른 것은 실수가 아니라 여기 적힌 선택이다.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL) String coverUrl) {

	public static AuthUserResponse from(AppUser user, String email) {
		return new AuthUserResponse(user.getUserId(), email, user.getDisplayName(), user.getLanguage(), user.getStatus(),
				user.getAvatarUrl(), user.getCoverUrl());
	}
}
