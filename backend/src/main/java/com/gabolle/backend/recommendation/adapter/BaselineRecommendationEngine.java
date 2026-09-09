package com.gabolle.backend.recommendation.adapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.gabolle.backend.recommendation.config.PreferenceAlignmentWeights;
import com.gabolle.backend.recommendation.domain.FallbackMode;
import com.gabolle.backend.recommendation.domain.RequestLocation;
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

	private static final Logger LOGGER = LoggerFactory.getLogger(BaselineRecommendationEngine.class);

	private final TripRepository tripRepository;

	private final PlaceCandidateQueryService placeCandidateQueryService;

	private final BaselineCandidateTranslator translator;

	private final BaselineCandidateScorer scorer;

	private final BaselineEngineProperties properties;

	/** S15P21E201-547 — 취향 다섯 차원이 {@code weights.preferenceAlignment} 를 나누는 비율. */
	private final PreferenceAlignmentWeights alignmentWeights;

	private final UserPlaceCodeMapRepository codeMapRepository;

	/** S15P21E201-338 — 복제 씨앗. 보통 여행은 비어 있어 아무 일도 하지 않는다({@link SeedBoost}). */
	private final TripSeedPlaceRepository seedPlaceRepository;

	public BaselineRecommendationEngine(TripRepository tripRepository,
			PlaceCandidateQueryService placeCandidateQueryService, BaselineCandidateTranslator translator,
			BaselineCandidateScorer scorer, BaselineEngineProperties properties,
			PreferenceAlignmentWeights alignmentWeights, UserPlaceCodeMapRepository codeMapRepository,
			TripSeedPlaceRepository seedPlaceRepository) {
		this.tripRepository = tripRepository;
		this.placeCandidateQueryService = placeCandidateQueryService;
		this.translator = translator;
		this.scorer = scorer;
		this.properties = properties;
		this.alignmentWeights = alignmentWeights;
		this.codeMapRepository = codeMapRepository;
		this.seedPlaceRepository = seedPlaceRepository;
	}

	@Override
	public EngineCandidateBatch generate(EngineRequest request) {
		Trip trip = loadTrip(request.tripId());

		// 🔴 S15P21E201-550 — 요청이 준 현재 위치가 있으면 그것이 중심이고, 없으면 여행
		//    출발지를 쓴다. 둘 다 없을 때만 실패다.
		//
		//    수기 입력(MANUAL)은 여기서 GPS 와 **똑같이** 다뤄진다 — 작업 내용이 "위치 거부
		//    시 수기 입력을 1급 fallback 으로 제공한다" 라고 못 박았고, 거리 계산에 들어가는
		//    값은 어느 쪽이든 좌표 하나다.
		RequestLocation location = (request.location() != null) ? request.location()
				: RequestLocation.ofTripOrigin(trip.originLat(), trip.originLng(), trip.createdAt());
		if (location == null) {
			// 🔴 부산 시청 같은 중심 좌표를 지어내지 않는다 — 결과가 왜 이상한지 아무도 못
			// 찾게 된다. 좌표가 없으면 여기서 실패로 남긴다.
			throw new RecommendationEngineException("ENGINE_ORIGIN_MISSING",
					"요청에 현재 위치가 없고 여행에도 출발지 좌표가 없다: tripId=" + request.tripId());
		}

		// 🔴 findLatestSnapshot·findConstraints 를 쓰지 않는다 — 추천 Job 이 기록해 둔 "그 판"
		// 을 읽어야 한다. 실행이 비동기라 그 사이 사용자가 취향·제약을 다시 답했을 수 있다.
		PreferenceSnapshot preferenceSnapshot = (request.preferenceSnapshotId() == null) ? null
				: this.tripRepository.findSnapshotById(request.preferenceSnapshotId().toString()).orElse(null);
		List<TripConstraint> constraints = (request.constraintSnapshotId() == null) ? List.of()
				: this.tripRepository.findConstraintsBySnapshotId(request.constraintSnapshotId().toString());

		long candidateGenerationStart = System.nanoTime();
		PlaceCandidateRequest queryRequest =
				this.translator.translate(location, trip, preferenceSnapshot, constraints);
		PlaceCandidateResponse response = this.placeCandidateQueryService.findCandidates(queryRequest);
		long candidateGenerationMs = elapsedMs(candidateGenerationStart);

		// 대조표는 배치당 한 번만 읽는다 — 후보마다 다시 읽으면 질의 수가 후보 수에 비례한다.
		List<UserPlaceCodeMap> preferenceCodeMap =
				this.codeMapRepository.findByIdUserInputKindOrderByIdUserInputCodeAsc(UserInputKind.PREFERENCE);
		List<UserPlaceCodeMap> constraintCodeMap =
				this.codeMapRepository.findByIdUserInputKindOrderByIdUserInputCodeAsc(UserInputKind.CONSTRAINT);

		if (response.scanTruncated()) {
			// 🔴 조용히 넘기지 않는다. 잘렸다는 것은 "반경 안인데 채점조차 안 된 장소가 있다" 는
			//    뜻이고, 그 사실이 안 남으면 나중에 결과가 이상해도 원인을 못 찾는다.
			//    gabolle.place.candidate-max-scanned 를 올려야 한다는 신호다.
			LOGGER.warn("후보 조회가 상한에 걸려 잘렸다 — tripId={} 반경={}m 받은 후보={}곳. "
					+ "gabolle.place.candidate-max-scanned 를 올려야 반경 안 장소가 전부 채점된다",
					request.tripId(), this.properties.radiusM(), response.candidates().size());
		}

		long rankingStart = System.nanoTime();
		List<EngineCandidate> candidates = new ArrayList<>(response.candidates().size());
		for (PlaceCandidateResponse.Candidate candidate : response.candidates()) {
			candidates.add(this.scorer.score(candidate, preferenceSnapshot, constraints, this.properties.radiusM(),
					this.properties.weights(), this.alignmentWeights, preferenceCodeMap, constraintCodeMap));
		}
		// 🔴 S15P21E201-338 — 복제 씨앗을 앞세운다. 점수만 올리고 제약 판정은 그대로다(SeedBoost 참고).
		candidates = SeedBoost.apply(candidates, this.seedPlaceRepository.findByTripId(trip.tripId()));
		// 🔴 S15P21E201-724 — 자르기는 여기서, 채점을 마친 뒤에 한다.
		candidates = keepBestScoring(candidates, this.properties.candidateLimit());
		long rankingMs = elapsedMs(rankingStart);

		String datasetVersion = resolveDatasetVersion(response.datasetVersions());

		EngineVersions versions = new EngineVersions(this.properties.modelVersion(), this.properties.featureVersion(),
				this.properties.ontologyVersion(), this.properties.policyVersion(), datasetVersion);
		EngineLatencies latencies = new EngineLatencies(candidateGenerationMs, null, null, rankingMs, null);

		// 🔴 실제로 쓴 출발지를 함께 돌려준다 — 그 선택을 아는 것은 엔진뿐이다.
		return new EngineCandidateBatch(candidates, versions, latencies, FallbackMode.BASELINE,
				"NO_MODEL_ENGINE", location);
	}

	/**
	 * 점수 높은 순으로 {@code limit} 개만 남긴다 — S15P21E201-724.
	 *
	 * <h2>🔴 왜 자르는 자리를 옮겼나</h2>
	 *
	 * 전에는 {@code candidateLimit} 이 {@code PlaceCandidateQueryService} 로 그대로 넘어갔다.
	 * 그 서비스는 점수를 모르므로 <b>거리순</b>으로 자르고, 그래서 그 상한은 "채점 후 상위 200"
	 * 이 아니라 <b>"가까운 순 200곳만 채점 대상"</b> 이었다. 부산 반경 5km 안에는 음식점만 평균
	 * 9,422곳이 있어서 실효 반경이 중앙값 <b>304m</b> 였다 — 그 밖의 장소는 아무리 취향에 맞아도
	 * 점수를 매길 기회조차 없었고, 그래서 서로 다른 네 조건으로 재도 "후보 200 안에 든 정답"
	 * 비율이 0.26% 로 소수점까지 같았다(S15P21E201-713 실측).
	 *
	 * <h2>🔴 저장되는 후보 수는 안 는다</h2>
	 *
	 * {@code CandidateAssembler} 는 엔진이 돌려준 것을 <b>전부</b> 행으로 남긴다. 그래서 여기서
	 * 자르지 않으면 요청 하나에 수천 행이 쌓인다. 자르는 수를 예전과 같은 {@code candidateLimit}
	 * 으로 둔 이유가 그것이다 — 바뀐 것은 <b>어느 200곳이 남는가</b>뿐이다.
	 *
	 * <h2>🔴 잘린 후보의 행은 남지 않는다</h2>
	 *
	 * 그건 예전과 같다. 예전에는 "가까운 200곳" 밖이 통째로 사라졌고 지금은 "점수 상위 200곳"
	 * 밖이 사라진다. 다만 <b>탈락 판정을 받은 후보(FAIL·미확인)는 점수가 낮아도 여기서 우선
	 * 지켜지지 않는다</b> — 그것까지 남기려면 저장 행 수 자체를 늘려야 하고, 그건 이 티켓의
	 * 범위가 아니다. 알려진 한계로 적어 둔다.
	 *
	 * <p>동점은 {@code placeId} 로 가른다 — 순서가 실행마다 달라지면 나중에 비교가 불가능해진다
	 * ({@code CandidateAssembler} 가 같은 이유로 같은 규칙을 쓴다).
	 */
	private static List<EngineCandidate> keepBestScoring(List<EngineCandidate> candidates, int limit) {
		if (candidates.size() <= limit) {
			return candidates;
		}
		List<EngineCandidate> sorted = new ArrayList<>(candidates);
		sorted.sort(Comparator
				// 🔴 점수가 없는 후보를 0 으로 치지 않는다 — 없는 것과 낮은 것은 다르다.
				//    맨 뒤로 보내되 버리지는 않는다(자리가 남으면 들어온다).
				.comparing(EngineCandidate::preRankScore,
						Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparing(EngineCandidate::placeId));
		return new ArrayList<>(sorted.subList(0, limit));
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
