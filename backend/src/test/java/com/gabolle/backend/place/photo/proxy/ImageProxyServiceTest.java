package com.gabolle.backend.place.photo.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gabolle.backend.story.storage.StoragePort;

/**
 * S15P21E201-1954 — 허락한 공공 사진만, 사진일 때만, 한 번만 받아 온다.
 * 상대 서버는 가짜로 바꿔 끼운다. 실제 www.visitbusan.net 은 {@link HttpImageFetcherLiveTest} 가 본다.
 */
class ImageProxyServiceTest {

	private static final String URL = "https://www.visitbusan.net/uploadImgs/files/cntnts/20230612143356750_ttiel";

	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F' };

	private static final byte[] HTML = "<html>not a photo</html>".getBytes();

	/** 차례로 응답하고 부른 주소를 적어 두는 가짜 서버. */
	static final class FakeFetcher implements ImageFetcher {

		final Deque<Response> responses = new ArrayDeque<>();

		final List<URI> calls = new ArrayList<>();

		@Override
		public Response get(URI uri) {
			this.calls.add(uri);
			Response next = this.responses.poll();
			if (next == null) {
				throw new Unreachable("응답이 더 없다", null);
			}
			return next;
		}
	}

	static final class MemoryStorage implements StoragePort {

		final Map<String, StoredObject> files = new HashMap<>();

		@Override
		public String put(String key, String contentType, byte[] bytes) {
			this.files.put(key, new StoredObject(contentType, bytes));
			return "/stored/" + key;
		}

		@Override
		public void delete(String key) {
			this.files.remove(key);
		}

		@Override
		public Optional<StoredObject> get(String key) {
			return Optional.ofNullable(this.files.get(key));
		}
	}

	private final FakeFetcher fetcher = new FakeFetcher();

	private final MemoryStorage storage = new MemoryStorage();

	private final ImageProxyProperties properties = new ImageProxyProperties();

	private final ImageProxyService service = new ImageProxyService(
			new ImageUrlGuard(this.properties.getAllowedHosts(), (h) -> new InetAddress[] { InetAddress.getByName("211.252.1.10") }),
			this.fetcher, this.storage, this.properties);

	private static ImageFetcher.Response ok(String contentType, byte[] body) {
		return new ImageFetcher.Response(200, null, contentType, body, false);
	}

	@Test
	void photoWithoutContentTypeIsServedAsJpegAndStored() {
		// www.visitbusan.net 은 사진에 Content-Type 을 안 붙인다 — 첫 바이트로 가린다.
		this.fetcher.responses.add(ok("", JPEG));

		ImageProxyService.Outcome outcome = this.service.load(URL);

		assertThat(outcome).isInstanceOf(ImageProxyService.Outcome.Image.class);
		ImageProxyService.Outcome.Image image = (ImageProxyService.Outcome.Image) outcome;
		assertThat(image.kind()).isEqualTo(ImageKind.JPEG);
		assertThat(image.bytes()).isEqualTo(JPEG);
		assertThat(this.storage.files).containsOnlyKeys(ImageProxyService.key(ImageProxyService.sha256(URL), ImageKind.JPEG));
	}

	@Test
	void secondRequestIsServedFromStorageWithoutCallingTheServer() {
		this.fetcher.responses.add(ok("image/jpeg", JPEG));
		ImageProxyService.Outcome first = this.service.load(URL);
		ImageProxyService.Outcome second = this.service.load(URL);

		assertThat(this.fetcher.calls).hasSize(1);
		assertThat(second).isInstanceOf(ImageProxyService.Outcome.Image.class);
		assertThat(((ImageProxyService.Outcome.Image) second).etag())
				.isEqualTo(((ImageProxyService.Outcome.Image) first).etag());
	}

	@Test
	void disallowedHostNeverReachesTheServer() {
		ImageProxyService.Outcome outcome = this.service.load("https://evil.example.com/a.jpg");

		assertThat(outcome).isInstanceOf(ImageProxyService.Outcome.Rejected.class);
		assertThat(this.fetcher.calls).isEmpty();
	}

