package com.gabolle.backend.auth.api;

import jakarta.validation.constraints.NotBlank;

/**
 * 계정 삭제 요청 (S15P21E201-425, 확인 방식은 -837 에서 바뀌었다).
 *
 * <p>되돌릴 수 없는 작업이라 한 겹 더 확인한다. {@code confirmation} 은 사용자가 탈퇴 화면에서
 * 직접 친 값이고, {@code AccountDeletionService.CONFIRMATION_PHRASE} 와 정확히 같아야 한다.
 *
 * <h2>2026-09-11 — 비밀번호를 필수에서 선택으로 내렸다 (S15P21E201-837)</h2>
 *
 * 그전에는 비밀번호가 {@code @NotBlank} 였다. 그런데 <b>소셜로만 가입한 계정에는 비밀번호가
 * 없다</b> — 그 사람들은 본문을 어떻게 채워도 탈퇴할 수 없었다. 비우고 보내면 400, 아무 값이나
 * 넣어 보내면 409 였다.
 *
 * <p>그래서 확인 수단을 비밀번호가 아니라 확인 값으로 옮겼다. 비밀번호는 <b>가진 사람만</b>
 * 보내면 되고, 보냈다면 반드시 맞아야 한다 — 틀린 것을 조용히 무시하지 않는다.
 *
 * <p>🔴 확인을 {@code boolean} 한 칸으로 받지 않는다. 참·거짓이면 화면이 사용자를 거치지 않고
 * 기본값으로 채워 보낼 수 있고, 그러면 확인이 아니라 형식이 된다. 사용자가 실제로 타이핑해야
 * 하는 값이어야 의미가 있다.
 *
 * <p>{@code DELETE} 에 본문을 싣는 것은 흔하지 않지만 티켓이 정한 계약이다. 확인 값이나
 * 비밀번호를 질의 문자열에 넣으면 접속 기록과 브라우저 이력에 그대로 남으므로 본문이 맞다.
 */
public record DeleteAccountRequest(@NotBlank String confirmation, String password) {
}
