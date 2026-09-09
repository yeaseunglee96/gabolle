package com.gabolle.backend.auth.api;

import jakarta.validation.constraints.NotBlank;

/**
 * 계정 삭제 요청 (S15P21E201-425).
 *
 * <p>🔴 되돌릴 수 없는 작업이라 비밀번호를 다시 받는다. 로그인한 상태에서 자리를 비운 사이 남이
 * 눌러 계정을 지우는 것을 막는다. access token 만으로는 부족하다 — 그건 이미 로그인했다는 뜻일 뿐
 * 지금 그 사람이 맞다는 뜻이 아니다.
 *
 * <p>{@code DELETE} 에 본문을 싣는 것은 흔하지 않지만 티켓이 정한 계약이다. 비밀번호를 질의
 * 문자열에 넣으면 접속 기록과 브라우저 이력에 그대로 남으므로 본문이 맞다.
 */
public record DeleteAccountRequest(@NotBlank String password) {
}
