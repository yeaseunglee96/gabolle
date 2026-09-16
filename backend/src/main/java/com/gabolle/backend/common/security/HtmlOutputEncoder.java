package com.gabolle.backend.common.security;

import org.owasp.encoder.Encode;

/**
 * 사용자 자유 입력을 응답에 실을 때 HTML/JS 로 해석될 수 있는 문자를 인코딩한다 —
 * S15P21E201-835.
 *
 * <h2>왜 저장 시점이 아니라 출력 시점인가</h2>
 * 저장할 때 인코딩하면 <b>원문이 사라진다</b> — 나중에 검색 색인이나 다른 형식(예: 알림 이메일의
 * 일반 텍스트 부분)으로 같은 값을 다시 써야 할 때, 이미 HTML 인코딩된 문자열을 원문으로 되돌릴
 * 방법이 없다. 그래서 도메인·DB 는 원문을 그대로 두고, <b>API 응답으로 나가는 자리</b>(DTO 를
 * 조립하는 지점)에서만 인코딩한다 — OWASP 가 "contextual output encoding" 이라고 부르는 것이다.
 *
 * <h2>왜 직접 치환하지 않고 OWASP Java Encoder 를 쓰는가</h2>
 * {@code <}·{@code >} 만 바꾸는 손짜기 치환은 속성 컨텍스트 탈출(예: {@code onerror=})이나
 * 유니코드 우회를 놓치기 쉽다. 이 프로젝트가 개인정보·민감정보 검사를 직접 짜지 않고 검증된
 * 라이브러리에 맡긴 것({@code SensitivePayloadGuard})과 같은 이유로, 이스케이프 규칙 자체를
 * 다시 만들지 않는다.
 *
 * <p>지금은 이 값이 JSON 문자열로 나간 뒤 <b>어딘가에서 HTML 로 렌더링될 가능성</b>에 대비한
 * 방어다(지금 화면 코드에는 그런 경로가 없다 — 이 클래스의 존재 이유는 "지금 뚫린다" 가 아니라
 * "나중에 그런 렌더링 경로가 생겨도 이 값이 원인이 되지 않는다" 다). 그래서 HTML 컨텍스트
 * 인코딩({@link Encode#forHtml(String)})을 쓴다 — JSON 문자열 자체의 이스케이프는 Jackson 이
 * 이미 한다.
 */
public final class HtmlOutputEncoder {

	private HtmlOutputEncoder() {
	}

	/**
	 * @return HTML 특수문자를 인코딩한 문자열. {@code null} 은 그대로 {@code null} 이다 —
	 *     "값이 없다" 를 "빈 문자열이다" 로 바꾸지 않는다
	 */
	public static String forHtml(String value) {
		return (value == null) ? null : Encode.forHtml(value);
	}
}
