package com.gabolle.backend.recommendation.adapter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.api.PlaceCandidateRequest;
import com.gabolle.backend.place.domain.UserInputKind;
import com.gabolle.backend.place.domain.UserPlaceCodeMap;
import com.gabolle.backend.place.repository.UserPlaceCodeMapRepository;
import com.gabolle.backend.recommendation.config.BaselineEngineProperties;
import com.gabolle.backend.recommendation.domain.RequestLocation;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripConstraint;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link BaselineCandidateTranslator} — 대조표 → 질의 조건 (S15P21E201-604).
 *
 * <p>스텁 code-map 으로 돈다. {@link UserPlaceCodeMapRepository} 는 인터페이스라 실제 DB 없이도
 * Mockito 로 흉내 낼 수 있다.
 */
class BaselineCandidateTranslatorTest {

	// 🔴 상한이 둘이다 (S15P21E201-724). 앞의 9000 이 채점 대상(장소 조회에 넘어가는 limit),
	//    뒤의 150 은 채점을 마친 뒤 남길 수다. 이 변환기는 앞의 것만 쓴다.
	private static final BaselineEngineProperties PROPERTIES = new BaselineEngineProperties(
			"rule-v1", "feature-v1", "ontology-v1", "policy-v1", 4000, 9000, 150, null, null);

	private final UserPlaceCodeMapRepository codeMapRepository = mock(UserPlaceCodeMapRepository.class);

	private final BaselineCandidateTranslator translator =
			new BaselineCandidateTranslator(PROPERTIES, this.codeMapRepository, new ObjectMapper());

	@Test
	@DisplayName("중심 좌표는 Trip.originLat/originLng, 반경은 설정값이고 상한은 채점 대상 상한이다")
	void 중심좌표와_반경은_설정과_여행에서_온다() {
		categoryIsMapped(true);
		Trip trip = trip(35.15, 129.05);

		PlaceCandidateRequest request = this.translator.translate(originOf(trip), trip, null, List.of());

		assertThat(request.center().lat()).isEqualTo(35.15);
		assertThat(request.center().lng()).isEqualTo(129.05);
		assertThat(request.radiusM()).isEqualTo(4000);
		// 🔴 150(=candidateLimit) 이 아니라 9000(=candidateScanLimit) 이어야 한다.
		//    장소 조회는 점수를 모르므로 limit 을 거리순으로 자른다 — 여기에 150 을 주면
		//    채점기는 가까운 150곳만 보게 된다 (S15P21E201-724).
		assertThat(request.limit()).isEqualTo(9000);
	}

	@Test
	@DisplayName("🔴 requiredFeatures·excludedFeatures 는 제약이 있어도 항상 비어 있다")
	void required와_excluded는_항상_비어있다() {
		categoryIsMapped(true);
		Trip trip = trip(35.15, 129.05);
		List<TripConstraint> constraints = List.of(
				new TripConstraint(UUID.randomUUID().toString(), trip.tripId(), "ALLERGY", "PEANUT",
						TripConstraint.Severity.HARD, "EXCLUDES", null, null,
						TripConstraint.EvidenceStatus.VERIFIED, TripConstraint.AnswerStatus.SELECTED,
						PersonalizationScope.TRIP, null));

		PlaceCandidateRequest request = this.translator.translate(originOf(trip), trip, null, constraints);

		assertThat(request.requiredFeatures()).isEmpty();
		assertThat(request.excludedFeatures()).isEmpty();
	}

	@Test
	@DisplayName("CATEGORY 답의 코드가 categories 로 그대로 들어간다")
	void category_답이_categories로_들어간다() {
		categoryIsMapped(true);
		Trip trip = trip(35.15, 129.05);
		PreferenceSnapshot snapshot = snapshot("CATEGORY", "{\"codes\": [\"SEA\", \"CAFE\"]}");

		PlaceCandidateRequest request = this.translator.translate(originOf(trip), trip, snapshot, List.of());

		assertThat(request.categories()).containsExactly("SEA", "CAFE");
	}

	@Test
	@DisplayName("대조표에 CATEGORY 짝이 없으면 categories 로 좁히지 않는다 — 없는 관계를 지어내지 않는다")
	void 대조표에_카테고리_짝이_없으면_비운다() {
		categoryIsMapped(false);
		Trip trip = trip(35.15, 129.05);
		PreferenceSnapshot snapshot = snapshot("CATEGORY", "{\"codes\": [\"SEA\"]}");

		PlaceCandidateRequest request = this.translator.translate(originOf(trip), trip, snapshot, List.of());

		assertThat(request.categories()).isEmpty();
	}

	@Test
	@DisplayName("취향 스냅샷이 없으면 categories 는 빈 목록이다")
	void 취향스냅샷_없으면_categories_비어있다() {
		categoryIsMapped(true);
		Trip trip = trip(35.15, 129.05);

		PlaceCandidateRequest request = this.translator.translate(originOf(trip), trip, null, List.of());

		assertThat(request.categories()).isEmpty();
	}

	private void categoryIsMapped(boolean mapped) {
		List<UserPlaceCodeMap> rows = mapped ? List.of(mock(UserPlaceCodeMap.class)) : List.of();
		when(this.codeMapRepository.findByIdUserInputKindAndIdUserInputCode(UserInputKind.PREFERENCE, "CATEGORY"))
				.thenReturn(rows);
	}

	private static Trip trip(double lat, double lng) {
		return new Trip(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
				LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12), lat, lng, 300_000, 2,
				"MORNING_TO_EVENING", "Asia/Seoul", Instant.now());
	}

	private static PreferenceSnapshot snapshot(String dimension, String valueJson) {
		return new PreferenceSnapshot(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 1,
				List.of(new PreferenceSnapshot.PreferenceAnswer(dimension, valueJson,
						PreferenceSnapshot.AnswerStatus.SELECTED)),
				PersonalizationScope.TRIP, List.of(), Instant.now());
	}

	/**
	 * 🔴 S15P21E201-550 — 중심 좌표를 여행에서 읽는 대신 {@code RequestLocation} 으로
	 * 받게 바뀌었다. 이 테스트들은 "요청이 위치를 안 준" 경우를 보므로 여행 출발지에서
	 * 만든다 — 엔진이 실제로 하는 것과 같다.
	 */
	private static RequestLocation originOf(Trip trip) {
		return RequestLocation.ofTripOrigin(trip.originLat(), trip.originLng(), trip.createdAt());
	}
}
