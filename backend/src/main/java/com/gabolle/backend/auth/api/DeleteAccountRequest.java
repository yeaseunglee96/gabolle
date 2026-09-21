package com.gabolle.backend.auth.api;

import jakarta.validation.constraints.NotBlank;

/**
 * 계정 삭제 요청.
 *
 * <p>{@code confirmation} 은 사용자가 탈퇴 화면에서 직접 친 값이고
 * {@code AccountDeletionService.CONFIRMATION_PHRASE} 와 정확히 같아야 한다. {@code boolean} 한
 * 칸으로 받지 않는 것은 참·거짓이면 화면이 사용자를 거치지 않고 기본값으로 채워 보낼 수 있어서다.
 *
 * <p>비밀번호는 가진 사람만 보내면 되고, 보냈다면 반드시 맞아야 한다. 필수로 두면 비밀번호가
 * 없는 소셜 계정은 탈퇴할 수 없다.
 *
 * <p>{@code DELETE} 에 본문을 싣는 것은 확인 값이나 비밀번호를 질의 문자열에 넣으면 접속 기록과
 * 브라우저 이력에 그대로 남기 때문이다.
 */
public record DeleteAccountRequest(@NotBlank String confirmation, String password) {
}
