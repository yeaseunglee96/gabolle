package com.gabolle.backend.place.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
 */
class PlacePhotoControllerTest {

	private static final String GOOGLE_ID = "ChIJfYLMelSTaDURFlCRfk5W1PA";

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
		when(this.client.photoUri(GOOGLE_ID)).thenReturn(Optional.of("https://lh3.googleusercontent.com/p/x"));

		ResponseEntity<Void> res = this.controller.photo(id);

		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FOUND);
		assertThat(res.getHeaders().getLocation()).hasToString("https://lh3.googleusercontent.com/p/x");
	}

	@Test
	void Google_장소가_아닌_사진은_Google_을_부르지_않고_404() {
		UUID id = placeWith(new PhotoLicense("CC BY-SA 4.0", null, "https://commons.wikimedia.org/wiki/File:x.jpg"));

		assertThat(this.controller.photo(id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		verify(this.client, never()).photoUri(anyString());
	}

	@Test
	void 없는_장소는_404() {
		UUID id = UUID.randomUUID();
		when(this.repository.findById(id)).thenReturn(Optional.empty());

		assertThat(this.controller.photo(id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		verify(this.client, never()).photoUri(anyString());
	}

	@Test
	void 캐시_시간_안에는_Google_을_다시_부르지_않고_지나면_다시_묻는다() {
		UUID id = placeWith(new PhotoLicense(GooglePlacePhotoLink.LICENSE_NAME, null,
				GooglePlacePhotoLink.filePageOf(GOOGLE_ID)));
		when(this.client.photoUri(GOOGLE_ID)).thenReturn(Optional.of("https://lh3.googleusercontent.com/p/x"));

		this.controller.photo(id);
		this.controller.photo(id);
		verify(this.client, times(1)).photoUri(GOOGLE_ID);

		this.clock.advance(this.properties.getCacheTtl().plus(Duration.ofSeconds(1)));
		this.controller.photo(id);
		verify(this.client, times(2)).photoUri(GOOGLE_ID);
	}

	@Test
	void 장소_번호가_아닌_글자는_주소로_조립하지_않는다() {
		assertThat(GooglePlacePhotoLink.googlePlaceIdOf(GooglePlacePhotoLink.PREFIX + "../../evil?x=1")).isEmpty();
		assertThat(GooglePlacePhotoLink.googlePlaceIdOf(GooglePlacePhotoLink.filePageOf(GOOGLE_ID))).contains(GOOGLE_ID);
		assertThatThrownBy(() -> GooglePlacePhotoLink.filePageOf("a/b")).isInstanceOf(IllegalArgumentException.class);
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
