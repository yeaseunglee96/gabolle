package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;

import com.gabolle.backend.place.adapter.InternalOriginSearchFallback;
import com.gabolle.backend.place.adapter.OriginSearchPort;
import com.gabolle.backend.place.adapter.OriginSearchProperties;
import com.gabolle.backend.place.api.OriginSearchResponse;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.service.OriginSearchService;

/**
 * 대체 목록 경로. 키가 없거나 호출이 실패하거나 provider 를 꺼도 오류가 아니라
 * {@code degraded=true} 로 내려가고, 예외가 밖으로 나가지 않는다. DB 없이 돈다.
 */
class OriginSearchFallbackTest {

	private final PlaceRepository placeRepository = mock(PlaceRepository.class);

	private final InternalOriginSearchFallback fallback = new InternalOriginSearchFallback(this.placeRepository);

	@Test
	@DisplayName("카카오 키가 없으면 대체 목록 + PROVIDER_KEY_MISSING, 카카오 포트는 불리지 않는다")
	void missingKeyFallsBackWithKeyMissingReason() {
		// fakePlace() 는 그 자체로 stubbing 이라 thenReturn(...) 안에서 만들면
		// Mockito 가 UnfinishedStubbingException 을 던진다. 먼저 만들어 두고 넘긴다.
		List<Place> stored = List.of(fakePlace("서면역", 35.1579, 129.0593));
		when(this.placeRepository.searchByName(anyString(), any(Limit.class))).thenReturn(stored);

		OriginSearchProperties properties = new OriginSearchProperties();
		properties.setKakaoRestApiKey("");
		OriginSearchService service = new OriginSearchService(neverCalledPort(), this.fallback, properties);

		OriginSearchResponse response = service.search("서면역", 10);

		assertThat(response.degraded()).isTrue();
		assertThat(response.degradedReason()).isEqualTo("PROVIDER_KEY_MISSING");
		assertThat(response.items()).hasSize(1);
		assertThat(response.items().get(0).source()).isEqualTo("INTERNAL_FALLBACK");
	}

	@Test
	@DisplayName("🔴 카카오 어댑터가 예외를 던지면 PROVIDER_UNAVAILABLE 로 내려가고 예외가 밖으로 나가지 않는다")
	void adapterFailureFallsBackWithoutPropagating() {
		List<Place> stored = List.of(fakePlace("서면역", 35.1579, 129.0593));
		when(this.placeRepository.searchByName(anyString(), any(Limit.class))).thenReturn(stored);

		OriginSearchProperties properties = new OriginSearchProperties();
		properties.setKakaoRestApiKey("real-looking-key");
		OriginSearchPort failingKakaoPort = (query, limit) -> {
			throw new IllegalStateException("kakao http 500");
		};
		OriginSearchService service = new OriginSearchService(failingKakaoPort, this.fallback, properties);

		OriginSearchResponse response = service.search("서면역", 10);

		assertThat(response.degraded()).isTrue();
		assertThat(response.degradedReason()).isEqualTo("PROVIDER_UNAVAILABLE");
		assertThat(response.items()).hasSize(1);
	}

	@Test
	@DisplayName("provider 가 NONE 이면 카카오 포트를 부르지 않고 PROVIDER_DISABLED 로 내려간다")
	void providerNoneIsDisabled() {
		when(this.placeRepository.searchByName(anyString(), any(Limit.class))).thenReturn(List.of());

		OriginSearchProperties properties = new OriginSearchProperties();
		properties.setProvider("NONE");
		properties.setKakaoRestApiKey("real-looking-key");
		OriginSearchService service = new OriginSearchService(neverCalledPort(), this.fallback, properties);

		OriginSearchResponse response = service.search("서면역", 10);

		assertThat(response.degraded()).isTrue();
		assertThat(response.degradedReason()).isEqualTo("PROVIDER_DISABLED");
		assertThat(response.items()).isEmpty();
	}

	private OriginSearchPort neverCalledPort() {
		return (query, limit) -> {
			throw new AssertionError("이 경로에서는 카카오 포트가 불리면 안 된다");
		};
	}

	private Place fakePlace(String nameKo, double lat, double lng) {
		Place place = mock(Place.class);
		when(place.getPlaceId()).thenReturn(UUID.randomUUID());
		when(place.getNameKo()).thenReturn(nameKo);
		when(place.getAddress()).thenReturn("부산 동구 어딘가");
		when(place.getLat()).thenReturn(lat);
		when(place.getLng()).thenReturn(lng);
		when(place.hasCoordinates()).thenReturn(true);
		return place;
	}
}
