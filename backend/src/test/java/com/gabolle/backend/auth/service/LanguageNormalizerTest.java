package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LanguageNormalizerTest {

	@Test
	void convertsProviderLocalesToDatabaseLanguageCodes() {
		assertThat(LanguageNormalizer.normalize("en-US")).isEqualTo("EN");
		assertThat(LanguageNormalizer.normalize("ko_KR")).isEqualTo("KO");
		assertThat(LanguageNormalizer.normalize(null)).isEqualTo("KO");
	}

	@Test
	@DisplayName("🔴 프론트가 보내는 5개 언어(ko/en/ja/zh-Hans/zh-Hant)가 각각 정규화된다 — S15P21E201-1109")
	void convertsAllFiveSupportedLanguages() {
		assertThat(LanguageNormalizer.normalize("ja-JP")).isEqualTo("JA");
		assertThat(LanguageNormalizer.normalize("ja_JP")).isEqualTo("JA");
		assertThat(LanguageNormalizer.normalize("zh-CN")).isEqualTo("ZH-HANS");
		assertThat(LanguageNormalizer.normalize("zh-TW")).isEqualTo("ZH-HANT");
		assertThat(LanguageNormalizer.normalize("zh-HK")).isEqualTo("ZH-HANT");
		assertThat(LanguageNormalizer.normalize("zh-Hant-TW")).isEqualTo("ZH-HANT");
	}

	@Test
	@DisplayName("모르는 언어 값은 한국어로 떨어진다")
	void unknownLanguageFallsBackToKorean() {
		assertThat(LanguageNormalizer.normalize("fr-FR")).isEqualTo("KO");
	}
}
