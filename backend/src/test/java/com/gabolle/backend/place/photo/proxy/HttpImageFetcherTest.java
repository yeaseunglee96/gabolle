package com.gabolle.backend.place.photo.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import javax.net.ssl.SSLContext;

import org.junit.jupiter.api.Test;

/** S15P21E201-1954 — 추가 신뢰 인증서가 실제로 실려 있고, 신뢰 목록이 만들어진다(네트워크 없음). */
class HttpImageFetcherTest {

	@Test
	void extraIntermediateIsBundledAndStillValidForAYear() throws Exception {
		for (String path : HttpImageFetcher.EXTRA_TRUST) {
			try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
				assertThat(in).as(path).isNotNull();
				X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
				assertThat(cert.getSubjectX500Principal().getName())
						.contains("Sectigo Public Server Authentication CA DV R36");
				// 만료가 가까워지면 이 시험이 먼저 알린다 — 그때 새 중간 인증서로 바꾼다.
				assertThat(cert.getNotAfter().toInstant()).isAfter(Instant.now().plus(365, ChronoUnit.DAYS));
			}
		}
	}

	@Test
	void sslContextBuilds() {
		SSLContext context = HttpImageFetcher.sslContext();
		assertThat(context.getProtocol()).isEqualTo("TLS");
	}
}
