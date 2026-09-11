package com.gabolle.backend.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Local fallback until SMTP credentials are configured. Never logs tokens in production. */
@Component
@ConditionalOnProperty(prefix = "gabolle.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

	@Override
	public void sendEmailVerification(String email, String verificationUrl) {
		log.info("Email verification requested for {}", email);
	}

	@Override
	public void sendPasswordReset(String email, String resetUrl) {
		log.info("Password reset requested for {}", email);
	}

	// 본문(excerpt)은 안 찍는다 — 기록 본문은 사용자가 쓴 글이고, 신고까지 받은 글이다.
	@Override
	public void sendStoryRemovedByModerator(String email, String excerpt) {
		log.info("Story removal notice requested for {}", email);
	}
}
