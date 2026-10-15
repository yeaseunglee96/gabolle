package com.gabolle.backend.place.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.Place.PhotoLicense;
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * S15P21E201-1832 — 사진은 저장하지 않고 요청 때 Google 에서 받아 302 로 넘긴다.
 * Google 장소 번호가 없는 장소는 Google 을 부르지 않는다(과금이 새지 않게).
 *
 * <p>S15P21E201-2059 — 몇 번째 사진인지는 {@code photo_file_page} 끝의 {@code &photo=N} 에서 읽어
 * 클라이언트에 넘긴다. 캐시 열쇠에 그 번호가 들어가 서로 다른 번호의 사진이 섞이지 않는다.
 */
class PlacePhotoControllerTest {

	private static final String GOOGLE_ID = "ChIJfYLMelSTaDURFlCRfk5W1PA";

	private static final String FIRST = "https://lh3.googleusercontent.com/p/first";

	private static final String THIRD = "https://lh3.googleusercontent.com/p/third";

	private static final String FIFTH = "https://lh3.googleusercontent.com/p/fifth";

	private final PlaceRepository repository = mock(PlaceRepository.class);

	private final GooglePlacePhotoClient client = mock(GooglePlacePhotoClient.class);

	private final GooglePlacePhotoProperties properties = new GooglePlacePhotoProperties();

	private final MutableClock clock = new MutableClock(Instant.parse("2026-09-29T00:00:00Z"));

	private final PlacePhotoController controller = new PlacePhotoController(this.repository, this.client,
			this.properties, this.clock);

