package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * 받는 사람에게 보이는 발신자가 Gmail 주소가 아니라 "가볼래" 인지 본다.
 *
 * <p>메일 헤더는 ASCII 만 담을 수 있어서 한글 이름은 RFC 2047 로 인코딩돼야 한다.
 * 헤더 원문에 한글이 그대로 남아 있지 않은지까지 확인한다. 다만 이 검사가
 * "이름을 주소 문자열에 이어 붙였을 때" 를 잡지는 못한다 — 그 경우도 JavaMail 이
 * 파싱해서 같은 인코딩을 한다. 실제로 되돌려 보고 확인했다. 여기서 잡는 것은
 * 발신 이름이 아예 빠지는 회귀다(고치기 전 상태가 그랬다).
 *
 * <p>SMTP 로 붙지 않는다. {@code JavaMailSenderImpl} 은 접속 없이도
 * {@code createMimeMessage()} 를 만들어 주므로, {@code send} 를 가로채서 완성된
 * 메시지만 들여다본다.
 */
class SmtpEmailSenderFromHeaderTest {

	private static final String ADDRESS = "gabolle@example.test";

	private final List<MimeMessage> sent = new ArrayList<>();

	private SmtpEmailSender sender(String fromName) {
		JavaMailSenderImpl delegate = new JavaMailSenderImpl() {
			@Override
			public void send(MimeMessage... messages) {
				sent.addAll(List.of(messages));
			}
		};
		delegate.setDefaultEncoding("UTF-8");
		return new SmtpEmailSender(delegate, ADDRESS, fromName);
	}

	@Test
	void showsTheServiceNameInsteadOfTheRawAddress() throws Exception {
		sender("가볼래").sendEmailVerification("user@example.test", "https://example.test/verify?token=x");

		assertThat(sent).hasSize(1);
		InternetAddress from = (InternetAddress) sent.get(0).getFrom()[0];
		assertThat(from.getPersonal()).isEqualTo("가볼래");
		assertThat(from.getAddress()).isEqualTo(ADDRESS);
	}

	@Test
	void encodesTheKoreanNameSoTheHeaderStaysAscii() throws Exception {
		sender("가볼래").sendPasswordReset("user@example.test", "https://example.test/reset?token=x");

		String rawHeader = sent.get(0).getHeader("From")[0];
		assertThat(rawHeader).doesNotContain("가볼래");
		assertThat(rawHeader).contains("=?UTF-8?", ADDRESS);
	}

	@Test
	void fallsBackToTheAddressOnlyWhenNoNameIsConfigured() throws Exception {
		sender("  ").sendEmailVerification("user@example.test", "https://example.test/verify?token=x");

		InternetAddress from = (InternetAddress) sent.get(0).getFrom()[0];
		assertThat(from.getPersonal()).isNull();
		assertThat(from.getAddress()).isEqualTo(ADDRESS);
	}

	@Test
	void keepsTheKoreanSubjectReadable() throws Exception {
		sender("가볼래").sendEmailVerification("user@example.test", "https://example.test/verify?token=x");

		assertThat(sent.get(0).getSubject()).isEqualTo("가볼래 이메일 인증");
	}

	/**
	 * S15P21E201-1017 — 중계 업체가 From 을 자기 도메인으로 바꿔 보내므로, 답장이 갈 곳을
	 * 따로 실어야 한다. 이게 없으면 사용자가 답장을 눌러도 아무도 안 보는 주소로 간다.
	 */
	@Test
	void repliesGoBackToTheRealAddressEvenIfTheRelayRewritesFrom() throws Exception {
		sender("가볼래").sendEmailVerification("user@example.test", "https://example.test/verify?token=x");

		InternetAddress replyTo = (InternetAddress) sent.get(0).getReplyTo()[0];
		assertThat(replyTo.getAddress()).isEqualTo(ADDRESS);
		assertThat(replyTo.getPersonal()).isEqualTo("가볼래");
	}

	@Test
	void keepsTheReplyAddressWhenNoNameIsConfigured() throws Exception {
		sender("  ").sendPasswordReset("user@example.test", "https://example.test/reset?token=x");

		InternetAddress replyTo = (InternetAddress) sent.get(0).getReplyTo()[0];
		assertThat(replyTo.getAddress()).isEqualTo(ADDRESS);
		assertThat(replyTo.getPersonal()).isNull();
	}
}
