package com.gabolle.backend.recommendation.application;

import java.util.Map;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.recommendation.domain.CandidateStage;
import com.gabolle.backend.recommendation.domain.ConstraintVerdict;
import com.gabolle.backend.recommendation.domain.FallbackMode;
import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.RecommendationCandidate;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.presentation.RecommendationResultController;
import com.gabolle.backend.recommendation.presentation.dto.RecommendationResultResponse;
import com.gabolle.backend.recommendation.repository.RecommendationCandidateRepository;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 결과 DTO 매핑. 실제 값이 없는 칸을 지어내지 않는가와, 반환되지 않은 후보가 새지 않는가를
 * 본다.
 */
class RecommendationResultQueryServiceTest {

	private RecommendationCandidateRepository candidateRepository;
	private PlaceRepository placeRepository;
	private ItineraryRepository itineraryRepository;
	private RecommendationResultQueryService service;

	private final UUID requestId = UUID.randomUUID();
	private final UUID placeId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		this.candidateRepository = mock(RecommendationCandidateRepository.class);
		this.placeRepository = mock(PlaceRepository.class);
		this.itineraryRepository = mock(ItineraryRepository.class);
		this.service = new RecommendationResultQueryService(this.candidateRepository, this.placeRepository,
				this.itineraryRepository, new ObjectMapper(),
				// 가격 자료가 없는 상태 — estimatedCostKrw 가 null 로 남는지 본다.
				placeIds -> Map.of());