	@Test
	void Google_장소_번호가_있으면_받은_사진_주소로_302_를_돌려준다() {
		UUID id = placeWith(new PhotoLicense(GooglePlacePhotoLink.LICENSE_NAME, null,
				GooglePlacePhotoLink.filePageOf(GOOGLE_ID)));
		when(this.client.photoUri(GOOGLE_ID, 0)).thenReturn(Optional.of("https://lh3.googleusercontent.com/p/x"));

		ResponseEntity<Void> res = this.controller.photo(id);

		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FOUND);
		assertThat(res.getHeaders().getLocation()).hasToString("https://lh3.googleusercontent.com/p/x");
	}

	@Test
	void Google_장소가_아닌_사진은_Google_을_부르지_않고_404() {
		UUID id = placeWith(new PhotoLicense("CC BY-SA 4.0", null, "https://commons.wikimedia.org/wiki/File:x.jpg"));

		assertThat(this.controller.photo(id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		verify(this.client, never()).photoUri(anyString(), anyInt());
	}

	@Test
	void 없는_장소는_404() {
		UUID id = UUID.randomUUID();
		when(this.repository.findById(id)).thenReturn(Optional.empty());

		assertThat(this.controller.photo(id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		verify(this.client, never()).photoUri(anyString(), anyInt());
	}

	@Test
	void 캐시_시간_안에는_Google_을_다시_부르지_않고_지나면_다시_묻는다() {
		UUID id = placeWith(new PhotoLicense(GooglePlacePhotoLink.LICENSE_NAME, null,
				GooglePlacePhotoLink.filePageOf(GOOGLE_ID)));
		when(this.client.photoUri(GOOGLE_ID, 0)).thenReturn(Optional.of("https://lh3.googleusercontent.com/p/x"));

		this.controller.photo(id);
		this.controller.photo(id);
		verify(this.client, times(1)).photoUri(GOOGLE_ID, 0);

		this.clock.advance(this.properties.getCacheTtl().plus(Duration.ofSeconds(1)));
		this.controller.photo(id);
		verify(this.client, times(2)).photoUri(GOOGLE_ID, 0);
	}

	@Test
	void 장소_번호가_아닌_글자는_주소로_조립하지_않는다() {
		assertThat(GooglePlacePhotoLink.googlePlaceIdOf(GooglePlacePhotoLink.PREFIX + "../../evil?x=1")).isEmpty();
		assertThat(GooglePlacePhotoLink.googlePlaceIdOf(GooglePlacePhotoLink.filePageOf(GOOGLE_ID))).contains(GOOGLE_ID);
		assertThatThrownBy(() -> GooglePlacePhotoLink.filePageOf("a/b")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void Google_이_사진을_못_주면_404_이고_그_실패를_캐시하지_않는다() {
		UUID id = placeWith(new PhotoLicense(GooglePlacePhotoLink.LICENSE_NAME, null,
				GooglePlacePhotoLink.filePageOf(GOOGLE_ID)));
		when(this.client.photoUri(GOOGLE_ID, 0)).thenReturn(Optional.empty());

		assertThat(this.controller.photo(id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(this.controller.photo(id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

		verify(this.client, times(2)).photoUri(GOOGLE_ID, 0);
	}

	// ---- S15P21E201-2059: 몇 번째 사진인가 ----

	@Test
	void 주소에_사진_번호가_있으면_그_번호를_Google_에_물어_302_를_돌려준다() {
		UUID id = placeWith(licenseOf(GooglePlacePhotoLink.filePageOf(GOOGLE_ID, 2)));
		when(this.client.photoUri(GOOGLE_ID, 0)).thenReturn(Optional.of(FIRST));
		when(this.client.photoUri(GOOGLE_ID, 2)).thenReturn(Optional.of(THIRD));

		ResponseEntity<Void> res = this.controller.photo(id);

		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FOUND);
		assertThat(res.getHeaders().getLocation()).hasToString(THIRD);
		verify(this.client, never()).photoUri(GOOGLE_ID, 0);
	}

	@Test
	void 사진_번호가_이상한_값이면_번호가_없을_때와_똑같이_첫_사진() {
		UUID id = placeWith(licenseOf(GooglePlacePhotoLink.PREFIX + GOOGLE_ID + "&photo=abc"));
		when(this.client.photoUri(GOOGLE_ID, 0)).thenReturn(Optional.of(FIRST));

		ResponseEntity<Void> res = this.controller.photo(id);

		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FOUND);
		assertThat(res.getHeaders().getLocation()).hasToString(FIRST);
	}

	@Test
	void 범위를_넘는_번호도_404_가_아니라_첫_사진() {
		UUID id = placeWith(licenseOf(GooglePlacePhotoLink.PREFIX + GOOGLE_ID + "&photo=42"));
		when(this.client.photoUri(GOOGLE_ID, 0)).thenReturn(Optional.of(FIRST));

		assertThat(this.controller.photo(id).getHeaders().getLocation()).hasToString(FIRST);
	}

	@Test
	void 같은_Google_장소를_다른_번호로_고른_두_장소의_사진은_서로_섞이지_않는다() {
		UUID first = placeWith(licenseOf(GooglePlacePhotoLink.filePageOf(GOOGLE_ID)));
		UUID third = placeWith(licenseOf(GooglePlacePhotoLink.filePageOf(GOOGLE_ID, 2)));
		when(this.client.photoUri(GOOGLE_ID, 0)).thenReturn(Optional.of(FIRST));
		when(this.client.photoUri(GOOGLE_ID, 2)).thenReturn(Optional.of(THIRD));

		// 번갈아 두 번씩 — 어느 쪽이 먼저 캐시에 들어가든 서로의 사진이 나가면 안 된다.
		assertThat(this.controller.photo(first).getHeaders().getLocation()).hasToString(FIRST);
		assertThat(this.controller.photo(third).getHeaders().getLocation()).hasToString(THIRD);
		assertThat(this.controller.photo(first).getHeaders().getLocation()).hasToString(FIRST);
		assertThat(this.controller.photo(third).getHeaders().getLocation()).hasToString(THIRD);

		// 각자 자기 열쇠로 한 번만 물었다 — 캐시는 번호별로 따로 작동한다.
		verify(this.client, times(1)).photoUri(GOOGLE_ID, 0);
		verify(this.client, times(1)).photoUri(GOOGLE_ID, 2);
	}

	@Test
	void 운영자가_번호를_바꾸면_캐시_시간을_기다리지_않고_바뀐_번호의_사진이_나간다() {
		Place place = mock(Place.class);
		UUID id = UUID.randomUUID();
		when(this.repository.findById(id)).thenReturn(Optional.of(place));
		when(place.getPhotoLicense()).thenReturn(licenseOf(GooglePlacePhotoLink.filePageOf(GOOGLE_ID)));
		when(this.client.photoUri(GOOGLE_ID, 0)).thenReturn(Optional.of(FIRST));
		when(this.client.photoUri(GOOGLE_ID, 4)).thenReturn(Optional.of(FIFTH));

		assertThat(this.controller.photo(id).getHeaders().getLocation()).hasToString(FIRST);

		// 운영자가 photo_file_page 를 &photo=4 로 고쳤다. 시계는 그대로다.
		when(place.getPhotoLicense()).thenReturn(licenseOf(GooglePlacePhotoLink.filePageOf(GOOGLE_ID, 4)));
		assertThat(this.controller.photo(id).getHeaders().getLocation()).hasToString(FIFTH);

		// 되돌리면 캐시에 남아 있는 첫 사진이 바로 나가고, Google 은 다시 부르지 않는다.
		when(place.getPhotoLicense()).thenReturn(licenseOf(GooglePlacePhotoLink.filePageOf(GOOGLE_ID)));
		assertThat(this.controller.photo(id).getHeaders().getLocation()).hasToString(FIRST);
		verify(this.client, times(1)).photoUri(GOOGLE_ID, 0);
		verify(this.client, times(1)).photoUri(GOOGLE_ID, 4);
	}

	@Test
	void 운영자가_Google_링크를_지우면_캐시에_남은_사진도_더_나가지_않는다() {
		Place place = mock(Place.class);
		UUID id = UUID.randomUUID();
		when(this.repository.findById(id)).thenReturn(Optional.of(place));
		when(place.getPhotoLicense()).thenReturn(licenseOf(GooglePlacePhotoLink.filePageOf(GOOGLE_ID, 1)));
		when(this.client.photoUri(GOOGLE_ID, 1)).thenReturn(Optional.of(THIRD));
		assertThat(this.controller.photo(id).getStatusCode()).isEqualTo(HttpStatus.FOUND);

		when(place.getPhotoLicense()).thenReturn(new PhotoLicense("CC BY-SA 4.0", null, "https://commons.wikimedia.org/wiki/File:x.jpg"));

		assertThat(this.controller.photo(id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	private static PhotoLicense licenseOf(String filePage) {
		return new PhotoLicense(GooglePlacePhotoLink.LICENSE_NAME, null, filePage);
	}

	private UUID placeWith(PhotoLicense license) {
		UUID id = UUID.randomUUID();
		Place place = mock(Place.class);
		when(place.getPhotoLicense()).thenReturn(license);
		when(this.repository.findById(id)).thenReturn(Optional.of(place));
		return id;
	}

	private static final class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration d) {
			this.now = this.now.plus(d);
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return this.now;
		}
	}
}
