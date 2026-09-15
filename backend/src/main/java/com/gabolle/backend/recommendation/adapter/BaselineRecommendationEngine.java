package com.gabolle.backend.recommendation.adapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.gabolle.backend.recommendation.application.RecommendationCodes;
import com.gabolle.backend.recommendation.domain.FallbackMode;
import com.gabolle.backend.recommendation.domain.RequestLocation;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TravelArea;
import com.gabolle.backend.trip.domain.TripTravelAreaRepository;
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
 * <h2>배선 — 조건이 아니라 스캔 목록</h2>
 * 이 엔진은 {@code place} 패키지의 빈들을 필요로 한다. 그것을 {@code @ConditionalOnBean} 으로
 * 다루던 것을 S15P21E201-808 에서 걷어냈다. 아래 애노테이션 위 주석에 이유가 있다.
 *
 * <p>배선이 빠지면 {@link DevProfileApplicationContextTest} 가 잡는다. 기동 검사기가 아니라
 * 그쪽에 둔 이유도 같다 — 검사기가 엔진과 같은 조건을 쓰면 엔진이 빠질 때 검사기도 함께
 * 빠져서, 감시하려던 실패에 감시자가 걸린다.
 */
// S15P21E201-808 — @ConditionalOnBean 을 걷어냈다.
//
// 리포지토리에 조건을 걸면 스캔 순서 문제를 피한다고 적어 뒀었는데, 실측해 보니 그렇지
// 않았다. dev 프로필 전체 앱에서도 이 빈이 안 만들어졌고, 그래서 이 엔진은 어떤 컨텍스트
// 에서도 붙은 적이 없다. 장소 표가 비어 있어 추천이 어차피 후보 0건이었기 때문에 그 사실이
// 드러나지 않았을 뿐이다.
//
// @ConditionalOnBean 은 자동 설정에서 쓰라고 만든 것이고, 사용자가 직접 스캔하는
// @Component 에서는 평가 시점이 스캔 순서에 달려 있다. 조건을 어디에 거느냐로는 그 문제를
// 못 피한다. 그래서 조건 자체를 없애고, 이 엔진이 필요로 하는 place 패키지를 안 올리던
// 슬라이스(RecommendationSliceApplication)에 그것을 더했다. 배선을 조건이 아니라 스캔
// 목록으로 정하면 "무엇이 올라오는가" 가 파일에 적혀 있어 읽는 사람이 확인할 수 있다.
@Component
@Profile({ "db", "dev" })
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

	/**
	 * 범위 안 후보가 이보다 적으면 출발지 기준으로 채운다 — S15P21E201-980.
	 *
	 * <p>하루에 네 곳씩 최대 이레를 배정하므로 스물여덟이 상한이고, 그 두 배쯤은 있어야
	 * 갈래를 섞어 고를 수 있다. 정확한 근거가 있는 값은 아니고 운영을 보고 조정할 값이다.
	 */
	private static final int MIN_AREA_CANDIDATES = 60;

	/** S15P21E201-338 — 복제 씨앗. 보통 여행은 비어 있어 아무 일도 하지 않는다({@link SeedBoost}). */
	private final TripSeedPlaceRepository seedPlaceRepository;

	/** S15P21E201-980 — 여행 범위. 안 고른 여행은 비어 있어 예전과 똑같이 돈다. */
	private final Optional<TripTravelAreaRepository> travelAreas;

	public BaselineRecommendationEngine(TripRepository tripRepository,
			PlaceCandidateQueryService placeCandidateQueryService, BaselineCandidateTranslator translator,
			BaselineCandidateScorer scorer, BaselineEngineProperties properties,
			PreferenceAlignmentWeights alignmentWeights, UserPlaceCodeMapRepository codeMapRepository,
			TripSeedPlaceRepository seedPlaceRepository,
			Optional<TripTravelAreaRepository> travelAreas) {
		this.tripRepository = tripRepository;
		this.placeCandidateQueryService = placeCandidateQueryService;
		this.translator = translator;
		this.scorer = scorer;
		this.properties = properties;
		this.alignmentWeights = alignmentWeights;
		this.codeMapRepository = codeMapRepository;
		this.seedPlaceRepository = seedPlaceRepository;
		this.travelAreas = travelAreas;
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
		PlaceCandidateResponse response = findCandidatesWithinTravelAreas(trip.tripId(), queryRequest);
		long candidateGenerationMs = elapsedMs(candidateGenerationStart);

		// 대조표는 배치당 한 번만 읽는다 — 후보마다 다시 읽으면 질의 수가 후보 수에 비례한다.
		List<UserPlaceCodeMap> preferenceCodeMap =
				this.codeMapRepository.findByIdUserInputKindOrderByIdUserInputCodeAsc(UserInputKind.PREFERENCE);
		List<UserPlaceCodeMap> constraintCodeMap =
				this.codeMapRepository.findByIdUserInputKindOrderByIdUserInputCodeAsc(UserInputKind.CONSTRAINT);

		// 🔴 S15P21E201-827 — 후보가 0곳이면 여기서 멈춘다.
		//
		//    이 검사가 없으면 아래 resolveDatasetVersion 이 빈 목록을 받아 null 을 내고,
		//    요청은 VERSION_UNRESOLVED 로 끝난다. 그것은 원인이 아니라 결과다 — 후보가
		//    없어서 수집분 이름을 못 정한 것인데, 그 코드만 보면 배포 설정이 잘못된 것처럼
		//    읽힌다. 2026-09-10 배포에서 실제로 그랬다(바다만 고른 요청).
		//
		//    무엇을 찾다가 비었는지 함께 남긴다. 갈래를 좁혀서 빈 것과 반경 안에 아무것도
		//    없어서 빈 것은 사람이 할 일이 다르다.
		if (response.candidates().isEmpty()) {
			String asked = queryRequest.categoriesOrEmpty().isEmpty() ? "갈래를 안 좁혔다"
					: "고른 갈래=" + String.join(",", queryRequest.categoriesOrEmpty());
			throw new RecommendationEngineException(RecommendationCodes.ERROR_NO_CANDIDATES,
					"반경 %dm 안에 조건에 해당하는 장소가 하나도 없다 — %s"
							.formatted(this.properties.radiusM(), asked));
		}

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

	/**
	 * 고른 여행 범위 안에서 후보를 고른다 — S15P21E201-980.
	 *
	 * <p>범위를 안 골랐으면 지금까지와 똑같다 — 출발지 하나를 중심으로 한 번 훑는다.
	 *
	 * <p>골랐으면 <b>지역마다 한 번씩</b> 훑어 합친다. 중심 하나에 반경을 키우는 방법은 쓸 수
	 * 없다 — 해운대와 남포동을 같이 고르면 그 둘을 다 덮는 원이 부산 전체가 되어, 범위를
	 * 골랐다는 말이 아무 뜻이 없어진다.
	 *
	 * <p>🔴 모자라면 출발지 기준 조회를 더해 채운다. 조건이 후보를 0곳으로 만들어 일정 생성이
	 * 통째로 실패하는 일이 이미 있었다(큰 짐 조건). 범위 때문에 같은 일이 나면 안 된다 —
	 * 범위는 "여기 위주로" 이지 "여기가 아니면 여행을 만들지 마라" 가 아니다.
	 *
	 * <p>점수 계산은 안 건드린다. 거리는 여전히 출발지 기준이고, 이 자리는 <b>무엇을 채점할
	 * 것인가</b>만 정한다 — 가중치는 S15P21E201-106·452 의 범위다.
	 */
	private PlaceCandidateResponse findCandidatesWithinTravelAreas(String tripId, PlaceCandidateRequest base) {
		List<TravelArea> areas = this.travelAreas.map((repository) -> repository.findByTripId(tripId))
				.orElse(List.of());
		if (areas.isEmpty()) {
			return this.placeCandidateQueryService.findCandidates(base);
		}

		Map<String, PlaceCandidateResponse.Candidate> merged = new LinkedHashMap<>();
		List<String> appliedFilters = new ArrayList<>(List.of("TRAVEL_AREA"));
		for (TravelArea area : areas) {
			PlaceCandidateRequest perArea = new PlaceCandidateRequest(
					new PlaceCandidateRequest.Center(area.lat(), area.lng()), area.radiusM(),
					base.categories(), base.requiredFeatures(), base.excludedFeatures(),
					base.openNowAt(), base.minimumCount(), base.limit());
			for (PlaceCandidateResponse.Candidate candidate
					: this.placeCandidateQueryService.findCandidates(perArea).candidates()) {
				merged.putIfAbsent(candidate.placeId().toString(), candidate);
			}
		}

		PlaceCandidateResponse fromAreas = this.placeCandidateQueryService.findCandidates(base);
		if (merged.size() < MIN_AREA_CANDIDATES) {
			// 🔴 범위 안이 비었다. 출발지 기준 후보로 채우고 그 사실을 남긴다 — 조용히 채우면
			//    "해운대를 골랐는데 왜 서면이 나오냐" 를 아무도 설명할 수 없다.
			for (PlaceCandidateResponse.Candidate candidate : fromAreas.candidates()) {
				merged.putIfAbsent(candidate.placeId().toString(), candidate);
			}
			appliedFilters.add("TRAVEL_AREA_WIDENED");
		}

		List<PlaceCandidateResponse.Candidate> candidates = List.copyOf(merged.values());
		return new PlaceCandidateResponse(candidates, candidates.size(), fromAreas.minimumRequired(),
				candidates.size() < fromAreas.minimumRequired(), appliedFilters, fromAreas.notApplied(),
				fromAreas.scanTruncated(), fromAreas.datasetVersions());
	}

}
