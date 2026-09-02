package com.gabolle.backend.auth.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(prefix = "gabolle.mail", name = "enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

	private final JavaMailSender mailSender;
	private final String from;

	public SmtpEmailSender(JavaMailSender mailSender,
			@org.springframework.beans.factory.annotation.Value("${gabolle.mail.from:${spring.mail.username:}}") String from) {
		this.mailSender = mailSender;
		this.from = from;
	}

	@Override
	public void sendEmailVerification(String email, String verificationUrl) {
		send(email, "GABOLLE 이메일 인증", "아래 링크에서 이메일 인증을 완료해 주세요.\n" + verificationUrl);
	}

	@Override
	public void sendPasswordReset(String email, String resetUrl) {
		send(email, "GABOLLE 비밀번호 재설정", "아래 링크에서 비밀번호를 재설정해 주세요.\n" + resetUrl);
	}

	private void send(String email, String subject, String text) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(email);
		message.setSubject(subject);
		message.setText(text);
		mailSender.send(message);
	}
}
