package com.gabolle.backend.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.event.presentation.EventIngestExceptionHandler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * 🔴 S15P21E201-1730 — 앱 이벤트를 거절하면 이유가 서버 기록에 남는다.
 *
 * <p>앱은 거절돼도 다시 보내지 않고 조용히 버린다. 전에는 서버도 아무것도 안 남겨서, 2026-09-26 운영의 400 6건이
 * 왜 거절됐는지 끝내 알 수 없었다. DB 도 스프링 컨텍스트도 필요 없다.
 */
class EventRejectionLoggingTest {

	private Logger logbackLogger;

	private ListAppender<ILoggingEvent> appender;

	private Level originalLevel;

	@BeforeEach
	void attach() {
		this.logbackLogger = (Logger) LoggerFactory.getLogger(EventIngestExceptionHandler.class);
		this.originalLevel = this.logbackLogger.getLevel();
		this.logbackLogger.setLevel(Level.INFO);
		this.appender = new ListAppender<>();
		this.appender.start();
		this.logbackLogger.addAppender(this.appender);
	}

	@AfterEach
	void detach() {
		this.logbackLogger.detachAppender(this.appender);
		this.logbackLogger.setLevel(this.originalLevel);
	}

	@Test
	@DisplayName("거절하면 400 을 주고, 그 이유를 WARN 으로 한 줄 남긴다")
	void aRejectedEventLeavesItsReasonInTheServerLog() {
		String reason = "occurredAt(2026-09-26T05:44:48Z) 이 수신 시각(2026-09-26T05:38:48Z) 보다 5분 넘게 뒤다";

		ResponseEntity<ApiResponse<Void>> response = new EventIngestExceptionHandler()
				.handleInvalidEvent(new IllegalArgumentException(reason));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		List<ILoggingEvent> warnings = this.appender.list.stream()
				.filter(event -> event.getLevel() == Level.WARN)
				.toList();
		assertThat(warnings).as("거절이 서버 기록에 안 남으면 운영에서 왜 안 쌓이는지 아무도 모른다").hasSize(1);
		assertThat(warnings.get(0).getFormattedMessage()).contains("EVENT_REJECTED").contains(reason);
	}
}
