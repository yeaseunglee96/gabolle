package com.gabolle.backend.place.photo.proxy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gabolle.backend.story.storage.StoragePort;

/**
 * 허락한 공공 사진을 한 번 받아 저장해 두고 그다음부터는 저장한 것을 내보낸다(S15P21E201-1954).
 *
 * <p>저장 위치는 기록 사진과 같은 저장소({@link StoragePort})의 {@code proxy/} 아래다. 키는 원래 주소의
 * SHA-256 이라 같은 주소는 늘 같은 파일이고, 주소를 모르면 키를 만들 수 없다.
 *
 * <p>실패는 셋으로만 나간다 — {@link Outcome.Rejected}(400, 주소가 규칙에 안 맞음),
 * {@link Outcome.NotFound}(404, 상대에게 그 사진이 없음), {@link Outcome.Failed}(502, 닿지 못했거나
 * 사진이 아니거나 너무 큼). 사진 한 장 때문에 로그가 시끄러워지지 않게 경고 한 줄만 남긴다.
 */
public class ImageProxyService {

	private static final Logger log = LoggerFactory.getLogger(ImageProxyService.class);

	static final String KEY_PREFIX = "proxy/";

	private final ImageUrlGuard guard;

	private final ImageFetcher fetcher;

	/** 없을 수 있다(저장소 빈이 없는 시험용 축소 앱). 없으면 저장하지 않는다. */
	private final StoragePort storage;

	private final ImageProxyProperties properties;

	public ImageProxyService(ImageUrlGuard guard, ImageFetcher fetcher, StoragePort storage,
			ImageProxyProperties properties) {
		this.guard = guard;
		this.fetcher = fetcher;
		this.storage = storage;
		this.properties = properties;
	}

	public sealed interface Outcome {

		record Image(ImageKind kind, byte[] bytes, String etag) implements Outcome {
		}

		record Rejected(String reason) implements Outcome {
		}

		record NotFound() implements Outcome {
		}

		record Failed(String reason) implements Outcome {
		}
	}

	public Outcome load(String rawUrl) {
		URI uri;
		try {
			uri = this.guard.check(rawUrl);
		}
		catch (ImageUrlGuard.Rejected e) {
			return new Outcome.Rejected(e.getMessage());
		}
		String hash = sha256(uri.toString());
		String etag = "\"" + hash + "\"";
		Optional<Outcome.Image> stored = findStored(hash, etag);
		if (stored.isPresent()) {
			return stored.get();
		}
		return fetchAndStore(uri, hash, etag);
	}

	private Optional<Outcome.Image> findStored(String hash, String etag) {
		if (this.storage == null) {
			return Optional.empty();
		}
		for (ImageKind kind : ImageKind.values()) {
			try {
				Optional<StoragePort.StoredObject> hit = this.storage.get(key(hash, kind));
				if (hit.isPresent()) {
					return Optional.of(new Outcome.Image(kind, hit.get().bytes(), etag));
				}
			}
			catch (StoragePort.StorageException e) {
				// 저장소가 잠깐 안 되면 원래 서버에서 다시 받는다 — 화면에는 사진이 나가야 한다.
				log.warn("사진 대리 조회 저장소 읽기 실패 key={} cause={}", key(hash, kind), e.getClass().getSimpleName());
				return Optional.empty();
			}
		}
		return Optional.empty();
	}

	private Outcome fetchAndStore(URI start, String hash, String etag) {
		URI current = start;
		for (int hop = 0; hop <= this.properties.getMaxRedirects(); hop++) {
			ImageFetcher.Response response;
			try {
				response = this.fetcher.get(current);
			}
			catch (ImageFetcher.Unreachable e) {
				log.warn("사진 대리 조회 실패 host={} cause={}", current.getHost(),
						(e.getCause() == null) ? "-" : e.getCause().getClass().getSimpleName());
				return new Outcome.Failed("사진 서버에 닿지 못했다");
			}
			int status = response.status();
			if (status >= 300 && status < 400) {
				if (response.location() == null) {
					return new Outcome.Failed("리디렉트에 갈 곳이 없다");
				}
				try {
					current = this.guard.check(current.resolve(response.location()));
				}
				catch (IllegalArgumentException | ImageUrlGuard.Rejected e) {
					return new Outcome.Failed("허락하지 않은 곳으로 보냈다");
				}
				continue;
			}
			if (status == 404 || status == 410) {
				return new Outcome.NotFound();
			}
			if (status < 200 || status >= 300) {
				return new Outcome.Failed("사진 서버가 " + status + " 를 줬다");
			}
			if (response.tooLarge()) {
				return new Outcome.Failed("사진이 너무 크다");
			}
			if (!acceptableContentType(response.contentType())) {
				return new Outcome.Failed("사진이 아니다");
			}
			Optional<ImageKind> kind = ImageKind.sniff(response.body());
			if (kind.isEmpty()) {
				return new Outcome.Failed("사진이 아니다");
			}
			try {
				if (this.storage != null) {
					this.storage.put(key(hash, kind.get()), kind.get().contentType(), response.body());
				}
			}
			catch (StoragePort.StorageException e) {
				// 저장을 못 해도 이번 사진은 내보낸다. 다음 요청이 다시 받아 간다.
				log.warn("사진 대리 조회 저장 실패 cause={}", e.getClass().getSimpleName());
			}
			return new Outcome.Image(kind.get(), response.body(), etag);
		}
		return new Outcome.Failed("리디렉트가 너무 많다");
	}

	/**
	 * Content-Type 이 없거나 범용 바이너리이거나 image/* 일 때만 받는다. text/html 같은 것은
	 * 첫 바이트가 우연히 맞아도 사진으로 내보내지 않는다.
	 */
	static boolean acceptableContentType(String contentType) {
		String ct = (contentType == null) ? "" : contentType.trim().toLowerCase(Locale.ROOT);
		return ct.isEmpty() || ct.startsWith("image/") || ct.startsWith("application/octet-stream");
	}

	static String key(String hash, ImageKind kind) {
		return KEY_PREFIX + hash + "." + kind.extension();
	}

	static String sha256(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
