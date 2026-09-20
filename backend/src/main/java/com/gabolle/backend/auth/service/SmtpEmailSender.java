package com.gabolle.backend.auth.service;

import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(prefix = "gabolle.mail", name = "enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

	/** 본문 줄바꿈. 문자열 안에 직접 쓰면 이 파일을 고치는 도구에 따라 실제 줄바꿈으로 바뀐다. */
	private static final String NEW_LINE = "\n";

	private final JavaMailSender mailSender;
	private final String from;
	private final String fromName;

	public SmtpEmailSender(JavaMailSender mailSender,
			@Value("${gabolle.mail.from:${spring.mail.username:}}") String from,
			// \uAC00\uBCFC\uB798 = 가볼래. application-dev.properties 와 같은 표기를 쓴다 (그 파일 주석 참고).
			@Value("${gabolle.mail.from-name:\uAC00\uBCFC\uB798}") String fromName) {
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
		this.fromName = (fromName == null || fromName.isBlank()) ? null : fromName;
	}

	@Override
	public void sendEmailVerification(String email, String verificationUrl) {
		send(email, "가볼래 이메일 인증", "아래 링크에서 이메일 인증을 완료해 주세요.\n" + verificationUrl);
	}

	@Override
	public void sendPasswordReset(String email, String resetUrl) {
		send(email, "가볼래 비밀번호 재설정", "아래 링크에서 비밀번호를 재설정해 주세요.\n" + resetUrl);
	}

	@Override
	public void sendStoryRemovedByModerator(String email, String excerpt) {
		String shown = (excerpt == null || excerpt.isBlank()) ? "(내용 없음)" : excerpt;
		send(email, "가볼래 기록이 삭제되었습니다",
				"신고 검토 결과 아래 기록이 삭제되었습니다." + NEW_LINE + NEW_LINE
						+ shown + NEW_LINE + NEW_LINE
						+ "딸린 사진도 함께 지워졌습니다. 문의가 있으면 이 메일에 답장해 주세요.");
	}

	/**
	 * {@code SimpleMailMessage} 가 아니라 {@code MimeMessage} 를 쓴다 — 이름과 주소를 나눠 줄
	 * 자리가 없으면 발신 이름 없이 주소만 나간다. 이름과 주소를 따로 받는
	 * {@link MimeMessageHelper#setFrom(String, String)} 을 쓰는 것은, 이름에 쉼표나 꺾쇠가
	 * 들어왔을 때 JavaMail 의 파싱 결과에 기대지 않기 위해서다.
	 *
	 * <p>{@code Reply-To} 를 함께 넣는다. 중계 업체는 발신 도메인에 DKIM 서명을 붙일 수 없으면
	 * From 을 자기 도메인으로 바꿔 보내므로, 그것이 없으면 사용자가 답장을 눌러도 아무도 안 보는
	 * 곳으로 간다. {@code Reply-To} 는 업체가 바꾸지 않는다.
	 */
	private void send(String email, String subject, String text) {
		MimeMessage message = mailSender.createMimeMessage();
		try {
			MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
			if (fromName == null) {
				helper.setFrom(from);
				helper.setReplyTo(from);
			}
			else {
				helper.setFrom(from, fromName);
				helper.setReplyTo(from, fromName);
			}
			helper.setTo(email);
			helper.setSubject(subject);
			helper.setText(text, false);
		}
		catch (jakarta.mail.MessagingException | UnsupportedEncodingException ex) {
			throw new MailPreparationException("메일을 만들지 못했습니다.", ex);
		}
		mailSender.send(message);
	}
}
