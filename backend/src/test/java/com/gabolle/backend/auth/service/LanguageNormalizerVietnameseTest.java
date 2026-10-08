package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 여섯째 언어 베트남어(vi) — S15P21E201-2004. 모르는 값으로 떨어지면 한국어로 저장돼 다음 로그인부터 화면이 한국어로 돌아간다. */
class LanguageNormalizerVietnameseTest {

	@Test
	@DisplayName("🔴 vi · vi-VN 은 VI 로 저장된다 — 한국어로 떨어지지 않는다")
	void vietnameseIsKept() {
		assertThat(LanguageNormalizer.normalize("vi")).isEqualTo("VI");
		assertThat(LanguageNormalizer.normalize("vi-VN")).isEqualTo("VI");
		assertThat(LanguageNormalizer.normalize("vi_VN")).isEqualTo("VI");
	}
}
