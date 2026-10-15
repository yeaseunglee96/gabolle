package com.gabolle.backend.place.photo;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
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
 * 부르고, 여기서 Google 이 준 사진 주소로 302(<b>"주소를 저쪽으로 옮겼다"는 응답</b>)를 돌려준다 — 화면을
 * 고치지 않아도 되고, 사진은 우리 쪽 어디에도 저장되지 않는다. {@code <img>} 요청에는 인증 헤더가 안
 * 붙으므로 로그인 없이 열려 있다({@code SecurityConfig}). 장소 번호가 UUID 라 목록을 긁을 수 없고, 어차피
 * 공개 장소다.
 *
 * <p>Google 장소 번호가 없는 장소는 404 다 — 이 경로가 아무 장소에나 Google 을 부르지 않게 한다.
 *
 * <p><b>몇 번째 사진을 보일지</b>는 운영자가 {@code place.photo_file_page} 끝의 {@code &photo=N} 으로
 * 정한다(S15P21E201-2059, 적는 법은 {@link GooglePlacePhotoLink}). 이 경로는 요청을 받을 때마다 그 칸을
 * 읽는다 — 요청에는 번호를 받지 않으므로, 밖에서 번호를 바꿔 가며 Google 을 더 부르게 할 수 없다.
 */
@RestController
@RequestMapping("/api/v1/places")
@Profile({ "db", "dev" })
public class PlacePhotoController {

	private final PlaceRepository placeRepository;

	private final GooglePlacePhotoClient client;

	private final GooglePlacePhotoProperties properties;

	private final Clock clock;

	/**
	 * 받아 온 사진 주소. 열쇠는 우리 장소 번호가 아니라 <b>(Google 장소 번호, 사진 번호)</b> 다 — 받아 온
	 * 주소는 이 둘로만 정해지기 때문이다. 그래서 운영자가 번호를 바꾸면 캐시 시간을 기다리지 않고 바로 바뀐
	 * 사진이 나가고, 같은 Google 장소를 다른 번호로 고른 두 장소의 사진이 서로 섞이지 않는다.
	 */
	private final Map<CacheKey, Cached> cache = new ConcurrentHashMap<>();

	// 생성자가 둘이라 스프링이 고를 것을 적는다 — 안 적으면 기본 생성자를 찾다가 앱 전체가 안 뜬다.
	@Autowired
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
		// 사진 번호는 이 장소의 photo_file_page 에 있고 캐시 열쇠에 들어가야 하므로, 캐시보다 먼저 읽는다.
		// 장소 한 줄을 기본키로 읽을 뿐이다. 캐시가 막는 것은 DB 가 아니라 과금되는 Google 호출이다.
		Optional<GooglePlacePhotoLink.PhotoRef> ref = this.placeRepository.findById(placeId)
				.map(Place::getPhotoLicense)
				.flatMap((license) -> GooglePlacePhotoLink.photoRefOf(license.filePage()));
		if (ref.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		CacheKey key = new CacheKey(ref.get().googlePlaceId(), ref.get().photoIndex());
		Instant now = this.clock.instant();
		Cached hit = this.cache.get(key);
		if (hit != null && hit.expiresAt().isAfter(now)) {
			return redirect(hit.uri());
		}
		Optional<String> uri = this.client.photoUri(key.googlePlaceId(), key.photoIndex());
		if (uri.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		if (this.cache.size() >= this.properties.getCacheMaxEntries()) {
			this.cache.clear();
		}
		this.cache.put(key, new Cached(uri.get(), now.plus(this.properties.getCacheTtl())));
		return redirect(uri.get());
	}

	private ResponseEntity<Void> redirect(String uri) {
		// 브라우저·앱 캐시도 서버 캐시보다 짧게만 들고 있게 한다 — 받은 주소가 만료되기 전에 새로 묻도록.
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(URI.create(uri))
				.cacheControl(CacheControl.maxAge(this.properties.getCacheTtl()).cachePrivate())
				.build();
	}

	private record CacheKey(String googlePlaceId, int photoIndex) {
	}

	private record Cached(String uri, Instant expiresAt) {
	}
}
