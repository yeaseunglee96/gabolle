package com.gabolle.backend.menuscan.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GmsMenuReaderVietnameseTest {

	@Test
	@DisplayName("🔴 메뉴판을 vi 로 읽으면 설명을 베트남어로 쓰라고 지시한다")
	void vietnamese() {
		assertThat(GmsMenuReader.languageNameFor("vi")).isEqualTo("베트남어");
	}
}
