package com.gabolle.backend.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

class PushCopyVietnameseTest {

	@Test
	@DisplayName("🔴 언어가 VI 인 사용자에게는 푸시가 베트남어로 간다 — 전에는 한국어였다")
	void vietnamesePush() {
		PushCopy.Lang lang = PushCopy.lang("VI");
		assertThat(lang).isEqualTo(PushCopy.Lang.VI);
		assertThat(PushCopy.createdTitle(lang)).isEqualTo("Lịch trình du lịch Busan của bạn đã sẵn sàng");
		assertThat(PushCopy.dates(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 10), lang)).contains("8").contains("10/10");
	}
}
