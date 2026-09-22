package com.gabolle.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.gabolle.backend.common.privacy.SensitiveDataInPayloadException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 빌드가 자바 소스를 UTF-8 로 읽는지 검사한다.
 *
 * <p>기대값을 유니코드 이스케이프로 적는 것이 핵심이다. 기대값도 한글로 적으면 컴파일러가 이 파일을
 * 틀린 인코딩으로 읽을 때 실제값과 기대값이 똑같이 깨져 비교가 그대로 통과한다.
 */
class SourceEncodingTest {

	private static final String EXPECTED_IN_TEST_SOURCE = "\uD14C\uC2A4\uD2B8 \uC18C\uC2A4 \uC778\uCF54\uB529";

	private static final String EXPECTED_IN_MAIN_SOURCE = "\uC77C\uBC18 \uB85C\uADF8\uC5D0 \uB123\uC744 \uC218 \uC5C6\uB294 \uAC12\uC774\uB2E4: ";

	private static final String EXPECTED_EM_DASH = " \u2014 ";

	@Test
	@DisplayName("테스트 소스의 한글 리터럴이 UTF-8 로 컴파일된다")
	void testSourceCompilesAsUtf8() {
		assertThat("테스트 소스 인코딩").isEqualTo(EXPECTED_IN_TEST_SOURCE);
	}

	@Test
	@DisplayName("main 소스의 한글 메시지가 UTF-8 로 컴파일된다")
	void mainSourceCompilesAsUtf8() {
		SensitiveDataInPayloadException exception =
				new SensitiveDataInPayloadException("feature_values.origin.lat", "좌표는 개인위치다");

		assertThat(exception.getMessage())
				.startsWith(EXPECTED_IN_MAIN_SOURCE)
				.contains(EXPECTED_EM_DASH);
	}
}
