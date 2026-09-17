package com.gabolle.backend.auth.service;

import java.util.Locale;

/**
 * 프론트가 5개 언어(ko/en/ja/zh-Hans/zh-Hant)를 지원한다 — {@code frontend/src/i18n/languages.ts}.
 * OAuth 업체가 주는 로케일 표기({@code en-US}·{@code ja_JP}·{@code zh-TW} 등)를 받아 그 다섯
 * 값 중 하나로 정규화한다. 중국어는 번체/간체 구분을 위해 지역 코드(TW·HK·MO 는 번체)까지 본다.
 * 모르는 값은 한국어로 떨어진다 — {@code app_user.language} 가 {@code NOT NULL} 이라 빈 값을
 * 그대로 저장할 수 없다.
 */
public final class LanguageNormalizer {

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
		if (language.startsWith("zh")) {
			return (language.contains("hant") || language.contains("-tw") || language.contains("-hk")
					|| language.contains("-mo")) ? "ZH-HANT" : "ZH-HANS";
		}
		return "KO";
	}
}
