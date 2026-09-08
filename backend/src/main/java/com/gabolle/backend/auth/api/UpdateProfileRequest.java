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
 * 정규화해서 {@code KO}/{@code EN} 으로 받아들이므로, 여기서 {@code @Pattern} 으로
 * 한 번 더 좁히면 정규화기가 허용하는 입력(예: 소문자 {@code en})을 서비스 계층
 * 전에 막아버리는 모순이 생긴다.
 *
 * <p>이메일 필드는 여기 없다. 이메일 변경은 이 티켓 범위 밖이고, 요청 DTO 에 필드를
 * 아예 두지 않으면 클라이언트가 이메일을 보내도 Jackson 이 알 수 없는 필드로 버린다.
 * "받아서 무시" 하는 코드를 서비스에 남기는 것보다 "애초에 받지 않는" 편이 안전하다.
 */
public record UpdateProfileRequest(
		@Size(min = 1, max = 30) String displayName,
		String language) {
}
