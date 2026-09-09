package com.gabolle.backend.place;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.gabolle.backend.place.api.PlaceDetailResponse;
import com.gabolle.backend.place.api.PlaceFeatureView;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlaceEvidenceStatus;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.repository.UserPlaceCodeMapRepository;
import com.gabolle.backend.place.service.ItineraryMembershipPort;
import com.gabolle.backend.place.service.PlaceDetailService;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 상세 응답이 <b>표현할 수 없는 조합</b>을 만들지 않는지 본다 — S15P21E201-749 후속.
 *
 * <h2>🔴 왜 통합 테스트가 아니라 목(mock) 인가</h2>
 *
 * <p>{@code place_feature.value} 는 {@code JSONB} 라서 PostgreSQL 이 쓰는 시점에 JSON 을
 * 검사한다. 깨진 값은 <b>DB 에 저장 자체가 안 된다</b> — 그래서 진짜 DB 를 쓰는 통합 테스트로는
 * 이 경로를 만들 수가 없다. 목으로 저장소를 대신해서 "DB 를 거치지 않고 만들어진
 * {@link PlaceFeature}" 를 넣는 것만이 이 가지를 지나는 유일한 방법이다.
 *
 * <p>🔴 <b>그러므로 이 테스트가 지키는 것은 "오늘 나는 버그" 가 아니라 계약이다.</b>
 * {@code VERIFIED} + {@code value:null} 은 {@link PlaceFeatureView} 가 정의한 다섯 상태
 * 어디에도 없다. 그 조합을 만들 수 있는 경로가 생기면 여기서 빨개진다.
 * 자세한 사정은 {@code PlaceDetailService.toView} 주석에 있다.
 */
class PlaceDetailFeatureParseSafetyTest {

	private static final UUID PLACE_ID = UUID.randomUUID();

	private PlaceRepository placeRepository;

	private PlaceFeatureRepository placeFeatureRepository;

	private PlaceDetailService service;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		this.placeRepository = mock(PlaceRepository.class);
		this.placeFeatureRepository = mock(PlaceFeatureRepository.class);
		UserPlaceCodeMapRepository codeMapRepository = mock(UserPlaceCodeMapRepository.class);
		ObjectProvider<ItineraryMembershipPort> membership = mock(ObjectProvider.class);
		ObjectMapper objectMapper = JsonMapper.builder().build();

		// 대조표를 비워 둔다 — NOT_COLLECTED 합성이 끼어들면 무엇을 재는지가 흐려진다.
		given(codeMapRepository.findAll()).willReturn(List.of());
		given(membership.getIfAvailable()).willReturn(null);

		this.service = new PlaceDetailService(this.placeRepository, this.placeFeatureRepository,
				codeMapRepository, membership, objectMapper);

		Place place = Place.imported(PLACE_ID, "해운대 맛집", "food", "부산 해운대구", 35.1595, 129.1604,
				"sbiz", "src-1", OffsetDateTime.now(), OffsetDateTime.now(), "v1");
		given(this.placeRepository.findById(PLACE_ID)).willReturn(Optional.of(place));
	}

	private void storedFeature(String value) {
		PlaceFeature feature = PlaceFeature.imported(UUID.randomUUID(), PLACE_ID, "ALLERGEN_TAG", "PEANUT",
				value, PlaceEvidenceStatus.VERIFIED, "manual", "src-1", OffsetDateTime.now(), "v1",
				OffsetDateTime.now());
		given(this.placeFeatureRepository.findByPlaceId(PLACE_ID)).willReturn(List.of(feature));
	}

	private PlaceFeatureView onlyFeature() {
		PlaceDetailResponse response = this.service.get(PLACE_ID, null);
		assertThat(response.features()).hasSize(1);
		return response.features().get(0);
	}

	@Test
	@DisplayName("정상 JSON 값은 상태도 값도 그대로 나간다")
	void wellFormedValueIsUnchanged() {
		storedFeature("false");

		PlaceFeatureView view = onlyFeature();

		assertThat(view.evidenceStatus()).isEqualTo("VERIFIED");
		assertThat(view.value().asBoolean()).isFalse();
	}

	@Test
	@DisplayName("🔴 값이 있는데 파싱에 실패하면 UNKNOWN 으로 낮춘다 — VERIFIED + value:null 로 내보내지 않는다")
	void unparsableValueIsDowngradedToUnknown() {
		storedFeature("{이건 JSON이 아니다");

		PlaceFeatureView view = onlyFeature();

		// 🔴 이 둘이 함께여야 한다. 상태만 낮추고 값을 남기거나, 값만 비우고 VERIFIED 를
		//    남기면 둘 다 PlaceFeatureView 가 정의하지 않은 조합이다.
		assertThat(view.evidenceStatus()).isEqualTo("UNKNOWN");
		assertThat(view.value()).isNull();
		// 행 자체는 남는다 — 버리면 "수집 대상에 안 들어갔다"(NOT_COLLECTED)로 읽혀 버린다.
		assertThat(view.featureType()).isEqualTo("ALLERGEN_TAG");
		assertThat(view.featureKey()).isEqualTo("PEANUT");
	}

	@Test
	@DisplayName("값이 원래 없는 행은 파싱 실패가 아니므로 상태를 낮추지 않는다")
	void absentValueKeepsItsStatus() {
		storedFeature(null);

		PlaceFeatureView view = onlyFeature();

		assertThat(view.evidenceStatus()).isEqualTo("VERIFIED");
		assertThat(view.value()).isNull();
	}
}
