package com.gabolle.backend.place.photo.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * S15P21E201-1954 — 실제 www.visitbusan.net 인증서 사슬로 사진을 받는지 본다.
 *
 * <p>바깥 네트워크가 필요해서 CI 에서는 건너뛴다. 손으로 돌릴 때:
 * {@code GABOLLE_LIVE_NETWORK=1 ./gradlew test --tests '*HttpImageFetcherLiveTest'}
 */
@EnabledIfEnvironmentVariable(named = "GABOLLE_LIVE_NETWORK", matches = "1")
class HttpImageFetcherLiveTest {

	private static final URI PHOTO = URI.create("https://www.visitbusan.net/uploadImgs/files/cntnts/20230612143356750_ttiel");

	@Test
	void plainJavaClientRejectsTheBrokenChain() {
		// 이 시험이 실패하면 상대가 중간 인증서를 보내기 시작했다는 뜻이다 — 그러면 추가 인증서를 걷어도 된다.
		HttpClient plain = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		assertThatThrownBy(() -> plain.send(HttpRequest.newBuilder(PHOTO).GET().build(),
				HttpResponse.BodyHandlers.discarding()))
				.hasRootCauseMessage("unable to find valid certification path to requested target");
	}

	@Test
	void proxyClientFetchesTheRealPhoto() {
		HttpImageFetcher fetcher = new HttpImageFetcher(new ImageProxyProperties());

		ImageFetcher.Response response = fetcher.get(PHOTO);

		assertThat(response.status()).isEqualTo(200);
		assertThat(ImageKind.sniff(response.body())).contains(ImageKind.JPEG);
	}
}
