package com.gabolle.backend.auth.api;

import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /api/v1/auth/me} 요청 바디 — 이름·언어를 부분 수정한다.
 *
 * <p>두 필드 모두 선택 항목이다. JSON 에 키를 아예 안 보내면 {@code null} 로 들어와
 * 그 필드는 바꾸지 않는다. 다만 {@code displayName} 을 빈 문자열로 보내는 것은 "지우기"
 * 로 취급하지 않고 거부한다 — 이름 없는 계정을 만들 수 있게 되기 때문이다.
 * {@code @Size(min = 1)} 은 Bean Validation 스펙상 {@code null} 은 건드리지 않고
 * 통과시키면서 빈 문자열만 최소 길이 위반으로 잡는 성질을 그대로 이용한 것이다.
 *
 * <p>🔴 이름 상한을 30자로 둔 이유: Jira 완료 기준이 "31자 이름은 거부된다" 다. 그런데
 * 가입({@link LocalSignupRequest})은 {@code @Size(max = 50)} 이고 소셜 가입
 * ({@code OAuthAccountService})도 50자로 잘라 저장한다 — 즉 이 API 가 배포되면
 * "가입 때는 50자까지 되던 이름이 수정에서는 31자부터 막히는" 상태가 된다. 완료
 * 기준이 30을 요구해서 이대로 두지만, 가입·수정의 상한을 맞추는 정리가 별도로 필요하다.
 *
 * <p>{@code language} 는 값 형식을 이 DTO 에서 강제하지 않는다 — 이미 있는
 * {@link com.gabolle.backend.auth.service.LanguageNormalizer} 가 대소문자·별칭까지
 * 정규화해서 {@code KO}/{@code EN}/{@code JA}/{@code ZH-HANS}/{@code ZH-HANT} 중 하나로
 * 받아들이므로, 여기서 {@code @Pattern} 으로 한 번 더 좁히면 정규화기가 허용하는 입력(예:
 * 소문자 {@code en})을 서비스 계층 전에 막아버리는 모순이 생긴다.
 *
 * <p>이메일 필드는 여기 없다. 이메일 변경은 이 티켓 범위 밖이고, 요청 DTO 에 필드를
 * 아예 두지 않으면 클라이언트가 이메일을 보내도 Jackson 이 알 수 없는 필드로 버린다.
 * "받아서 무시" 하는 코드를 서비스에 남기는 것보다 "애초에 받지 않는" 편이 안전하다.
 */
public record UpdateProfileRequest(
		@Size(min = 1, max = 30) String displayName,
		String language,
		@Size(max = 500) String avatarUrl,
		@Size(max = 500) String coverUrl) {

	/**
	 * 사진을 떼라는 요청인가 (S15P21E201-844).
	 *
	 * <p>🔴 이름과 사진은 "빈 값" 의 뜻이 반대다. 이름은 없는 계정을 만들 수 없어서 빈 문자열을
	 * 거부하지만, 사진은 <b>원래 없어도 되는 값</b>이라 떼는 길이 필요하다. 그런데 Jackson 은
	 * "키를 안 보냈다" 와 "null 을 보냈다" 를 똑같이 {@code null} 로 준다 — 그래서 {@code null}
	 * 하나로는 "안 바꾼다" 와 "뗀다" 를 가를 수 없다.
	 *
	 * <p>그래서 <b>빈 문자열이 떼기</b>다. 키를 안 보내면 그 값은 그대로 둔다. 저장되는 값 쪽은
	 * 여전히 {@code null} 하나뿐이라({@code AppUser.changeAvatarUrl}) 표에 빈 문자열이 남지 않는다.
	 */
	public boolean removesAvatar() {
		return avatarUrl != null && avatarUrl.isBlank();
	}

	/**
	 * 커버 사진을 떼라는 요청인가 (S15P21E201-1297). 시안의 「기본 사진으로 되돌리기」가 이것이다.
	 *
	 * <p>규칙은 {@link #removesAvatar} 와 <b>같다</b> — 빈 문자열이 떼기, 키를 안 보내면 그대로.
	 * 두 사진이 같은 화면에 나란히 있어서 규칙이 다르면 프런트가 둘을 다르게 다뤄야 하고, 그
	 * 차이는 어디에도 안 적힌 채 한쪽만 고쳐지는 날이 온다.
	 */
	public boolean removesCover() {
		return coverUrl != null && coverUrl.isBlank();
	}
}
