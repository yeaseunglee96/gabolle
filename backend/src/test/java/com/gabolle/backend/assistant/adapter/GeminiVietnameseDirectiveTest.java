package com.gabolle.backend.assistant.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GeminiVietnameseDirectiveTest {

	@Test
	@DisplayName("🔴 vi 요청에는 베트남어로 쓰라는 지시가 붙는다 — 빈 지시면 모델이 한국어로 답한다")
	void vietnameseDirectiveExists() {
		assertThat(GeminiAssistantAdapter.languageDirective("vi")).contains("베트남어");
	}
}
