package com.gabolle.backend.auth.service;

import java.util.Locale;

public final class LanguageNormalizer {

	private LanguageNormalizer() {
	}

	public static String normalize(String rawLanguage) {
		if (rawLanguage == null || rawLanguage.isBlank()) {
			return "KO";
		}
		String language = rawLanguage.trim().toUpperCase(Locale.ROOT);
		if (language.startsWith("EN")) {
			return "EN";
		}
		if (language.startsWith("KO")) {
			return "KO";
		}
		return "KO";
	}
}
