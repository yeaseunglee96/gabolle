package com.gabolle.backend.tools.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TranslationDirectionVietnameseTest {

	@Test
	@DisplayName("🔴 현장 통역에 베트남어 방향이 있다 — 앱이 VI_TO_KO 를 보내면 400 이 나지 않는다")
	void vietnameseDirections() {
		assertThat(TranslationDirection.valueOf("VI_TO_KO").source()).isEqualTo(TranslationDirection.Language.VIETNAMESE);
		assertThat(TranslationDirection.valueOf("KO_TO_VI").target()).isEqualTo(TranslationDirection.Language.VIETNAMESE);
	}
}
