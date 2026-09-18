package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import com.gabolle.backend.place.service.PlaceCandidateQueryService;

import tools.jackson.databind.json.JsonMapper;

/**
 * 후보 조회가 영업시간으로 거르는가 — S15P21E201-852.
 *
 * <h2>🔴 이 검사가 막는 것은 "걸렀다고 말하면서 안 거르는 것"</h2>
 * 이 조건은 <b>일부 장소에만</b> 값이 있다. 그 상태에서 잘못될 수 있는 것이 셋이고 셋 다
 * 응답이 200 이라 조용하다.
 *
 * <ul>
 * <li>모르는 곳을 후보에서 지운다 → 영업시간을 아직 안 넣은 2,355곳이 통째로 사라지고,
 *     조건을 건 사용자에게는 "그 시각에 여는 곳이 없다" 로 보인다</li>
 * <li>모르는 곳을 남기면서 {@code appliedFilters} 에 걸렀다고 적는다 → 호출자는 목록을
 *     "영업 중인 곳" 으로 믿는다</li>
 * <li>닫힌 곳을 안 지운다 → 사람이 닫힌 문 앞에 도착한다</li>
 * </ul>
 *
 * <p>2026-09-10 은 목요일이다.
 */
class PlaceCandidateOpeningHoursTest {

	private static final double CENTER_LAT = 35.1595;

	private static final double CENTER_LNG = 129.1604;

	/** 한국 시각 2026-09-10(목) 10:00. */
	private static final OffsetDateTime THU_10AM =
			OffsetDateTime.of(2026, 9, 10, 10, 0, 0, 0, ZoneOffset.ofHours(9));

	private static final UUID OPEN_PLACE = UUID.randomUUID();

	private static final UUID CLOSED_PLACE = UUID.randomUUID();

	private static final UUID UNKNOWN_PLACE = UUID.randomUUID();

	private PlaceRepository placeRepository;

	private PlaceFeatureRepository placeFeatureRepository;

	private PlaceCandidateQueryService service;

	@BeforeEach
	void setUp() {
		this.placeRepository = mock(PlaceRepository.class);
		this.placeFeatureRepository = mock(PlaceFeatureRepository.class);
		this.service = new PlaceCandidateQueryService(this.placeRepository, this.placeFeatureRepository,
				JsonMapper.builder().build(), new PlaceProperties());
	}

	@Test
	@DisplayName("문 닫는 곳은 빠지고, 모르는 곳은 남되 걸렀다고 말하지 않는다")
	void closedIsDroppedAndUnknownIsKeptButNotClaimed() {
		givenPlaces(OPEN_PLACE, CLOSED_PLACE, UNKNOWN_PLACE);
		givenFeatures(
				hours(OPEN_PLACE, """
						{"status":"PARSED","byDay":{"thu":[["09:00","18:00"]]}}"""),
				hours(CLOSED_PLACE, """
						{"status":"PARSED","byDay":{"thu":[]}}"""));

		PlaceCandidateResponse response = this.service.findCandidates(requestOpenAt(THU_10AM));

		assertThat(response.candidates()).extracting(PlaceCandidateResponse.Candidate::placeId)
				.as("닫힌 곳만 빠진다")
				.containsExactlyInAnyOrder(OPEN_PLACE, UNKNOWN_PLACE);
		assertThat(response.appliedFilters()).doesNotContain("OPENING_HOURS");
		assertThat(response.notApplied())
				.extracting(PlaceCandidateResponse.NotApplied::filter,
						PlaceCandidateResponse.NotApplied::reason)
				.contains(tuple("OPENING_HOURS", "NOT_COLLECTED"));
	}

	@Test
	@DisplayName("후보 전부의 영업시간을 알면 걸렀다고 적는다")
	void filterIsClaimedOnlyWhenEveryCandidateIsKnown() {
		givenPlaces(OPEN_PLACE, CLOSED_PLACE);
		givenFeatures(
				hours(OPEN_PLACE, """
						{"status":"ALWAYS_OPEN","byDay":null}"""),
				hours(CLOSED_PLACE, """
						{"status":"PARSED","byDay":{"thu":[["19:00","22:00"]]}}"""));

		PlaceCandidateResponse response = this.service.findCandidates(requestOpenAt(THU_10AM));

		assertThat(response.candidates()).extracting(PlaceCandidateResponse.Candidate::placeId)
				.containsExactly(OPEN_PLACE);
		assertThat(response.appliedFilters()).contains("OPENING_HOURS");
		assertThat(response.notApplied()).isEmpty();
	}

	@Test
	@DisplayName("🔴 시각을 안 주면 영업시간을 보지 않는다 — 걸렀다고도, 못 걸렀다고도 적지 않는다")
	void withoutTheTimeNothingIsSaidAboutOpeningHours() {
		givenPlaces(CLOSED_PLACE);
		givenFeatures(hours(CLOSED_PLACE, """
				{"status":"PARSED","byDay":{"thu":[]}}"""));

		PlaceCandidateResponse response = this.service.findCandidates(requestOpenAt(null));

		assertThat(response.candidates())
				.as("요청하지 않은 조건으로 후보를 지우지 않는다")
				.hasSize(1);
		assertThat(response.appliedFilters()).doesNotContain("OPENING_HOURS");
		assertThat(response.notApplied()).isEmpty();
	}

	@Test
	@DisplayName("영업시간을 아무 곳도 안 넣은 상태에서도 후보가 사라지지 않는다")
	void nothingCollectedStillReturnsCandidates() {
		givenPlaces(UNKNOWN_PLACE);
		givenFeatures();

		PlaceCandidateResponse response = this.service.findCandidates(requestOpenAt(THU_10AM));

		assertThat(response.candidates()).hasSize(1);
		assertThat(response.notApplied()).hasSize(1);
	}

	private void givenPlaces(UUID... placeIds) {
		List<Place> places = new ArrayList<>();
		for (UUID placeId : placeIds) {
			places.add(Place.imported(placeId, "장소 " + placeId, "food", "부산 해운대구",
					CENTER_LAT, CENTER_LNG, "sbiz", placeId.toString(), OffsetDateTime.now(),
					null, "v1"));
		}
		given(this.placeRepository.findWithinBoundingBox(any(Double.class), any(Double.class),
				any(Double.class), any(Double.class), any(Limit.class))).willReturn(places);
	}

	private void givenFeatures(PlaceFeature... features) {
		given(this.placeFeatureRepository.findByPlaceIdIn(any())).willReturn(List.of(features));
	}

	private static PlaceFeature hours(UUID placeId, String value) {
		return PlaceFeature.imported(UUID.randomUUID(), placeId, "OPENING_HOURS", null, value,
				PlaceEvidenceStatus.ESTIMATED, "TOURAPI", placeId.toString(), null, "v1",
				OffsetDateTime.now());
	}

	private static PlaceCandidateRequest requestOpenAt(OffsetDateTime openNowAt) {
		return new PlaceCandidateRequest(new PlaceCandidateRequest.Center(CENTER_LAT, CENTER_LNG),
				1000, List.of(), List.of(), List.of(), openNowAt, 0, 200);
	}
}
