package com.gabolle.backend.recommendation.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.place.api.PlaceCandidateRequest;
import com.gabolle.backend.place.api.PlaceCandidateResponse;
import com.gabolle.backend.place.domain.UserInputKind;
import com.gabolle.backend.place.domain.UserPlaceCodeMap;
import com.gabolle.backend.place.repository.UserPlaceCodeMapRepository;
import com.gabolle.backend.place.service.PlaceCandidateQueryService;
import com.gabolle.backend.recommendation.config.BaselineEngineProperties;
import com.gabolle.backend.recommendation.domain.FallbackMode;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripConstraint;
import com.gabolle.backend.trip.domain.TripRepository;
import com.gabolle.backend.trip.domain.TripSeedPlaceRepository;

/**
 * 규칙 기반 BASELINE 추천 엔진 (S15P21E201-604).
 *
 * <p>{@link RecommendationEnginePort} 의 운영 구현체가 없어서 모든 일정 생성 요청이
 * {@code ENGINE_NOT_CONFIGURED} 로 실패하던 것을 해소한다. 학습 모델·온톨로지 서버가
 * 아직 없는 동안 이 규칙 기반 엔진이 그 자리를 채운다 — {@link FallbackMode#BASELINE}.
 *
 * <p>🔴 <b>{@code @ConditionalOnBean(UserPlaceCodeMapRepository.class)} 를 쓰는 이유.</b>
 * {@code RecommendationSliceApplication}(추천 도메인만 스캔하는 테스트 전용 컨텍스트)이
 * {@code place} 패키지를 안 스캔해서 이 클래스가 요구하는 빈들이 없다. {@code
 * RecommendationJobRunner} 가 정확히 같은 이유로 같은 조건을 쓴 선례가 있다(그 javadoc
 * 32~41행) — 없으면 그 슬라이스 컨텍스트를 쓰는 테스트가 전부 컨텍스트 로딩에서 깨진다.
 * {@link BaselineEngineStartupValidator} 가 이 조건 배선 자체가 빠졌을 때(= {@code db}·
 * {@code dev} 프로필인데 place 패키지는 스캔하면서 이 빈은 안 붙었을 때)를 잡는다.
 */
@Component
@Profile({ "db", "dev" })
// 🔴 조건을 후보 조회 서비스가 아니라 리포지토리에 건다. 앞의 것은 이 클래스와 같은
//    @Component 라 스캔 순서에 따라 "아직 없음" 으로 읽힐 수 있고, 그러면 엔진이 조용히
//    빠진 채로 배포가 나간다. 리포지토리는 @EnableJpaRepositories 가 컴포넌트 스캔보다
//    먼저 등록되므로 그 순서 문제에 걸리지 않는다. 장소 데이터 계층이 있는데 후보 조회
//    서비스가 없으면 여기서 생성이 실패해 기동이 멈춘다 — 조용한 오작동보다 낫다.
@ConditionalOnBean(UserPlaceCodeMapRepository.class)
public class BaselineRecommendationEngine implements RecommendationEnginePort {

	private final TripRepository tripRepository;

	private final PlaceCandidateQueryService placeCandidateQueryService;

	private final BaselineCandidateTranslator translator;

	private final BaselineCandidateScorer scorer;

	private final BaselineEngineProperties properties;

	private final UserPlaceCodeMapRepository codeMapRepository;

	/** S15P21E201-338 — 복제 씨앗. 보통 여행은 비어 있어 아무 일도 하지 않는다({@link SeedBoost}). */
	private final TripSeedPlaceRepository seedPlaceRepository;

	public BaselineRecommendationEngine(TripRepository tripRepository,
			PlaceCandidateQueryService placeCandidateQueryService, BaselineCandidateTranslator translator,
			BaselineCandidateScorer scorer, BaselineEngineProperties properties,
			UserPlaceCodeMapRepository codeMapRepository, TripSeedPlaceRepository seedPlaceRepository) {
		this.tripRepository = tripRepository;
		this.placeCandidateQueryService = placeCandidateQueryService;
		this.translator = translator;
		this.scorer = scorer;
		this.properties = properties;
		this.codeMapRepository = codeMapRepository;
		this.seedPlaceRepository = seedPlaceRepository;
	}

