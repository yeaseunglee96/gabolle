package com.gabolle.backend.place.service;

import java.util.Locale;

/**
 * 요청 언어를 해석한다. 번역은 하지 않고 이미 가진 값({@code name_en}·{@code address_en})을 고를
 * 뿐이며, 없으면 한국어로 되돌린다.
 *
 * <p>되돌렸다는 사실은 {@link #resolve} 가 돌려주는 값으로 응답의 {@code resolvedLanguage} 에
 * 실린다. 그래야 화면이 받은 값이 영문인지 아닌지 구분할 수 있다.
 *
 * <p>어느 필드를 기준으로 "영문이 있다" 고 할지는 응답마다 다르므로(상세는 이름, 택시 카드는
 * 주소) 이 클래스가 정하지 않고 호출자가 판정해 넘긴다.
 */
public final class RequestLanguage {

	/** 이 서비스가 답할 수 있는 언어는 둘뿐이다. */
	public static final String ENGLISH = "en";

	public static final String KOREAN = "ko";

	private RequestLanguage() {
	}

	/**
	 * @param englishAvailable 이 응답이 실을 영문 값이 실제로 있는가. 호출자가 판정한다
	 * @return {@code "en"} 또는 {@code "ko"}. 영어를 요청했어도 영문 값이 없으면 {@code "ko"} 다
	 */
	public static String resolve(String acceptLanguageHeader, boolean englishAvailable) {
		return (prefersEnglish(acceptLanguageHeader) && englishAvailable) ? ENGLISH : KOREAN;
	}

	/**
	 * {@code Accept-Language} 의 첫 언어 태그만 본다. q값(선호도 점수) 순위는 따지지 않으므로
	 * {@code "ko;q=0.1,en;q=0.9"} 는 한국어로 읽는다 — 의도한 단순화다.
	 */
	public static boolean prefersEnglish(String acceptLanguageHeader) {
		if (acceptLanguageHeader == null || acceptLanguageHeader.isBlank()) {
			return false;
		}
		String primary = acceptLanguageHeader.split(",")[0].split(";")[0].trim();
		return primary.toLowerCase(Locale.ROOT).startsWith(ENGLISH);
	}
}
