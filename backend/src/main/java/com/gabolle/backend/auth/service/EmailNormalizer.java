package com.gabolle.backend.auth.service;

import java.util.Locale;

/**
 * 이메일을 표에 넣기 전에도, 표에서 찾기 전에도 항상 같은 모양으로 만든다.
 *
 * <p>규칙이 한 곳에만 있어야 한다 — 넣을 때와 찾을 때가 갈라지면 대문자로 적은 사람의 계정을
 * 못 찾고, 로그인은 되는데 재설정만 안 되는 식으로 일부만 어긋난다.
 *
 * <p>{@code Locale.ROOT} 가 필요하다. 인자 없는 {@code toLowerCase()} 는 서버 기본 로케일을 쓰고,
 * 터키어 로케일에서는 {@code I} 가 점 없는 {@code ı} 로 내려가 배포 환경마다 값이 달라진다.
 */
public final class EmailNormalizer {

	private EmailNormalizer() {
	}

	/**
	 * @param email 사용자나 배포 설정이 적은 그대로의 이메일. {@code null} 은 부르는 쪽이 먼저 걸러 준다.
	 * @return 앞뒤 공백을 떼고 소문자로 내린 값
	 */
	public static String normalize(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
