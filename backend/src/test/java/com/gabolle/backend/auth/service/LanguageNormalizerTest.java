package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LanguageNormalizerTest {

	@Test
	void convertsProviderLocalesToDatabaseLanguageCodes() {
		assertThat(LanguageNormalizer.normalize("en-US")).isEqualTo("EN");
		assertThat(LanguageNormalizer.normalize("ko_KR")).isEqualTo("KO");
		assertThat(LanguageNormalizer.normalize(null)).isEqualTo("KO");
	}
}
