package com.gabolle.backend.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 동백이(AI 대화)가 베트남어 사용자에게 베트남어로 답하게 — S15P21E201-2004. */
class AssistantLanguageVietnameseTest {

	@Test
	@DisplayName("🔴 Accept-Language 가 vi 면 답변 언어는 vi 다 — 전에는 ko 로 떨어져 한국어로 답했다")
	void vietnameseHeaderIsVietnamese() {
		assertThat(AssistantChatService.normalizeLanguage("vi")).isEqualTo("vi");
		assertThat(AssistantChatService.normalizeLanguage("vi-VN,en;q=0.8")).isEqualTo("vi");
	}
}
