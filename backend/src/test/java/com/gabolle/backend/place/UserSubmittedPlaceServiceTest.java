package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.domain.CurationStatus;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.service.UserSubmittedPlaceService;

/**
 * 사용자가 고른 장소를 우리 표의 장소로 바꾸는 규칙 — S15P21E201-1426.
 *
 * <p>여기서 보는 것의 핵심은 <b>같은 장소를 두 행으로 만들지 않는 것</b>이다. 갈라지면
 * 「이 장소의 기록 모아보기」가 반씩 나뉘고, 그것은 화면에서 안 보인다 — 둘 다 그럴듯하게
 * 동작하기 때문이다.
 */
class UserSubmittedPlaceServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-23T03:00:00Z");

	private PlaceRepository places;

	private UserSubmittedPlaceService service;

	@BeforeEach
	void setUp() {
		this.places = mock(PlaceRepository.class);
		this.service = new UserSubmittedPlaceService(this.places, Clock.fixed(NOW, ZoneOffset.UTC));
		when(this.places.save(any())).thenAnswer(call -> call.getArgument(0));
		when(this.places.findBySourceTypeAndSourceId(any(), any())).thenReturn(Optional.empty());
	}

	private UserSubmittedPlaceService.Snapshot snapshot(String source, String externalId) {
		return new UserSubmittedPlaceService.Snapshot(source, externalId, "해운대해수욕장",
				"부산 해운대구 우동", 35.1585, 129.1598, "SEA_BEACH");
	}

	@Test
	@DisplayName("🔴 이미 있는 장소면 «그 행»을 쓴다 — 큐레이션 장소여도 그렇다")
	void anExistingPlaceIsReusedEvenWhenCurated() {
		// V20260916210000 이 넣어 둔 해운대해수욕장이 바로 이 모양이다 — KAKAO_LOCAL·7913306.
		Place curated = Place.imported(UUID.randomUUID(), "해운대해수욕장", "SEA_BEACH", "부산 해운대구 우동",
				35.1585, 129.1598, "KAKAO_LOCAL", "7913306",
				OffsetDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC)), null, "KAKAO_LOCAL_2026-09-15");
		when(this.places.findBySourceTypeAndSourceId("KAKAO_LOCAL", "7913306")).thenReturn(Optional.of(curated));

		Place resolved = this.service.findOrCreate(snapshot("KAKAO_LOCAL", "7913306"));

		assertThat(resolved).as("큐레이션 행을 두고 새로 만들면 같은 장소가 두 행이 된다").isSameAs(curated);
		assertThat(resolved.getCurationStatus()).as("남의 행 등급을 끌어내리지 않는다")
				.isEqualTo(CurationStatus.CURATED);
		verify(this.places, never()).save(any());
	}

	@Test
	@DisplayName("🔴 없으면 만들되 USER_SUBMITTED 다 — 아무도 안 본 값이라 추천에서 빠져야 한다")
	void aNewPlaceIsMarkedUserSubmitted() {
		Place created = this.service.findOrCreate(snapshot("KAKAO_LOCAL", "99999999"));

		assertThat(created.getCurationStatus()).isEqualTo(CurationStatus.USER_SUBMITTED);
		assertThat(created.getSourceType()).isEqualTo("KAKAO_LOCAL");
		assertThat(created.getSourceId()).isEqualTo("99999999");
		assertThat(created.getNameKo()).isEqualTo("해운대해수욕장");
		verify(this.places).save(any());
	}

	@Test
	@DisplayName("🔴 출처는 대문자로 맞춘다 — kakao_local 이 섞이면 같은 장소가 두 행이 된다")
	void theSourceIsUpperCased() {
		Place created = this.service.findOrCreate(snapshot("kakao_local", "7913306"));

		assertThat(created.getSourceType()).isEqualTo("KAKAO_LOCAL");
		verify(this.places).findBySourceTypeAndSourceId("KAKAO_LOCAL", "7913306");
	}

	@Test
	@DisplayName("같은 (출처, 식별자)면 place_id 가 같다 — 동시에 둘이 골라도 뒤엣것이 PK 에서 막힌다")
	void thePlaceIdIsDerivedFromTheSourcePair() {
		Place first = this.service.findOrCreate(snapshot("KAKAO_LOCAL", "12345"));
		Place second = this.service.findOrCreate(snapshot("KAKAO_LOCAL", "12345"));

		assertThat(first.getPlaceId()).isEqualTo(second.getPlaceId());
		assertThat(first.getPlaceId())
				.as("출처가 다르면 다른 장소다")
				.isNotEqualTo(this.service.findOrCreate(snapshot("INTERNAL_FALLBACK", "12345")).getPlaceId());
	}

	@Test
	@DisplayName("🔴 수집분 딱지(datasetVersion)를 지어내지 않는다 — 이 행은 어느 수집분에도 안 속한다")
	void noDatasetVersionIsInvented() {
		assertThat(this.service.findOrCreate(snapshot("KAKAO_LOCAL", "555")).getDatasetVersion()).isNull();
	}

	@Test
	@DisplayName("좌표는 둘 다 있거나 둘 다 없어야 한다 — 한쪽만 오면 ck_place_origin_pair 에 걸린다")
	void aHalfCoordinateIsRejected() {
		UserSubmittedPlaceService.Snapshot half = new UserSubmittedPlaceService.Snapshot(
				"KAKAO_LOCAL", "777", "어딘가", "부산", 35.1, null, null);

		assertThatThrownBy(() -> this.service.findOrCreate(half))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("lat");
		verify(this.places, never()).save(any());
	}

	@Test
	@DisplayName("이름 없는 장소는 안 만든다 — 화면에 빈 칩이 뜨고 name_ko 는 NOT NULL 이다")
	void aNamelessPlaceIsRejected() {
		UserSubmittedPlaceService.Snapshot nameless = new UserSubmittedPlaceService.Snapshot(
				"KAKAO_LOCAL", "777", "   ", "부산", null, null, null);

		assertThatThrownBy(() -> this.service.findOrCreate(nameless))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("name");
	}

	@Test
	@DisplayName("출처나 식별자가 비면 거절한다 — 짝이 없으면 같은 장소인지 판정할 수가 없다")
	void anIncompleteSourcePairIsRejected() {
		assertThatThrownBy(() -> this.service.findOrCreate(
				new UserSubmittedPlaceService.Snapshot(null, "777", "어딘가", null, null, null, null)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> this.service.findOrCreate(
				new UserSubmittedPlaceService.Snapshot("KAKAO_LOCAL", " ", "어딘가", null, null, null, null)))
				.isInstanceOf(IllegalArgumentException.class);
		verify(this.places, never()).save(any());
	}
}
