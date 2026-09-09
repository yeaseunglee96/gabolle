package com.gabolle.backend.place;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;

import com.gabolle.backend.place.api.PlaceCandidateRequest;
import com.gabolle.backend.place.api.PlaceCandidateResponse;
import com.gabolle.backend.place.config.PlaceProperties;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlaceEvidenceStatus;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.service.OpeningHoursFilterPort;
import com.gabolle.backend.place.service.PlaceCandidateQueryService;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * S15P21E201-749 — {@code place_feature.value} 파싱 실패가 안전 판정을 뒤집지 않는지 본다.
 *
 * <p>고지혁 님이 2026-09-07 발견: {@code readValue} 가 파싱 실패를 조용히 {@code null} 로
 * 바꾸면, {@code BaselineCandidateScorer.bucketFor} 가 "행이 없음" 과 "행은 있는데 못 읽음" 을
 * 구분하지 못해 안전 제약(알레르기·식단·이동 접근성) 판정이 방향에 따라 뒤집힐 수 있다.
 * 여기서는 이 서비스 계층에서 그 행이 응답에 아예 안 실리는지만 본다 — 이후 판정은
 * {@code EditorialPickBaselineProviderTest} 등이 이미 "행이 없으면 UNVERIFIED" 를 검증한다.
 */
class PlaceCandidateQueryServiceTest {

	private static final UUID PLACE_ID = UUID.randomUUID();
	private static final double CENTER_LAT = 35.1595;
	private static final double CENTER_LNG = 129.1604;

	private PlaceRepository placeRepository;
	private PlaceFeatureRepository placeFeatureRepository;
	private PlaceCandidateQueryService service;

	@BeforeEach
	void setUp() {
		this.placeRepository = mock(PlaceRepository.class);
		this.placeFeatureRepository = mock(PlaceFeatureRepository.class);
		OpeningHoursFilterPort openingHoursFilter = mock(OpeningHoursFilterPort.class);
		given(openingHoursFilter.isAvailable()).willReturn(true);
		ObjectMapper objectMapper = JsonMapper.builder().build();
		PlaceProperties properties = new PlaceProperties();

		this.service = new PlaceCandidateQueryService(this.placeRepository, this.placeFeatureRepository,
				openingHoursFilter, objectMapper, properties);

		Place place = Place.imported(PLACE_ID, "해운대 맛집", "food", "부산 해운대구", CENTER_LAT, CENTER_LNG,
				"sbiz", "src-1", OffsetDateTime.now(), OffsetDateTime.now(), "v1");
		given(this.placeRepository.findWithinBoundingBox(any(Double.class), any(Double.class), any(Double.class),
				any(Double.class), any(Limit.class))).willReturn(List.of(place));
	}

	private PlaceCandidateRequest request() {
		return new PlaceCandidateRequest(new PlaceCandidateRequest.Center(CENTER_LAT, CENTER_LNG), 1000,
				List.of(), List.of(), List.of(), null, 0, 200);
	}

	private PlaceFeature featureWithValue(String value) {
		return PlaceFeature.imported(UUID.randomUUID(), PLACE_ID, "ALLERGEN_TAG", "PEANUT", value,
				PlaceEvidenceStatus.VERIFIED, "manual", "src-1", OffsetDateTime.now(), "v1", OffsetDateTime.now());
	}

	@Test
	@DisplayName("정상 JSON 값은 그대로 응답에 실린다")
	void wellFormedValueIsIncluded() {
		given(this.placeFeatureRepository.findByPlaceIdIn(any())).willReturn(List.of(featureWithValue("false")));

		PlaceCandidateResponse response = this.service.findCandidates(request());

		assertThat(response.candidates()).hasSize(1);
		assertThat(response.candidates().get(0).features()).hasSize(1);
		assertThat(response.candidates().get(0).features().get(0).value().asBoolean()).isFalse();
	}

	@Test
	@DisplayName("🔴 값이 있는데 JSON 파싱에 실패한 행은 응답에서 아예 빠진다 — null 로 실리지 않는다")
	void unparsableValueIsDroppedNotNulled() {
		given(this.placeFeatureRepository.findByPlaceIdIn(any()))
				.willReturn(List.of(featureWithValue("{이건 JSON이 아니다")));

		PlaceCandidateResponse response = this.service.findCandidates(request());

		assertThat(response.candidates()).hasSize(1);
		// 🔴 빈 목록이어야 한다 — value:null 인 행이 실리면 "행이 있는데 모른다" 와
		// "행이 아예 없다" 를 호출자가 구분할 수 없다.
		assertThat(response.candidates().get(0).features()).isEmpty();
	}

	@Test
	@DisplayName("값이 원래 없는(blank) 행은 파싱 실패가 아니라 정상적으로 value:null 로 실린다")
	void blankValueIsIncludedAsNull() {
		given(this.placeFeatureRepository.findByPlaceIdIn(any())).willReturn(List.of(featureWithValue(null)));

		PlaceCandidateResponse response = this.service.findCandidates(request());

		assertThat(response.candidates().get(0).features()).hasSize(1);
		assertThat(response.candidates().get(0).features().get(0).value()).isNull();
	}
}
