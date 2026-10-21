package com.gabolle.backend.dish.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GmsDishDescriberVietnameseTest {

	@Test
	@DisplayName("🔴 음식 설명을 vi 로 부르면 베트남어로 쓰라고 지시한다 — 전에는 한국어로 떨어졌다")
	void vietnamese() {
		assertThat(GmsDishDescriber.languageNameFor("vi")).isEqualTo("베트남어");
	}
}
