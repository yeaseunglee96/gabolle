package com.gabolle.backend.place.photo.proxy;

import java.time.Duration;

import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공공 사진 대리 조회(S15P21E201-1954) — {@code GET /api/v1/images/proxy?url=…}.
 *
 * <p>화면이 {@code <img>} 로 부르므로 인증 머리가 안 붙는다 — 그래서 로그인 없이 열려 있다
 * ({@code SecurityConfig}). 대신 허락 호스트만 받고({@link ImageUrlGuard}), 받은 것은 사진일 때만
 * 내보낸다. 같은 주소는 저장해 둔 것을 내보내므로 상대 서버를 다시 두드리지 않는다.
 */
@RestController
@RequestMapping("/api/v1/images")
@Profile({ "db", "dev" })
public class ImageProxyController {

	/** 같은 주소의 사진은 바뀌지 않는다고 본다 — 기기·브라우저가 30일 들고 있게 한다. */
	static final Duration BROWSER_CACHE = Duration.ofDays(30);

	private final ImageProxyService service;

	public ImageProxyController(ImageProxyService service) {
		this.service = service;
	}

	@GetMapping("/proxy")
	public ResponseEntity<byte[]> proxy(@RequestParam(name = "url", required = false) String url,
			@RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
		ImageProxyService.Outcome outcome = this.service.load(url);
		if (outcome instanceof ImageProxyService.Outcome.Image image) {
			CacheControl cache = CacheControl.maxAge(BROWSER_CACHE).cachePublic().immutable();
			if (image.etag().equals(ifNoneMatch)) {
				return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(image.etag()).cacheControl(cache).build();
			}
			return ResponseEntity.ok()
					.contentType(MediaType.parseMediaType(image.kind().contentType()))
					.eTag(image.etag())
					.cacheControl(cache)
					.header("X-Content-Type-Options", "nosniff")
					.body(image.bytes());
		}
		if (outcome instanceof ImageProxyService.Outcome.Rejected) {
			return ResponseEntity.badRequest().build();
		}
		if (outcome instanceof ImageProxyService.Outcome.NotFound) {
			return ResponseEntity.notFound()
					.cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
					.build();
		}
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).cacheControl(CacheControl.noStore()).build();
	}
}
