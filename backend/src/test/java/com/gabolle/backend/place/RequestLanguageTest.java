package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.service.RequestLanguage;

/**
 * 요청 언어 해석. 이 판정이 {@code /places/{id}} 와 {@code /places/{id}/taxi-card} 두 응답에서
 * 함께 쓰이므로 여기서 한 번만 잰다.
 */
class RequestLanguageTest {

	@Test
	@DisplayName("영어를 요청하고 영문 값이 있으면 en 이다")
	void englishRequestedAndAvailable() {
		assertThat(RequestLanguage.resolve("en-US,en;q=0.9,ko;q=0.8", true)).isEqualTo("en");
		assertThat(RequestLanguage.resolve("en", true)).isEqualTo("en");
	}

	@Test
	@DisplayName("🔴 영어를 요청했는데 영문 값이 없으면 ko 로 되돌린다")
	void englishRequestedButUnavailableFallsBack() {
		assertThat(RequestLanguage.resolve("en-US,en;q=0.9", false)).isEqualTo("ko");
	}

	@Test
	@DisplayName("한국어를 요청하면 영문 값이 있어도 ko 다")
	void koreanRequestedStaysKorean() {
		assertThat(RequestLanguage.resolve("ko-KR,en;q=0.9", true)).isEqualTo("ko");
	}

	@Test
	@DisplayName("헤더가 없거나 비어 있으면 ko 다 — 기본값을 영어로 두지 않는다")
	void missingHeaderDefaultsToKorean() {
		assertThat(RequestLanguage.resolve(null, true)).isEqualTo("ko");
		assertThat(RequestLanguage.resolve("", true)).isEqualTo("ko");
		assertThat(RequestLanguage.resolve("   ", true)).isEqualTo("ko");
	}

	@Test
	@DisplayName("대소문자와 앞뒤 공백을 가리지 않는다")
	void caseAndWhitespaceInsensitive() {
		assertThat(RequestLanguage.resolve("  EN-GB , ko ", true)).isEqualTo("en");
	}

	@Test
	@DisplayName("q값 순위는 아직 안 본다 — 첫 태그만 본다. 의도한 단순화다")
	void qValuesAreNotRankedYet() {
		// "ko" 의 선호도가 더 낮지만 첫 태그라 한국어로 읽는다.
		assertThat(RequestLanguage.resolve("ko;q=0.1,en;q=0.9", true)).isEqualTo("ko");
	}

	@Test
	@DisplayName("알 수 없는 언어는 ko 다 — 지원하지 않는 언어로 답하지 않는다")
	void unsupportedLanguageFallsBackToKorean() {
		assertThat(RequestLanguage.resolve("ja-JP", true)).isEqualTo("ko");
		assertThat(RequestLanguage.resolve("zh-CN,zh;q=0.9", true)).isEqualTo("ko");
	}
}
