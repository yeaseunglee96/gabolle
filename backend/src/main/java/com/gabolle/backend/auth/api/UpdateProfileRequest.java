package com.gabolle.backend.auth.api;

import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /api/v1/auth/me} 요청 바디 — 이름·언어를 부분 수정한다.
 *
 * <p>모든 필드가 선택 항목이다. 키를 안 보내면 {@code null} 로 들어와 그 필드는 바꾸지 않는다.
 * {@code @Size(min = 1)} 은 {@code null} 을 통과시키고 빈 문자열만 잡는 성질을 쓴 것이다 —
 * 이름을 빈 문자열로 지우면 이름 없는 계정이 생긴다.
 *
 * <p>TODO 이름 상한이 가입(50자)과 어긋난다. 가입 때 쓴 이름을 수정에서 못 쓰는 상태다.
 *
 * <p>{@code language} 는 형식을 여기서 강제하지 않는다 —
 * {@link com.gabolle.backend.auth.service.LanguageNormalizer} 가 대소문자·별칭까지 정규화하므로,
 * {@code @Pattern} 으로 좁히면 정규화기가 허용하는 입력을 서비스 전에 막게 된다.
 *
 * <p>이메일 필드는 일부러 없다. DTO 에 두지 않으면 클라이언트가 보내도 Jackson 이 버린다.
 */
public record UpdateProfileRequest(
		@Size(min = 1, max = 30) String displayName,
		String language,
		@Size(max = 500) String avatarUrl,
		@Size(max = 500) String coverUrl) {

	/**
	 * 사진을 떼라는 요청인가. 사진에서는 빈 문자열이 "떼기" 이고 키를 안 보내면 그대로 둔다 —
	 * Jackson 이 "키를 안 보냈다" 와 "null 을 보냈다" 를 똑같이 {@code null} 로 주기 때문에
	 * {@code null} 하나로는 둘을 가를 수 없다. 저장되는 값은 여전히 {@code null} 뿐이다.
	 */
	public boolean removesAvatar() {
		return avatarUrl != null && avatarUrl.isBlank();
	}

	/**
	 * 커버 사진을 떼라는 요청인가. 규칙은 {@link #removesAvatar} 와 같다 —
	 * 빈 문자열이 떼기, 키를 안 보내면 그대로 둔다.
	 */
	public boolean removesCover() {
		return coverUrl != null && coverUrl.isBlank();
	}
}
