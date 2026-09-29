package com.gabolle.backend.place.photo;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * 장소의 Google 사진을 그때그때 받아 넘긴다(S15P21E201-1832).
 *
 * <p>{@code place.photo_url} 이 이 경로를 가리킨다. 화면은 다른 사진과 똑같이 {@code <img>} 로
 * 부르고, 여기서 Google 이 준 사진 주소로 302 를 돌려준다 — 화면을 고치지 않아도 되고, 사진은
 * 우리 쪽 어디에도 저장되지 않는다. {@code <img>} 요청에는 인증 헤더가 안 붙으므로 로그인 없이
 * 열려 있다({@code SecurityConfig}). 장소 번호가 UUID 라 목록을 긁을 수 없고, 어차피 공개 장소다.
 *
 * <p>Google 장소 번호가 없는 장소는 404 다 — 이 경로가 아무 장소에나 Google 을 부르지 않게 한다.
 */
@RestController
@RequestMapping("/api/v1/places")
@Profile({ "db", "dev" })
public class PlacePhotoController {

	private final PlaceRepository placeRepository;

	private final GooglePlacePhotoClient client;

	private final GooglePlacePhotoProperties properties;

	private final Clock clock;

	private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

	public PlacePhotoController(PlaceRepository placeRepository, GooglePlacePhotoClient client,
			GooglePlacePhotoProperties properties) {
		this(placeRepository, client, properties, Clock.systemUTC());
	}

	PlacePhotoController(PlaceRepository placeRepository, GooglePlacePhotoClient client,
			GooglePlacePhotoProperties properties, Clock clock) {
		this.placeRepository = placeRepository;
		this.client = client;
		this.properties = properties;
		this.clock = clock;
	}

	@GetMapping("/{placeId}/photo")
	public ResponseEntity<Void> photo(@PathVariable UUID placeId) {
		Instant now = this.clock.instant();
		Cached hit = this.cache.get(placeId);
		if (hit != null && hit.expiresAt().isAfter(now)) {
			return redirect(hit.uri());
		}
		Optional<String> googlePlaceId = this.placeRepository.findById(placeId)
				.map(Place::getPhotoLicense)
				.flatMap((license) -> GooglePlacePhotoLink.googlePlaceIdOf(license.filePage()));
		if (googlePlaceId.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		Optional<String> uri = this.client.photoUri(googlePlaceId.get());
		if (uri.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		if (this.cache.size() >= this.properties.getCacheMaxEntries()) {
			this.cache.clear();
		}
		this.cache.put(placeId, new Cached(uri.get(), now.plus(this.properties.getCacheTtl())));
		return redirect(uri.get());
	}

	private ResponseEntity<Void> redirect(String uri) {
		// 브라우저·앱 캐시도 서버 캐시보다 짧게만 들고 있게 한다 — 받은 주소가 만료되기 전에 새로 묻도록.
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(URI.create(uri))
				.cacheControl(CacheControl.maxAge(this.properties.getCacheTtl()).cachePrivate())
				.build();
	}

	private record Cached(String uri, Instant expiresAt) {
	}
}
