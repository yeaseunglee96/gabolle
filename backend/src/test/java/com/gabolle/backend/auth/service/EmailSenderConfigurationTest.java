package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mail.javamail.JavaMailSender;

class EmailSenderConfigurationTest {

	@Test
	void usesLoggingSenderWhenMailIsDisabledByDefault() {
		try (AnnotationConfigApplicationContext context = emailContext()) {
			assertThat(context.getBeansOfType(EmailSender.class)).containsOnlyKeys("loggingEmailSender");
		}
	}

	@Test
	void usesSmtpSenderWhenMailIsEnabled() {
		try (AnnotationConfigApplicationContext context = emailContext("gabolle.mail.enabled=true",
				"spring.mail.username=gabolle@example.test")) {
			assertThat(context.getBeansOfType(EmailSender.class)).containsOnlyKeys("smtpEmailSender");
		}
	}

	@Test
	void failsToStartWhenMailIsEnabledWithoutSenderAddress() {
		// 이 조합으로 뜨면 가입은 201 을 주고 인증 메일만 조용히 안 나간다. 사용자는
		// 로그인 단계의 EMAIL_NOT_VERIFIED 로만 고장을 알게 되므로 기동에서 막는다.
		assertThatThrownBy(() -> emailContext("gabolle.mail.enabled=true").close())
				.hasRootCauseInstanceOf(IllegalStateException.class)
				.rootCause()
				.hasMessageContaining("GABOLLE_MAIL_USERNAME");
	}

	private AnnotationConfigApplicationContext emailContext(String... properties) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		TestPropertyValues.of(properties).applyTo(context);
		context.registerBean(JavaMailSender.class, () -> mock(JavaMailSender.class));
		context.register(LoggingEmailSender.class, SmtpEmailSender.class);
		context.refresh();
		return context;
	}
}
