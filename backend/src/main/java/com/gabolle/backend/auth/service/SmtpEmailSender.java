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
	 * {@code SimpleMailMessage} 가 아니라 {@code MimeMessage} 를 쓴다.
	 *
	 * <p>받는 사람에게 Gmail 주소가 그대로 보이지 않게 발신 이름을 붙이려면 From 헤더가
	 * {@code 가볼래 <주소>} 모양이어야 한다. {@code SimpleMailMessage} 로는 이름과 주소를
	 * 나눠 줄 자리가 없어서 주소만 나간다.
	 *
	 * <p>이름과 주소를 따로 받는 {@link MimeMessageHelper#setFrom(String, String)} 을 쓴다.
	 * 헤더에 실릴 때 한글 이름은 RFC 2047 로 인코딩되고, 그 결과는
	 * {@code SmtpEmailSenderFromHeaderTest} 가 못 박는다. 이름을 주소 문자열에 직접
	 * 이어 붙여도 JavaMail 이 파싱해서 같은 인코딩을 하기는 하지만, 이름에 쉼표나
	 * 꺾쇠가 들어오면 파싱 결과가 달라진다. 나눠 주는 쪽이 그 해석에 기대지 않는다.
	 *
	 * <h2>Reply-To 를 함께 넣는 이유 — S15P21E201-1017</h2>
	 * 2026-09-16 에 발신을 개인 Gmail SMTP 에서 중계 업체(Brevo)로 옮겼다. 그런데
	 * 받는 사람에게 보이는 주소는 우리가 넣은 값이 아니라
	 * {@code ...@12162776.brevosend.com} 이다 — 발신 도메인이 {@code gmail.com} 이라
	 * 업체가 DKIM 서명을 붙일 수 없어서 자기 도메인으로 바꿔 보낸다(발신자 인증을
	 * 마쳐도 그렇다. 업체 화면이 "Freemail domain is not recommended" 라고 적어 둔다).
	 *
	 * <p>그 상태로는 <b>사용자가 답장을 눌러도 아무도 안 보는 곳으로 간다.</b> 하필
	 * 기록 삭제 안내 메일 본문이 "문의가 있으면 이 메일에 답장해 주세요" 라고 적고
	 * 있어서, 답장 경로가 없는 것은 문구와 어긋나기까지 한다.
	 *
	 * <p>{@code Reply-To} 는 업체가 바꾸지 않으므로 여기에 실제 주소를 넣는다. From 이
	 * 무엇으로 바뀌든 답장은 {@code gabolle.mail.from} 으로 온다. 회사 도메인을 인증해
	 * From 자체를 되찾는 것이 원래 답이지만, 그것은 DNS 를 만질 수 있어야 한다.
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
