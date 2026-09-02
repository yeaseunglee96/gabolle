package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
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
		try (AnnotationConfigApplicationContext context = emailContext("gabolle.mail.enabled=true")) {
			assertThat(context.getBeansOfType(EmailSender.class)).containsOnlyKeys("smtpEmailSender");
		}
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