	@Test
	void redirectToAnotherHostIsNotFollowed() {
		// 허락 호스트가 다른 곳으로 보내는 우회 — 따라가지 않는다.
		this.fetcher.responses.add(new ImageFetcher.Response(302, "https://evil.example.com/x.jpg", "", new byte[0], false));

		ImageProxyService.Outcome outcome = this.service.load(URL);

		assertThat(outcome).isInstanceOf(ImageProxyService.Outcome.Failed.class);
		assertThat(this.fetcher.calls).hasSize(1);
	}

	@Test
	void redirectWithinTheAllowedHostIsFollowed() {
		this.fetcher.responses.add(new ImageFetcher.Response(301, "/uploadImgs/other.jpg", "", new byte[0], false));
		this.fetcher.responses.add(ok("image/jpeg", JPEG));

		ImageProxyService.Outcome outcome = this.service.load(URL);

		assertThat(outcome).isInstanceOf(ImageProxyService.Outcome.Image.class);
		assertThat(this.fetcher.calls.get(1)).isEqualTo(URI.create("https://www.visitbusan.net/uploadImgs/other.jpg"));
	}

	@Test
	void endlessRedirectsStop() {
		for (int i = 0; i < 10; i++) {
			this.fetcher.responses.add(new ImageFetcher.Response(302, "/again", "", new byte[0], false));
		}

		assertThat(this.service.load(URL)).isInstanceOf(ImageProxyService.Outcome.Failed.class);
		assertThat(this.fetcher.calls).hasSize(this.properties.getMaxRedirects() + 1);
	}

	@Test
	void htmlIsNotServedEvenIfTheServerSaysImage() {
		this.fetcher.responses.add(ok("image/jpeg", HTML));

		assertThat(this.service.load(URL)).isInstanceOf(ImageProxyService.Outcome.Failed.class);
		assertThat(this.storage.files).isEmpty();
	}

	@Test
	void htmlContentTypeIsNotServedEvenIfBytesLookLikeJpeg() {
		this.fetcher.responses.add(ok("text/html; charset=utf-8", JPEG));

		assertThat(this.service.load(URL)).isInstanceOf(ImageProxyService.Outcome.Failed.class);
	}

	@Test
	void tooLargeIsAFailure() {
		this.fetcher.responses.add(new ImageFetcher.Response(200, null, "image/jpeg", new byte[0], true));

		assertThat(this.service.load(URL)).isInstanceOf(ImageProxyService.Outcome.Failed.class);
		assertThat(this.storage.files).isEmpty();
	}

	@Test
	void missingPhotoIsNotFound() {
		this.fetcher.responses.add(new ImageFetcher.Response(404, null, "text/html", new byte[0], false));

		assertThat(this.service.load(URL)).isInstanceOf(ImageProxyService.Outcome.NotFound.class);
	}

	@Test
	void unreachableServerIsAFailure() {
		// 응답을 하나도 넣지 않으면 가짜 서버가 「닿지 못했다」를 던진다.
		assertThat(this.service.load(URL)).isInstanceOf(ImageProxyService.Outcome.Failed.class);
	}

	@Test
	void worksWithoutStorage() {
		// 저장소 빈이 없는 축소 앱에서도 뜬다 — 저장 없이 매번 받아 넘긴다.
		ImageProxyService noStorage = new ImageProxyService(
				new ImageUrlGuard(this.properties.getAllowedHosts(), (h) -> new InetAddress[] { InetAddress.getByName("211.252.1.10") }),
				this.fetcher, null, this.properties);
		this.fetcher.responses.add(ok("", JPEG));
		this.fetcher.responses.add(ok("", JPEG));

		assertThat(noStorage.load(URL)).isInstanceOf(ImageProxyService.Outcome.Image.class);
		assertThat(noStorage.load(URL)).isInstanceOf(ImageProxyService.Outcome.Image.class);
		assertThat(this.fetcher.calls).hasSize(2);
	}
}
