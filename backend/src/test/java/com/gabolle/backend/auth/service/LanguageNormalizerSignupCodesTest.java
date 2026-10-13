package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gabolle.backend.notification.application.PushCopy;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 가입 요청이 받는 언어 코드({@link LanguageNormalizer#SIGNUP_LANGUAGE_REGEX})와 정규화기를 한데 묶는다.
 *
 * <p>🔴 이 두 개가 어긋나면 사람 눈에는 아무 일도 안 일어난다. 정규식에 새 코드를 넣고 정규화기를 안 고치면
 * 가입은 통과하는데 저장은 조용히 한국어로 떨어지고, 반대로 정규화기만 고치면 가입이 400 으로 막힌다
 * (S15P21E201-2053 전에는 후자였다 — 앱은 여섯 언어, 가입 요청은 KO|EN).
 *
 * <p>세 번째로 알림 문구 선택({@link PushCopy#lang})까지 이어 본다. 계정 언어는 저장하고 끝이 아니라
 * 푸시 알림 문구를 고르는 기준이라, 저장된 값이 그 읽기 쪽에서 제 언어로 읽혀야 의미가 있다.
 */
class LanguageNormalizerSignupCodesTest {

	/** 요청의 {@code @Pattern} 과 같은 판정 — Bean Validation 은 전체 일치({@code matches})로 본다. */
	private static final Pattern SIGNUP = Pattern.compile(LanguageNormalizer.SIGNUP_LANGUAGE_REGEX);

	/** {@code app_user.language} 열 너비. 마이그레이션 V20260917300000 이 넓힌 값이다. */
	private static final int COLUMN_WIDTH = 10;

	@ParameterizedTest(name = "{0} 는 가입 요청이 받는다")
	@ValueSource(strings = { "KO", "EN", "JA", "ZH_HANS", "ZH_HANT", "VI", "ZH-HANS", "ZH-HANT" })
	void acceptsEverySupportedCode(String code) {
		assertThat(SIGNUP.matcher(code).matches()).isTrue();
	}

	@ParameterizedTest(name = "「{0}」 는 가입 요청이 거부한다")
	@ValueSource(strings = { "", " ", "FR", "ZH", "ZH_HK", "ZH_HANSX", "ZHHANS", "EN-US", "KO ", " KO", "KO|EN",
			"ko", "en", "ja", "vi", "zh_hans", "zh-Hans" })
	void rejectsEverythingElse(String code) {
		assertThat(SIGNUP.matcher(code).matches()).isFalse();
	}

	/**
	 * 받은 코드는 저장 전에 자기 언어로 정규화되고, 그 저장값은 열에 들어가며, 알림 쪽에서도 같은 언어로 읽힌다.
	 * 열은 {@code ZH-HANS} 처럼 두 글자를 넘는 값을 담아야 한다(전에는 VARCHAR(2) 라 INSERT 가 실패했다).
	 */
	@ParameterizedTest(name = "{0} → 저장 {1} → 알림 {2}")
	@CsvSource({
			"KO,      KO,      KO",
			"EN,      EN,      EN",
			"JA,      JA,      JA",
			"VI,      VI,      VI",
			"ZH_HANS, ZH-HANS, ZH_HANS",
			"ZH_HANT, ZH-HANT, ZH_HANT",
			"ZH-HANS, ZH-HANS, ZH_HANS",
			"ZH-HANT, ZH-HANT, ZH_HANT",
	})
	void everyAcceptedCodeIsStoredAsItsOwnLanguage(String requested, String stored, PushCopy.Lang push) {
		assertThat(SIGNUP.matcher(requested).matches()).isTrue();

		String normalized = LanguageNormalizer.normalize(requested);

		// 모르는 값이 한국어로 떨어지는 규칙에 걸려 엉뚱하게 저장되면 안 된다.
		assertThat(normalized).isEqualTo(stored);
		assertThat(normalized.length()).isLessThanOrEqualTo(COLUMN_WIDTH);
		// 저장값을 한 번 더 정규화해도 같다 — 프로필 수정 경로가 서버가 내준 값을 되돌려 받아도 안 바뀐다.
		assertThat(LanguageNormalizer.normalize(normalized)).isEqualTo(stored);
		assertThat(PushCopy.lang(normalized)).isEqualTo(push);
	}
}
