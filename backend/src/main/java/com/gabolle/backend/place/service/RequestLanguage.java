package com.gabolle.backend.place.service;

import java.util.Locale;

/**
 * 요청 언어를 해석한다 — S15P21E201-430 (부분).
 *
 * <p>번역 업체가 정해지기 전까지 할 수 있는 것은 하나다. <b>이미 가지고 있는 값</b>
 * ({@code name_en}·{@code address_en})을 요청 언어에 따라 고르고, 없으면 한국어로 되돌린다.
 * 실제 번역은 이 클래스의 일이 아니다.
 *
 * <h2>🔴 되돌렸다는 사실을 응답에 실어야 한다</h2>
 * 영어를 요청했는데 영문 값이 없어 한국어로 답하는 경우가 흔하다. 그걸 알리지 않으면 화면은
 * 자기가 받은 것이 번역된 값인지 아닌지 구분할 수 없고, 사용자에게 "이 장소는 아직 영문 정보가
 * 없습니다" 라고 안내할 수도 없다. 그래서 {@link #resolve} 가 문자열을 돌려주고 응답의
 * {@code resolvedLanguage} 칸이 그 값을 그대로 싣는다.
 *
 * <h2>무엇을 기준으로 "영어로 답했다" 고 하는가</h2>
 * 응답마다 다르다. 장소 상세는 이름이 그 화면의 주된 값이라 {@code name_en} 을 보고, 택시
 * 목적지 카드는 기사에게 보여줄 <b>주소</b>가 주된 값이라 {@code address_en} 을 본다. 그래서 이
 * 클래스는 "무엇이 있는가" 를 판정하지 않고 <b>호출자가 판정해 넘긴다</b> — 여기서 한 필드를
 * 못박으면 응답 하나는 반드시 거짓말을 하게 된다.
 */
public final class RequestLanguage {

	/** 이 서비스가 답할 수 있는 언어. 지금은 둘이다. */
	public static final String ENGLISH = "en";

	public static final String KOREAN = "ko";

	private RequestLanguage() {
	}

	/**
	 * @param englishAvailable 이 응답이 실을 영문 값이 실제로 있는가. 호출자가 판정한다
	 *        (클래스 javadoc "무엇을 기준으로" 참고)
	 * @return {@code "en"} 또는 {@code "ko"}. 영어를 요청했어도 영문 값이 없으면 {@code "ko"} 다
	 */
	public static String resolve(String acceptLanguageHeader, boolean englishAvailable) {
		return (prefersEnglish(acceptLanguageHeader) && englishAvailable) ? ENGLISH : KOREAN;
	}

	/**
	 * {@code Accept-Language} 의 <b>첫 언어 태그만</b> 본다. q값(선호도 점수) 순위까지 따지지
	 * 않는다 — 이 티켓의 범위가 "있는 값을 고르는 것" 까지라서다.
	 *
	 * <p>예: {@code "en-US,en;q=0.9,ko;q=0.8"} 은 영어를 우선하고,
	 * {@code "ko-KR,en;q=0.9"} 는 한국어를 우선한다. 뒤쪽 태그의 q값이 더 높은
	 * ({@code "ko;q=0.1,en;q=0.9"}) 경우는 지금 한국어로 읽는다 — 의도한 단순화이고, q값을
	 * 실제로 쓰는 클라이언트가 나타나면 그때 고친다.
	 */
	public static boolean prefersEnglish(String acceptLanguageHeader) {
		if (acceptLanguageHeader == null || acceptLanguageHeader.isBlank()) {
			return false;
		}
		String primary = acceptLanguageHeader.split(",")[0].split(";")[0].trim();
		return primary.toLowerCase(Locale.ROOT).startsWith(ENGLISH);
	}
}
