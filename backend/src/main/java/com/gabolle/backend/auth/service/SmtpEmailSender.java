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
		// gabolle.mail.enabled=true 인데 발신 계정이 비어 있으면 여기서 기동을 멈춘다.
		// 그대로 뜨면 가입은 201 을 주고 인증 메일만 조용히 안 나가서, 사용자는
		// 로그인 단계의 EMAIL_NOT_VERIFIED 로만 고장을 알게 된다.
		if (from == null || from.isBlank()) {
			throw new IllegalStateException(
					"gabolle.mail.enabled=true 인데 발신 주소가 없습니다. "
							+ "GABOLLE_MAIL_USERNAME 또는 GABOLLE_MAIL_FROM 을 주입하십시오.");
		}
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
