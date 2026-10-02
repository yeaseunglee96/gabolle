package com.gabolle.backend.place.photo.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * 실제로 상대 서버를 부른다(S15P21E201-1954).
 *
 * <p><b>왜 신뢰 목록을 따로 만드나.</b> www.visitbusan.net 은 서버 인증서만 보내고 그 위의
 * 중간 인증서(Sectigo Public Server Authentication CA DV R36)를 빠뜨린다. 브라우저는 빠진 것을
 * 스스로 받아 오지만 안드로이드와 Java 는 받아 오지 않아 연결을 거절한다 — 그래서 앱에서 사진이
 * 회색 칸이었다. 이 클라이언트만 「기본 신뢰 목록 + 그 중간 인증서」를 믿게 한다.
 * JVM 전체의 신뢰 목록은 건드리지 않는다 — 다른 바깥 호출까지 넓어지면 안 된다.
 *
 * <p>호스트 이름 검사는 그대로 켜져 있다(HttpClient 기본). 중간 인증서를 믿는다고 아무 서버나
 * 믿는 것이 아니다 — 그 중간 인증서가 서명한, 이름이 맞는 인증서만 통과한다.
 */
public class HttpImageFetcher implements ImageFetcher {

	/** resources 안의 추가 신뢰 인증서들. 파일마다 출처·지문·만료일이 머리말에 있다. */
	static final List<String> EXTRA_TRUST = List.of("proxy-certs/sectigo-public-server-authentication-ca-dv-r36.pem");

	private final HttpClient client;

	private final ImageProxyProperties properties;

	public HttpImageFetcher(ImageProxyProperties properties) {
		this.properties = properties;
		this.client = HttpClient.newBuilder()
				.connectTimeout(properties.getConnectTimeout())
				.followRedirects(HttpClient.Redirect.NEVER)
				.sslContext(sslContext())
				.build();
	}

	@Override
	public Response get(URI uri) {
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(this.properties.getRequestTimeout())
				.header("User-Agent", "GabolleImageProxy/1.0 (+https://j15e201.p.ssafy.io)")
				.header("Accept", "image/*")
				.GET()
				.build();
		try {
			HttpResponse<InputStream> response = this.client.send(request, HttpResponse.BodyHandlers.ofInputStream());
			int status = response.statusCode();
			String location = response.headers().firstValue("Location").orElse(null);
			String contentType = response.headers().firstValue("Content-Type").orElse("");
			try (InputStream in = response.body()) {
				if (status < 200 || status >= 300) {
					return new Response(status, location, contentType, new byte[0], false);
				}
				long max = this.properties.getMaxBytes();
				long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
				if (declared > max) {
					return new Response(status, location, contentType, new byte[0], true);
				}
				// 상한 + 1 바이트까지만 읽는다 — 머리말이 거짓이어도 메모리를 다 먹지 않는다.
				byte[] body = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, max + 1));
				if (body.length > max) {
					return new Response(status, location, contentType, new byte[0], true);
				}
				return new Response(status, location, contentType, body, false);
			}
		}
		catch (IOException e) {
			throw new Unreachable("사진 서버에 닿지 못했다: " + uri.getHost(), e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new Unreachable("사진 받기가 중단됐다: " + uri.getHost(), e);
		}
	}

	static SSLContext sslContext() {
		try {
			X509ExtendedTrustManager system = trustManager(null);
			KeyStore extra = KeyStore.getInstance(KeyStore.getDefaultType());
			extra.load(null, null);
			CertificateFactory cf = CertificateFactory.getInstance("X.509");
			for (String path : EXTRA_TRUST) {
				try (InputStream in = HttpImageFetcher.class.getClassLoader().getResourceAsStream(path)) {
					if (in == null) {
						throw new IllegalStateException("추가 신뢰 인증서가 없다: " + path);
					}
					extra.setCertificateEntry(path, cf.generateCertificate(in));
				}
			}
			X509ExtendedTrustManager extraTm = trustManager(extra);
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, new TrustManager[] { new EitherTrustManager(system, extraTm) }, null);
			return context;
		}
		catch (GeneralSecurityException | IOException e) {
			throw new IllegalStateException("사진 대리 조회용 신뢰 목록을 만들지 못했다", e);
		}
	}

	private static X509ExtendedTrustManager trustManager(KeyStore keyStore) throws GeneralSecurityException {
		TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		tmf.init(keyStore);
		for (TrustManager tm : tmf.getTrustManagers()) {
			if (tm instanceof X509ExtendedTrustManager x) {
				return x;
			}
		}
		throw new IllegalStateException("X509 신뢰 관리자가 없다");
	}

	/**
	 * 둘 중 하나가 믿으면 믿는다. 먼저 기본 목록을 묻고, 거절하면 추가 목록을 묻는다.
	 * 둘 다 거절하면 처음 거절 이유를 그대로 던진다.
	 */
	static final class EitherTrustManager extends X509ExtendedTrustManager {

		private final X509ExtendedTrustManager first;

		private final X509ExtendedTrustManager second;

		EitherTrustManager(X509ExtendedTrustManager first, X509ExtendedTrustManager second) {
			this.first = first;
			this.second = second;
		}

		@FunctionalInterface
		private interface Check {

			void run(X509ExtendedTrustManager tm) throws CertificateException;
		}

		private void either(Check check) throws CertificateException {
			try {
				check.run(this.first);
			}
			catch (CertificateException e) {
				try {
					check.run(this.second);
				}
				catch (CertificateException ignored) {
					throw e;
				}
			}
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
				throws CertificateException {
			either((tm) -> tm.checkServerTrusted(chain, authType, socket));
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
				throws CertificateException {
			either((tm) -> tm.checkServerTrusted(chain, authType, engine));
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			either((tm) -> tm.checkServerTrusted(chain, authType));
		}

		// 이 클라이언트는 클라이언트 인증서를 받지 않는다 — 서버 쪽 검사만 쓴다.
		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
				throws CertificateException {
			this.first.checkClientTrusted(chain, authType, socket);
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
				throws CertificateException {
			this.first.checkClientTrusted(chain, authType, engine);
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			this.first.checkClientTrusted(chain, authType);
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			List<X509Certificate> all = new ArrayList<>(List.of(this.first.getAcceptedIssuers()));
			all.addAll(List.of(this.second.getAcceptedIssuers()));
			return all.toArray(new X509Certificate[0]);
		}
	}
}
