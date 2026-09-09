package com.gabolle.backend.auth.service;

import java.util.Locale;

/**
 * 이메일을 표에 넣기 전에도, 표에서 찾기 전에도 항상 같은 모양으로 만든다.
 *
 * <p>🔴 규칙이 한 곳에만 있어야 하는 이유 — 넣을 때와 찾을 때가 갈라지면 같은 사람이 대문자로
 * 적었을 때 계정을 못 찾는다. 그런데 로그인은 되고 재설정만 안 되는 식으로 <b>일부만</b>
 * 어긋나므로 원인을 찾기 어렵다. 예전에는 같은 두 줄이 세 곳에 복사돼 있었고, 거기에 기동 시점
 * 관리자 지정(S15P21E201-225)이 네 번째 사본을 더할 상황이었다.
 *
 * <p>{@code Locale.ROOT} 를 주는 이유: 인자 없는 {@code toLowerCase()} 는 서버의 기본 로케일을
 * 쓰는데, 터키어 로케일에서는 {@code I} 가 점 없는 {@code ı} 로 내려간다. 그러면 같은 이메일이
 * 배포 환경마다 다른 값이 된다.
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
