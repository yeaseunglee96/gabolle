package com.gabolle.backend.place.photo.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** S15P21E201-1954 — 로그인 없이 열린 경로라 아무 주소나 받으면 남의 서버·내부망을 두드리는 문이 된다. */
class ImageUrlGuardTest {

	private static final String OK = "https://www.visitbusan.net/uploadImgs/files/cntnts/20230612143356750_ttiel";

	private static InetAddress ip(String s) {
		try {
			return InetAddress.getByName(s);
		}
		catch (UnknownHostException e) {
			throw new IllegalStateException(e);
		}
	}

	private static ImageUrlGuard guard(Map<String, String> dns) {
		return new ImageUrlGuard(List.of("www.visitbusan.net"), (host) -> {
			String a = dns.get(host);
			if (a == null) {
				throw new UnknownHostException(host);
			}
			return new InetAddress[] { ip(a) };
		});
	}

	private final ImageUrlGuard guard = guard(Map.of("www.visitbusan.net", "211.252.1.10"));

	@Test
	void allowedHostOverHttpsPasses() {
		assertThat(this.guard.check(OK)).isEqualTo(URI.create(OK));
		assertThat(this.guard.check("https://WWW.VisitBusan.net/a.jpg").getHost()).isEqualTo("WWW.VisitBusan.net");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"http://www.visitbusan.net/a.jpg", // https 만
			"https://evil.example.com/a.jpg", // 허락 안 한 호스트
			"https://visitbusan.net/a.jpg", // 정확히 같은 이름만 — 맨 도메인도 따로
			"https://img.www.visitbusan.net/a.jpg", // 하위 도메인
			"https://www.visitbusan.net.evil.com/a.jpg", // 뒤에 붙여 속이기
			"https://www.visitbusan.net@evil.com/a.jpg", // 사용자 정보로 속이기
			"https://user@www.visitbusan.net/a.jpg",
			"https://www.visitbusan.net:8443/a.jpg", // 다른 포트
			"file:///etc/passwd",
			"gopher://www.visitbusan.net/",
			"not a url",
			"" })
	void everythingElseIsRejected(String raw) {
		assertThatThrownBy(() -> this.guard.check(raw)).isInstanceOf(ImageUrlGuard.Rejected.class);
	}

	@Test
	void nullIsRejected() {
		assertThatThrownBy(() -> this.guard.check((String) null)).isInstanceOf(ImageUrlGuard.Rejected.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "127.0.0.1", "10.0.0.5", "172.16.3.4", "192.168.40.77", "169.254.169.254",
			"100.64.0.1", "0.0.0.0", "::1", "fd00::1", "fe80::1" })
	void allowedNameThatPointsInsideIsRejected(String internal) {
		// 허락한 이름이라도 DNS 가 내부망을 가리키면 막는다 — 169.254.169.254 는 클라우드 메타데이터 주소다.
		ImageUrlGuard rebound = guard(Map.of("www.visitbusan.net", internal));
		assertThatThrownBy(() -> rebound.check(OK)).isInstanceOf(ImageUrlGuard.Rejected.class);
	}

	@Test
	void unknownHostIsRejected() {
		ImageUrlGuard nothing = guard(Map.of());
		assertThatThrownBy(() -> nothing.check(OK)).isInstanceOf(ImageUrlGuard.Rejected.class);
	}
}
