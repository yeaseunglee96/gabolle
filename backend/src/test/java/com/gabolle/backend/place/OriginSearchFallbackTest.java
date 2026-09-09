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
 * 대체 목록 경로 단위 테스트 (S15P21E201-434) — {@code PlaceRepository} 는 Mockito 로 흉내 내고
 * DB 없이 돈다.
 *
 * <h2>키가 없거나 호출이 실패해도 200 이다 — 오류가 아니다</h2>
 *
 * <p>둘 다 {@code degraded=true} 로 내려가되 이유가 다르다. provider 를 껐을 때도 마찬가지다.
 * 세 경로 모두 예외가 컨트롤러 밖으로 나가지 않는 것이 완료 기준이다.
 */
class OriginSearchFallbackTest {

	private final PlaceRepository placeRepository = mock(PlaceRepository.class);

	private final InternalOriginSearchFallback fallback = new InternalOriginSearchFallback(this.placeRepository);

	@Test
	@DisplayName("카카오 키가 없으면 대체 목록 + PROVIDER_KEY_MISSING, 카카오 포트는 불리지 않는다")
	void missingKeyFallsBackWithKeyMissingReason() {
		// 🔴 가짜 Place 를 thenReturn(...) 안에서 만들면 안 된다. fakePlace() 자체가 mock 을
		//    stubbing 하는데, 그 시점에 바깥 when(...) 이 아직 안 끝나 있어서 Mockito 가
		//    UnfinishedStubbingException 을 던진다. 먼저 만들어 두고 넘긴다.
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
		// 🔴 가짜 Place 를 thenReturn(...) 안에서 만들면 안 된다. fakePlace() 자체가 mock 을
		//    stubbing 하는데, 그 시점에 바깥 when(...) 이 아직 안 끝나 있어서 Mockito 가
		//    UnfinishedStubbingException 을 던진다. 먼저 만들어 두고 넘긴다.
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
