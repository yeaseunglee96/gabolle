package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.service.RequestLanguage;

/** 장소 이름·주소는 베트남어 데이터가 없다 — vi 는 한국어가 아니라 영어로 물러선다(S15P21E201-2004). */
class RequestLanguageVietnameseTest {

	@Test
	@DisplayName("🔴 vi 요청은 영문 값이 있으면 영어로 답한다")
	void vietnameseFallsBackToEnglish() {
		assertThat(RequestLanguage.resolve("vi-VN", true)).isEqualTo("en");
		assertThat(RequestLanguage.resolve("vi", false)).isEqualTo("ko");
	}
}
