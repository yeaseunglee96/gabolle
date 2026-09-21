package com.gabolle.backend.common.security;

import org.owasp.encoder.Encode;

/**
 * 사용자 자유 입력을 응답에 실을 때 HTML/JS 로 해석될 수 있는 문자를 인코딩한다.
 *
 * <p>저장 시점이 아니라 출력 시점에 한다. 저장할 때 인코딩하면 원문이 사라져 검색 색인이나 일반
 * 텍스트 형식으로 같은 값을 다시 쓸 수 없다. 도메인·DB 는 원문을 그대로 두고 DTO 를 조립하는
 * 지점에서만 인코딩한다.
 *
 * <p>HTML 컨텍스트 인코딩이다 — JSON 문자열 자체의 이스케이프는 Jackson 이 이미 한다.
 */
public final class HtmlOutputEncoder {

	private HtmlOutputEncoder() {
	}

	/** {@code null} 은 그대로 {@code null} 이다 — "값이 없다" 를 "빈 문자열이다" 로 바꾸지 않는다. */
	public static String forHtml(String value) {
		return (value == null) ? null : Encode.forHtml(value);
	}
}
