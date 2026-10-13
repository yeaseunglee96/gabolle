package com.gabolle.backend.auth.service;

import java.util.Locale;

/**
 * 프론트가 6개 언어(ko/en/ja/zh-Hans/zh-Hant/vi)를 지원한다 — {@code frontend/src/i18n/languages.ts}.
 * OAuth 업체가 주는 로케일 표기({@code en-US}·{@code ja_JP}·{@code zh-TW} 등)를 받아 그 여섯
 * 값({@code KO·EN·JA·VI·ZH-HANS·ZH-HANT}) 중 하나로 정규화한다. 중국어는 번체/간체 구분을 위해
 * 지역 코드(TW·HK·MO 는 번체)까지 본다. 구분자는 {@code _} 든 {@code -} 든 같게 본다 — 알림 문구 쪽
 * enum({@code PushCopy.Lang})이 {@code ZH_HANS} 로 쓰는 표기도 그대로 받으려는 것이다.
 * 모르는 값은 한국어로 떨어진다 — {@code app_user.language} 가 {@code NOT NULL} 이라 빈 값을
 * 그대로 저장할 수 없다.
 */
public final class LanguageNormalizer {

	/**
	 * 가입 요청({@code LocalSignupRequest}·{@code OAuthSignupRequest})의 {@code language} 가 받는 값 —
	 * 두 요청의 {@code @Pattern} 이 이 한 줄을 같이 쓴다.
	 *
	 * <p>🔴 전에는 두 요청이 각자 {@code "KO|EN"} 을 들고 있어서, 앱이 여섯 언어를 지원하게 된 뒤에도
	 * 가입 화면이 한국어·영어만 고르게 했다(S15P21E201-2053). 목록이 두 곳에 있으면 한쪽만 넓어진다.
	 *
	 * <p>중국어는 두 표기를 다 받는다. {@code ZH_HANS}·{@code ZH_HANT} 는 앱·알림 enum 의 표기이고,
	 * {@code ZH-HANS}·{@code ZH-HANT} 는 이 서버가 계정 응답({@code GET /auth/me})과 소셜 가입 미리채움에
	 * 내려 주는 표기다 — 자기가 내준 값을 되돌려 받으면 400 이 나는 계약은 만들지 않는다.
	 * 어느 쪽으로 오든 저장은 {@link #normalize} 가 {@code ZH-HANS}·{@code ZH-HANT} 로 맞춘다.
	 *
	 * <p>🔴 새 언어를 더하면 여기와 {@link #normalize} 를 같이 고친다. 여기만 고치면 가입은 통과하는데
	 * 저장은 한국어로 떨어진다 — {@code LanguageNormalizerSignupCodesTest} 가 그 어긋남을 잡는다.
	 */
	public static final String SIGNUP_LANGUAGE_REGEX = "KO|EN|JA|VI|ZH[_-]HANS|ZH[_-]HANT";

	private LanguageNormalizer() {
	}

	public static String normalize(String rawLanguage) {
		if (rawLanguage == null || rawLanguage.isBlank()) {
			return "KO";
		}
		String language = rawLanguage.trim().toLowerCase(Locale.ROOT).replace('_', '-');
		if (language.startsWith("en")) {
			return "EN";
		}
		if (language.startsWith("ja")) {
			return "JA";
		}
		// 여섯째 언어 — S15P21E201-2004. 빠지면 한국어로 저장돼 다음 로그인부터 화면이 한국어로 돌아간다.
		if (language.startsWith("vi")) {
			return "VI";
		}
		if (language.startsWith("zh")) {
			return (language.contains("hant") || language.contains("-tw") || language.contains("-hk")
					|| language.contains("-mo")) ? "ZH-HANT" : "ZH-HANS";
		}
		return "KO";
	}
}