	@Override
	public EngineCandidateBatch generate(EngineRequest request) {
		Trip trip = loadTrip(request.tripId());

		if (trip.originLat() == null || trip.originLng() == null) {
			// 🔴 부산 시청 같은 중심 좌표를 지어내지 않는다 — 결과가 왜 이상한지 아무도 못
			// 찾게 된다. 좌표가 없으면 여기서 실패로 남긴다.
			throw new RecommendationEngineException("ENGINE_ORIGIN_MISSING",
					"여행에 출발지 좌표가 없다: tripId=" + request.tripId());
		}

		// 🔴 findLatestSnapshot·findConstraints 를 쓰지 않는다 — 추천 Job 이 기록해 둔 "그 판"
		// 을 읽어야 한다. 실행이 비동기라 그 사이 사용자가 취향·제약을 다시 답했을 수 있다.
		PreferenceSnapshot preferenceSnapshot = (request.preferenceSnapshotId() == null) ? null
				: this.tripRepository.findSnapshotById(request.preferenceSnapshotId().toString()).orElse(null);
		List<TripConstraint> constraints = (request.constraintSnapshotId() == null) ? List.of()
				: this.tripRepository.findConstraintsBySnapshotId(request.constraintSnapshotId().toString());

		long candidateGenerationStart = System.nanoTime();
		PlaceCandidateRequest queryRequest = this.translator.translate(trip, preferenceSnapshot, constraints);
		PlaceCandidateResponse response = this.placeCandidateQueryService.findCandidates(queryRequest);
		long candidateGenerationMs = elapsedMs(candidateGenerationStart);

		// 대조표는 배치당 한 번만 읽는다 — 후보마다 다시 읽으면 질의 수가 후보 수에 비례한다.
		List<UserPlaceCodeMap> preferenceCodeMap =
				this.codeMapRepository.findByIdUserInputKindOrderByIdUserInputCodeAsc(UserInputKind.PREFERENCE);
		List<UserPlaceCodeMap> constraintCodeMap =
				this.codeMapRepository.findByIdUserInputKindOrderByIdUserInputCodeAsc(UserInputKind.CONSTRAINT);

		long rankingStart = System.nanoTime();
		List<EngineCandidate> candidates = new ArrayList<>(response.candidates().size());
		for (PlaceCandidateResponse.Candidate candidate : response.candidates()) {
			candidates.add(this.scorer.score(candidate, preferenceSnapshot, constraints, this.properties.radiusM(),
					this.properties.weights(), preferenceCodeMap, constraintCodeMap));
		}
		// 🔴 S15P21E201-338 — 복제 씨앗을 앞세운다. 점수만 올리고 제약 판정은 그대로다(SeedBoost 참고).
		candidates = SeedBoost.apply(candidates, this.seedPlaceRepository.findByTripId(trip.tripId()));
		long rankingMs = elapsedMs(rankingStart);

		String datasetVersion = resolveDatasetVersion(response.datasetVersions());

		EngineVersions versions = new EngineVersions(this.properties.modelVersion(), this.properties.featureVersion(),
				this.properties.ontologyVersion(), this.properties.policyVersion(), datasetVersion);
		EngineLatencies latencies = new EngineLatencies(candidateGenerationMs, null, null, rankingMs, null);

		return new EngineCandidateBatch(candidates, versions, latencies, FallbackMode.BASELINE, "NO_MODEL_ENGINE");
	}

	private Trip loadTrip(UUID tripId) {
		if (tripId == null) {
			throw new RecommendationEngineException("ENGINE_TRIP_CONTEXT_MISSING", "추천 요청에 tripId 가 없다");
		}
		return this.tripRepository.findById(tripId.toString())
				.orElseThrow(() -> new RecommendationEngineException("ENGINE_TRIP_CONTEXT_MISSING",
						"여행을 찾을 수 없다: tripId=" + tripId));
	}

	/**
	 * {@code datasetVersion} 은 설정이 아니라 {@code PlaceCandidateResponse.datasetVersions()}
	 * 에서 가져온다 — 실제로 이번에 조회된 장소들이 어느 수집분에서 왔는지가 정본이다.
	 *
	 * <p>🔴 비어 있으면 {@code null} 을 그대로 돌려준다. {@code RecommendationService} 가
	 * {@code VERSION_UNRESOLVED} 로 이 요청을 실패시키고 {@code dataset_version} 이라고
	 * 이름까지 남긴다 — {@code "unknown"} 을 넣으면 재현할 수 없는 결과가 재현 가능한
	 * 척하게 된다.
	 */
	private String resolveDatasetVersion(List<String> datasetVersions) {
		if (datasetVersions == null || datasetVersions.isEmpty()) {
			return null;
		}
		// TreeSet 이 정렬과 중복 제거를 한 번에 한다.
		String joined = String.join(",", new TreeSet<>(datasetVersions));
		if (joined.length() > 100) {
			// 🔴 VARCHAR(100) 을 넘는다고 조용히 자르지 않는다 — 잘린 값은 그 뒤로 다른
			// 데이터셋 조합과 겹쳐 보일 수 있다.
			throw new RecommendationEngineException("ENGINE_DATASET_VERSION_AMBIGUOUS",
					"datasetVersion 을 이어 붙인 문자열이 100자를 넘는다(" + joined.length() + "자): " + joined);
		}
		return joined;
	}

	private static long elapsedMs(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000L;
	}
}
