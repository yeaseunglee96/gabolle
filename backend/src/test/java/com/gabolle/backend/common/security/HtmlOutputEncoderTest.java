package com.gabolle.backend.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S15P21E201-835 — ZAP 이 잡은 페이로드가 실제로 무해해지는지 본다.
 */
class HtmlOutputEncoderTest {

	@Test
	@DisplayName("🔴 ZAP 이 쓴 것과 같은 종류의 스크립트 태그가 실행 가능한 형태로 남지 않는다")
	void scriptTagIsNeutralized() {
		String encoded = HtmlOutputEncoder.forHtml("<script>alert(1)</script>");

		assertThat(encoded).doesNotContain("<script>");
		assertThat(encoded).contains("&lt;script&gt;");
	}

	@Test
	@DisplayName("null 은 null 그대로다 — 빈 문자열로 바꾸지 않는다")
	void nullStaysNull() {
		assertThat(HtmlOutputEncoder.forHtml(null)).isNull();
	}

	@Test
	@DisplayName("특수문자가 없는 평범한 글은 그대로 나온다")
	void plainTextIsUnchanged() {
		assertThat(HtmlOutputEncoder.forHtml("해운대에서 커피 한잔")).isEqualTo("해운대에서 커피 한잔");
	}

	@Test
	@DisplayName("속성 컨텍스트 탈출 시도(onerror=)도 인코딩된다")
	void attributeBreakoutIsEncoded() {
		String encoded = HtmlOutputEncoder.forHtml("<img src=x onerror=alert(1)>");

		assertThat(encoded).doesNotContain("<img");
	}
}