		Place place = mock(Place.class);
		when(place.getPlaceId()).thenReturn(this.placeId);
		when(place.getNameKo()).thenReturn("해운대 해수욕장");
		when(this.placeRepository.findByPlaceIdIn(any())).thenReturn(List.of(place));
	}

	private RecommendationJob succeededJob(FallbackMode fallbackMode) {
		RecommendationJob job = RecommendationJob.start(UUID.randomUUID(), this.requestId, UUID.randomUUID(),
				JobType.ITINERARY_GENERATION, OffsetDateTime.now());
		job.markCompleted(OffsetDateTime.now(), OffsetDateTime.now(), fallbackMode, null);
		return job;
	}

	private RecommendationCandidate.Builder returnedCandidateBuilder() {
		return RecommendationCandidate.builder()
				.candidateId(UUID.randomUUID())
				.requestId(this.requestId)
				.placeId(this.placeId)
				.candidateSource("BASELINE")
				.candidateStage(CandidateStage.RETURNED)
				.eligible(true)
				.constraintVerdict(ConstraintVerdict.PASS)
				.finalScore(1.0)
				.finalRank(1)
				.returned(true)
				.fallbackMode(FallbackMode.BASELINE)
				.createdAt(OffsetDateTime.now());
	}

	@Test
	@DisplayName("🔴 가격이 없는 곳은 estimatedCostKrw 가 null — 0 으로 채우지 않는다")
	void costIsNullWhenPriceNotCollected() {
		RecommendationCandidate candidate = returnedCandidateBuilder().build();
		when(this.candidateRepository.findByRequestIdAndReturnedTrueOrderByFinalRankAsc(this.requestId))
				.thenReturn(List.of(candidate));

		RecommendationResultResponse response = this.service.buildResult(succeededJob(FallbackMode.BASELINE));

		assertThat(response.items()).hasSize(1);
		RecommendationResultResponse.Item item = response.items().get(0);
		assertThat(item.imageUrl()).isNull();
		assertThat(item.estimatedCostKrw()).isNull();
		assertThat(item.id()).isEqualTo(this.placeId.toString());
		assertThat(item.title()).isEqualTo("해운대 해수욕장");
	}

	@Test
	@DisplayName("🔴 returned=false 후보는 응답에 없다 — 반환 전용 조회만 쓴다")
	void onlyReturnedCandidatesQueried() {
		RecommendationCandidate candidate = returnedCandidateBuilder().build();
		when(this.candidateRepository.findByRequestIdAndReturnedTrueOrderByFinalRankAsc(this.requestId))
				.thenReturn(List.of(candidate));

		RecommendationResultResponse response = this.service.buildResult(succeededJob(FallbackMode.BASELINE));

		assertThat(response.items()).hasSize(1);
		// 하드 제약에 걸려 반환되지 않은 후보까지 섞이는 전체 조회는 아예 부르지 않는다.
		verify(this.candidateRepository, never()).findByRequestIdOrderByFinalRankAscPlaceIdAsc(any());
	}

	@Test
	@DisplayName("crowdLevel — feature_values 에 CROWDING_SCORE 가 없으면 null(기본값 LOW 를 두지 않는다)")
	void crowdLevelNullWhenNoScore() {
		RecommendationCandidate candidate = returnedCandidateBuilder().featureValues("{}").build();
		when(this.candidateRepository.findByRequestIdAndReturnedTrueOrderByFinalRankAsc(this.requestId))
				.thenReturn(List.of(candidate));

		RecommendationResultResponse response = this.service.buildResult(succeededJob(FallbackMode.BASELINE));

		assertThat(response.items().get(0).crowdLevel()).isNull();
	}

	@Test
	@DisplayName("crowdLevel — CROWDING_SCORE 를 구간화한다")
	void crowdLevelBucketsScore() {
		RecommendationCandidate low = returnedCandidateBuilder().featureValues("{\"CROWDING_SCORE\":0.1}").build();
		when(this.candidateRepository.findByRequestIdAndReturnedTrueOrderByFinalRankAsc(this.requestId))
				.thenReturn(List.of(low));

		assertThat(this.service.buildResult(succeededJob(FallbackMode.BASELINE)).items().get(0).crowdLevel())
				.isEqualTo("LOW");
	}

	@Test
	@DisplayName("dataStatus — constraintVerdict UNKNOWN 인 후보가 있으면 전체 상태가 PARTIAL")
	void unknownConstraintVerdictMakesStatusPartial() {
		RecommendationCandidate unknown = returnedCandidateBuilder()
				.constraintVerdict(ConstraintVerdict.UNKNOWN)
				.warningCodes(new String[] { "CONSTRAINT_UNKNOWN" })
				.build();
		when(this.candidateRepository.findByRequestIdAndReturnedTrueOrderByFinalRankAsc(this.requestId))
				.thenReturn(List.of(unknown));

		RecommendationResultResponse response = this.service.buildResult(succeededJob(FallbackMode.BASELINE));

		assertThat(response.status()).isEqualTo("PARTIAL");
		assertThat(response.items().get(0).dataStatus()).isEqualTo("UNKNOWN");
	}

	@Test
	@DisplayName("전부 확인된 후보면 COMPLETED")
	void allVerifiedMeansCompleted() {
		RecommendationCandidate candidate = returnedCandidateBuilder().build();
		when(this.candidateRepository.findByRequestIdAndReturnedTrueOrderByFinalRankAsc(this.requestId))
				.thenReturn(List.of(candidate));

		RecommendationResultResponse response = this.service.buildResult(succeededJob(FallbackMode.BASELINE));

		assertThat(response.status()).isEqualTo("COMPLETED");
		assertThat(response.items().get(0).dataStatus()).isEqualTo("VERIFIED");
	}

	@Test
	@DisplayName("placeCount 는 items 개수와 같다")
	void placeCountMatchesItemCount() {
		RecommendationCandidate candidate = returnedCandidateBuilder().build();
		when(this.candidateRepository.findByRequestIdAndReturnedTrueOrderByFinalRankAsc(this.requestId))
				.thenReturn(List.of(candidate));

		RecommendationResultResponse response = this.service.buildResult(succeededJob(FallbackMode.BASELINE));

		assertThat(response.placeCount()).isEqualTo(response.items().size()).isEqualTo(1);
	}

	@Test
	@DisplayName("estimatedTravelMinutes — 일정이 없으면(itineraryId == null) null")
	void estimatedTravelMinutesNullWithoutItinerary() {
		RecommendationCandidate candidate = returnedCandidateBuilder().build();
		when(this.candidateRepository.findByRequestIdAndReturnedTrueOrderByFinalRankAsc(this.requestId))
				.thenReturn(List.of(candidate));

		RecommendationResultResponse response = this.service.buildResult(succeededJob(FallbackMode.BASELINE));

		assertThat(response.itineraryId()).isNull();
		assertThat(response.estimatedTravelMinutes()).isNull();
	}

	@Test
	@DisplayName("estimatedTravelMinutes — 구간의 duration_min 합, 모르는 구간은 더하지 않고 건너뛴다")
	void estimatedTravelMinutesSkipsUnknownLegs() {
		UUID itineraryId = UUID.randomUUID();
		RecommendationJob job = RecommendationJob.start(UUID.randomUUID(), this.requestId, UUID.randomUUID(),
				JobType.ITINERARY_GENERATION, OffsetDateTime.now());
		job.applyRequestContext(UUID.randomUUID(), 1, null, null, itineraryId, 1, null, null);
		job.markCompleted(OffsetDateTime.now(), OffsetDateTime.now(), FallbackMode.BASELINE, null);

		RecommendationCandidate candidate = returnedCandidateBuilder().build();
		when(this.candidateRepository.findByRequestIdAndReturnedTrueOrderByFinalRankAsc(this.requestId))
				.thenReturn(List.of(candidate));

		Itinerary itinerary = new Itinerary(itineraryId.toString(), UUID.randomUUID().toString(), 1);
		when(this.itineraryRepository.findById(itineraryId.toString())).thenReturn(java.util.Optional.of(itinerary));

		ItineraryLeg known = new ItineraryLeg(UUID.randomUUID().toString(), "v1", 0, 1,
				null, UUID.randomUUID().toString(), "WALK", 500, 10, 500, null, null, java.time.Instant.now());
		ItineraryLeg unknown = new ItineraryLeg(UUID.randomUUID().toString(), "v1", 0, 2,
				UUID.randomUUID().toString(), UUID.randomUUID().toString(), "BUS", null, null, null, null, null,
				java.time.Instant.now());
		ItineraryContent content = new ItineraryContent(null, List.of(), List.of(known, unknown), List.of());
		when(this.itineraryRepository.findContent(itineraryId.toString(), 1)).thenReturn(java.util.Optional.of(content));

		RecommendationResultResponse response = this.service.buildResult(job);

		assertThat(response.estimatedTravelMinutes()).isEqualTo(10);
	}

	@Test
	@DisplayName("🔴 아직 안 끝난 Job(PENDING) 은 409 로 이어질 예외를 던진다")
	void pendingJobThrowsNotReady() {
		RecommendationJob pending = RecommendationJob.start(UUID.randomUUID(), this.requestId, UUID.randomUUID(),
				JobType.ITINERARY_GENERATION, OffsetDateTime.now());

		assertThatThrownBy(() -> this.service.buildResult(pending))
				.isInstanceOf(RecommendationResultController.JobNotReadyException.class);
	}

	@Test
	@DisplayName("FAILED Job 은 errorCode 를 errorMessage 로 담고 items 는 비어 있다")
	void failedJobCarriesErrorCode() {
		RecommendationJob failed = RecommendationJob.start(UUID.randomUUID(), this.requestId, UUID.randomUUID(),
				JobType.ITINERARY_GENERATION, OffsetDateTime.now());
		failed.markFailed("ENGINE_TIMEOUT", com.gabolle.backend.recommendation.domain.JobStage.RANKING,
				OffsetDateTime.now(), true, true);

		RecommendationResultResponse response = this.service.buildResult(failed);

		assertThat(response.status()).isEqualTo("FAILED");
		assertThat(response.items()).isEmpty();
		assertThat(response.errorMessage()).isEqualTo("ENGINE_TIMEOUT");
	}

	// 앱의 추천 화면은 경로에 작업 번호만 들고 있어서, 담아두기·빼기를 보낼 주소를 응답에서
	// 받지 못하면 알 수 없다.

	@Test
	@DisplayName("🔴 성공 응답에 tripId 가 실린다 — 앱이 담아두기·빼기를 보낼 주소다")
	void succeededJobCarriesTripId() {
		UUID tripId = UUID.randomUUID();
		RecommendationJob job = succeededJob(FallbackMode.BASELINE);
		job.applyRequestContext(tripId, 1, null, null, null, null, null, "test");
		when(this.candidateRepository.findByRequestIdAndReturnedTrueOrderByFinalRankAsc(this.requestId))
				.thenReturn(List.of(returnedCandidateBuilder().build()));

		assertThat(this.service.buildResult(job).tripId()).isEqualTo(tripId.toString());
	}

	@Test
	@DisplayName("🔴 실패 응답에도 tripId 가 실린다 — 실패한 요청도 그 여행의 요청이다")
	void failedJobCarriesTripId() {
		UUID tripId = UUID.randomUUID();
		RecommendationJob failed = RecommendationJob.start(UUID.randomUUID(), this.requestId, UUID.randomUUID(),
				JobType.ITINERARY_GENERATION, OffsetDateTime.now());
		failed.applyRequestContext(tripId, 1, null, null, null, null, null, "test");
		failed.markFailed("ENGINE_TIMEOUT", com.gabolle.backend.recommendation.domain.JobStage.RANKING,
				OffsetDateTime.now(), true, true);

		assertThat(this.service.buildResult(failed).tripId()).isEqualTo(tripId.toString());
	}

	@Test
	@DisplayName("여행에 안 매인 작업은 tripId 가 null 이다 — 지어내지 않는다")
	void jobWithoutTripHasNullTripId() {
		when(this.candidateRepository.findByRequestIdAndReturnedTrueOrderByFinalRankAsc(this.requestId))
				.thenReturn(List.of(returnedCandidateBuilder().build()));

		assertThat(this.service.buildResult(succeededJob(FallbackMode.BASELINE)).tripId()).isNull();
	}
	@Test
	@DisplayName("실린 가격이 있으면 estimatedCostKrw 로 나온다 (S15P21E201-1479)")
	void costComesFromMenuPrice() {
		RecommendationResultQueryService withPrice = new RecommendationResultQueryService(this.candidateRepository,
				this.placeRepository, this.itineraryRepository, new ObjectMapper(),
				placeIds -> Map.of(this.placeId, 17_000));
		RecommendationCandidate candidate = returnedCandidateBuilder().build();
		when(this.candidateRepository.findByRequestIdAndReturnedTrueOrderByFinalRankAsc(this.requestId))
				.thenReturn(List.of(candidate));

		RecommendationResultResponse response = withPrice.buildResult(succeededJob(FallbackMode.BASELINE));

		assertThat(response.items().get(0).estimatedCostKrw()).isEqualTo(17_000);
	}

	@Test
	@DisplayName("🔴 다른 장소의 가격이 섞이지 않는다 — 열쇠가 맞을 때만 붙는다")
	void costDoesNotLeakFromAnotherPlace() {
		RecommendationResultQueryService withPrice = new RecommendationResultQueryService(this.candidateRepository,
				this.placeRepository, this.itineraryRepository, new ObjectMapper(),
				placeIds -> Map.of(UUID.randomUUID(), 99_000));
		RecommendationCandidate candidate = returnedCandidateBuilder().build();
		when(this.candidateRepository.findByRequestIdAndReturnedTrueOrderByFinalRankAsc(this.requestId))
				.thenReturn(List.of(candidate));

		RecommendationResultResponse response = withPrice.buildResult(succeededJob(FallbackMode.BASELINE));

		assertThat(response.items().get(0).estimatedCostKrw()).isNull();
	}
}
