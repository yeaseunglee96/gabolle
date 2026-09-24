package com.gabolle.backend.recommendation.adapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.TreeSet;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.gabolle.backend.place.api.PlaceCandidateRequest;
import com.gabolle.backend.place.api.PlaceCandidateResponse;
import com.gabolle.backend.place.api.PlaceFeatureView;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.UserInputKind;
import com.gabolle.backend.place.domain.UserPlaceCodeMap;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.repository.UserPlaceCodeMapRepository;
import com.gabolle.backend.preference.domain.TasteWeightComponent;
import com.gabolle.backend.preference.repository.UserTasteVectorRepository;
import com.gabolle.backend.preference.repository.UserTasteWeightRepository;
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
import com.gabolle.backend.trip.domain.TripSeedPlace;
import com.gabolle.backend.trip.domain.TripSeedPlaceRepository;

import tools.jackson.databind.JsonNode;

/**
 * 규칙 기반 BASELINE 추천 엔진. 학습 모델·온톨로지 서버가 아직 없는 동안 이 엔진이
 * {@link RecommendationEnginePort} 자리를 채운다 ({@link FallbackMode#BASELINE}).
 *
 * {@code place} 패키지의 빈을 {@code @ConditionalOnBean} 으로 다루지 않는다. 그 애노테이션은
 * 자동 설정용이라 직접 스캔하는 {@code @Component} 에서는 평가 시점이 스캔 순서에 달려
 * 조용히 빠진다. 배선은 조건이 아니라 슬라이스의 스캔 목록으로 정한다.
 */
@Component
@Profile({ "db", "dev" })
public class BaselineRecommendationEngine implements RecommendationEnginePort {

	private static final Logger LOGGER = LoggerFactory.getLogger(BaselineRecommendationEngine.class);

	private final TripRepository tripRepository;

	private final PlaceCandidateQueryService placeCandidateQueryService;

	private final BaselineCandidateTranslator translator;

	private final BaselineCandidateScorer scorer;

	private final BaselineEngineProperties properties;

	/** 취향 다섯 차원이 {@code weights.preferenceAlignment} 를 나누는 비율. */
	private final PreferenceAlignmentWeights alignmentWeights;

	private final UserPlaceCodeMapRepository codeMapRepository;

	/**
	 * 범위 안 후보가 이보다 적으면 출발지 기준으로 채운다. 하루에 네 곳씩 최대 이레면 스물여덟이
	 * 상한이고 그 두 배쯤은 있어야 갈래를 섞어 고를 수 있다. 운영을 보고 조정할 값이다.
	 */
	private static final int MIN_AREA_CANDIDATES = 60;

	/** 빠진 씨앗을 자기 좌표로 찾을 때의 반경. 요청이 허용하는 가장 작은 값이다. */
	private static final int SEED_LOOKUP_RADIUS_M = 100;

	/** 같은 자리에 여러 장소가 겹쳐 있어도 자기 자신이 섞여 나오도록 한 줌만 받는다. */
	private static final int SEED_LOOKUP_LIMIT = 20;

	/** 반경 밖 씨앗을 끼워 넣었다는 표시. 응답의 appliedFilters 에 남는다. */
	private static final String SEED_INJECTED_FILTER = "SEED_PLACE_INJECTED";

	/** 씨앗 — 사용자가 적은 「꼭 가고 싶은 장소」와 공유 일정 복제. 보통 여행은 비어 있다({@link SeedBoost}). */
	private final TripSeedPlaceRepository seedPlaceRepository;

	/** 후보 풀 밖에 있는 씨앗의 좌표를 찾을 때만 쓴다 ({@link #includeMissingSeeds}). */
	private final PlaceRepository placeRepository;

	/** 여행 범위. 안 고른 여행은 비어 있어 범위가 없던 때와 똑같이 돈다. */
	private final Optional<TripTravelAreaRepository> travelAreas;

	/**
	 * 접힌 취향 벡터를 읽는 통로. 이 클래스를 띄우는 시험 슬라이스 대부분이 {@code preference}
	 * 패키지를 스캔하지 않아 그냥 받으면 컨텍스트 로딩에서 죽는다. 없으면 빈 목록으로 보고 —
	 * 벡터가 없는 사람과 같은 취급이라 채점이 지금과 완전히 같다.
	 */
	private final ObjectProvider<UserTasteVectorRepository> tasteVectors;

	private final ObjectProvider<UserTasteWeightRepository> tasteWeightRepository;

	public BaselineRecommendationEngine(TripRepository tripRepository,
			PlaceCandidateQueryService placeCandidateQueryService, BaselineCandidateTranslator translator,
			BaselineCandidateScorer scorer, BaselineEngineProperties properties,
			PreferenceAlignmentWeights alignmentWeights, UserPlaceCodeMapRepository codeMapRepository,
			TripSeedPlaceRepository seedPlaceRepository, PlaceRepository placeRepository,
			Optional<TripTravelAreaRepository> travelAreas,
			ObjectProvider<UserTasteVectorRepository> tasteVectors,
			ObjectProvider<UserTasteWeightRepository> tasteWeightRepository) {
		this.tripRepository = tripRepository;
		this.placeCandidateQueryService = placeCandidateQueryService;
		this.translator = translator;
		this.scorer = scorer;
		this.properties = properties;
		this.alignmentWeights = alignmentWeights;
		this.codeMapRepository = codeMapRepository;
		this.seedPlaceRepository = seedPlaceRepository;
		this.placeRepository = placeRepository;
		this.travelAreas = travelAreas;
		this.tasteVectors = tasteVectors;
		this.tasteWeightRepository = tasteWeightRepository;
	}

	/**
	 * 이 사용자의 현재 판 성분들. 빈 목록은 정상이다 — 빈으로 못 올라온 슬라이스, 아직 접힌
	 * 적 없는 사용자, 사용자를 모르는 요청이 모두 여기로 온다.
	 *
	 * <p>🔴 <b>저장된 행을 그대로 내보내지 않는다</b> (S15P21E201-1499). 근거가 키의 일부라
	 * 한 성분이 설문 행과 행동 행으로 나뉘어 앉아 있다. 그대로 넘기면 채점기가 같은 성분을 두
	 * 번 세므로, 여기서 {@code (차원, 코드)} 마다 하나로 합쳐 내보낸다. 읽어 넘기는 자리가
	 * 여기 하나뿐이라 합치는 것도 여기 한 곳이면 된다.
	 */
	private List<TasteWeightComponent> currentTasteWeights(UUID userId) {
		UserTasteVectorRepository vectors = this.tasteVectors.getIfAvailable();
		UserTasteWeightRepository weights = this.tasteWeightRepository.getIfAvailable();
		if (userId == null || vectors == null || weights == null) {
			return List.of();
		}
		return vectors.findByUserIdAndSupersededAtIsNull(userId)
				.map((vector) -> TasteWeightComponent.merge(weights.findByIdTasteVectorId(vector.getTasteVectorId())))
				.orElseGet(List::of);
	}

	@Override
	public EngineCandidateBatch generate(EngineRequest request) {
		Trip trip = loadTrip(request.tripId());

		// 요청이 준 현재 위치가 있으면 그것이 중심이고, 없으면 여행 출발지를 쓴다. 수기 입력
		// (MANUAL)은 GPS 와 똑같이 다뤄진다 — 거리 계산에 들어가는 값은 어느 쪽이든 좌표 하나다.
		RequestLocation location = (request.location() != null) ? request.location()
				: RequestLocation.ofTripOrigin(trip.originLat(), trip.originLng(), trip.createdAt());
		if (location == null) {
			// 중심 좌표를 지어내지 않는다 — 결과가 왜 이상한지 아무도 못 찾게 된다.
			throw new RecommendationEngineException("ENGINE_ORIGIN_MISSING",
					"요청에 현재 위치가 없고 여행에도 출발지 좌표가 없다: tripId=" + request.tripId());
		}

		// findLatestSnapshot·findConstraints 를 쓰지 않는다 — 추천 Job 이 기록해 둔 그 판을
		// 읽어야 한다. 실행이 비동기라 그 사이 사용자가 취향·제약을 다시 답했을 수 있다.
		PreferenceSnapshot preferenceSnapshot = (request.preferenceSnapshotId() == null) ? null
				: this.tripRepository.findSnapshotById(request.preferenceSnapshotId().toString()).orElse(null);
		List<TripConstraint> constraints = (request.constraintSnapshotId() == null) ? List.of()
				: this.tripRepository.findConstraintsBySnapshotId(request.constraintSnapshotId().toString());

		long candidateGenerationStart = System.nanoTime();
		PlaceCandidateRequest queryRequest =
				this.translator.translate(location, trip, preferenceSnapshot, constraints);
		PlaceCandidateResponse response = findCandidatesWithinTravelAreas(trip.tripId(), queryRequest);
		// 사용자가 적은 「꼭 가고 싶은 장소」가 반경 밖이면 여기까지 안 들어온다. 끼워 넣는다.
		List<TripSeedPlace> seeds = this.seedPlaceRepository.findByTripId(trip.tripId());
		response = includeMissingSeeds(response, seeds);
		response = withoutClosedEvents(response, trip, seeds);
		long candidateGenerationMs = elapsedMs(candidateGenerationStart);

		// 대조표는 배치당 한 번만 읽는다 — 후보마다 다시 읽으면 질의 수가 후보 수에 비례한다.
		List<UserPlaceCodeMap> preferenceCodeMap =
				this.codeMapRepository.findByIdUserInputKindOrderByIdUserInputCodeAsc(UserInputKind.PREFERENCE);
		List<UserPlaceCodeMap> constraintCodeMap =
				this.codeMapRepository.findByIdUserInputKindOrderByIdUserInputCodeAsc(UserInputKind.CONSTRAINT);

		// 취향 벡터도 대조표와 같은 이유로 요청당 한 번만 읽는다.
		List<TasteWeightComponent> tasteWeights = currentTasteWeights(request.userId());

		// 후보가 0곳이면 여기서 멈춘다. 이 검사가 없으면 아래 resolveDatasetVersion 이 빈
		// 목록을 받아 null 을 내고 요청이 VERSION_UNRESOLVED 로 끝나는데, 그것은 원인이 아니라
		// 결과라서 배포 설정이 잘못된 것처럼 읽힌다. 무엇을 찾다가 비었는지도 함께 남긴다 —
		// 갈래를 좁혀서 빈 것과 반경 안에 아무것도 없어서 빈 것은 사람이 할 일이 다르다.
		if (response.candidates().isEmpty()) {
			String asked = queryRequest.categoriesOrEmpty().isEmpty() ? "갈래를 안 좁혔다"
					: "고른 갈래=" + String.join(",", queryRequest.categoriesOrEmpty());
			throw new RecommendationEngineException(RecommendationCodes.ERROR_NO_CANDIDATES,
					"반경 %dm 안에 조건에 해당하는 장소가 하나도 없다 — %s"
							.formatted(this.properties.radiusM(), asked));
		}

		if (response.scanTruncated()) {
			// 잘렸다는 것은 반경 안인데 채점조차 안 된 장소가 있다는 뜻이다. 조용히 넘기면
			// 나중에 결과가 이상해도 원인을 못 찾는다.
			LOGGER.warn("후보 조회가 상한에 걸려 잘렸다 — tripId={} 반경={}m 받은 후보={}곳. "
					+ "gabolle.place.candidate-max-scanned 를 올려야 반경 안 장소가 전부 채점된다",
					request.tripId(), this.properties.radiusM(), response.candidates().size());
		}

		long rankingStart = System.nanoTime();
		List<EngineCandidate> candidates = new ArrayList<>(response.candidates().size());
		for (PlaceCandidateResponse.Candidate candidate : response.candidates()) {
			candidates.add(this.scorer.score(candidate, preferenceSnapshot, constraints, this.properties.radiusM(),
					this.properties.weights(), this.alignmentWeights, preferenceCodeMap, constraintCodeMap,
					tasteWeights, this.properties.tasteVectorMultiplier()));
		}
		// 씨앗을 앞세운다. 점수만 올리고 제약 판정은 그대로다 — SeedBoost 참고.
		candidates = SeedBoost.apply(candidates, seeds);
		// 총예산에 맞춘다. 역시 점수만 움직이고 후보를 빼지 않는다 — BudgetFit 참고.
		candidates = BudgetFit.apply(candidates, priceBandsOf(response), BudgetFit.targetBand(trip));
		// 자르기는 채점을 마친 뒤다. 갈래를 골랐으면 그 갈래·끼니·쉼 갈래에 몫을 먼저 준다({@link #keepWithCategoryShares}).
		// 갈래를 안 골랐으면 뒤쪽을 여행마다 다르게 채운다 (S15P21E201-1463).
		// 🔴 고른 갈래는 질의가 아니라 취향에서 읽는다 — 질의는 갈래로 좁히지 않아(-1535) 늘 비어 있고, 그 탓에
		//    「안 골랐다」가 모든 여행에 참이 되어 있었다.
		List<String> chosenCategories = this.scorer.chosenCategories(preferenceSnapshot);
		candidates = keepBestScoring(candidates, this.properties.candidateLimit(), chosenCategories, request.tripId());
		long rankingMs = elapsedMs(rankingStart);

		String datasetVersion = resolveDatasetVersion(response.datasetVersions());

		EngineVersions versions = new EngineVersions(this.properties.modelVersion(), this.properties.featureVersion(),
				this.properties.ontologyVersion(), this.properties.policyVersion(), datasetVersion);
		EngineLatencies latencies = new EngineLatencies(candidateGenerationMs, null, null, rankingMs, null);

		// 실제로 쓴 출발지를 함께 돌려준다 — 그 선택을 아는 것은 엔진뿐이다.
		return new EngineCandidateBatch(candidates, versions, latencies, FallbackMode.BASELINE,
				"NO_MODEL_ENGINE", location);
	}

	/**
	 * 무작위로 채우는 자리가 상한에서 차지하는 몫. 위쪽 절반은 점수 순으로 그대로 지킨다.
	 *
	 * <p>🔴 <b>전부 섞으면 가장 잘 맞는 곳이 빠질 수 있다.</b> 카테고리를 안 골랐어도 음식·분위기
	 * 같은 다른 취향은 골랐을 수 있고, 그 사람에게 1등을 떨어뜨리는 것은 다양성이 아니라 손해다.
	 * 그래서 앞쪽은 고정하고 <b>뒤쪽만</b> 넓은 풀에서 골라 채운다.
	 */
	private static final double ANCHOR_SHARE = 0.5;

	/**
	 * 뒤쪽을 채울 때 들여다보는 풀의 크기 = {@code limit} 의 몇 배인가.
	 *
	 * <p>값 자체가 실험 대상이라 설정으로 빼지 않고 상수로 둔다 — {@code MOBILITY_WARNING_PENALTY}
	 * 와 같은 이유다. 너무 크면 점수가 한참 낮은 곳까지 같은 확률로 올라오고, 1이면 섞을 것이 없다.
	 */
	private static final int VARIETY_POOL_FACTOR = 3;

	/**
	 * 점수 높은 순으로 {@code limit} 개만 남긴다. 자르는 자리가 채점 뒤여야 한다 —
	 * {@code candidateLimit} 을 {@code PlaceCandidateQueryService} 로 넘기면 그쪽은 점수를
	 * 모르므로 거리순으로 잘라, 상한이 "가까운 순 N곳만 채점 대상" 이 된다. 동점은
	 * {@code placeId} 로 가른다 — 순서가 실행마다 달라지면 나중에 비교할 수 없다.
	 * 점수가 낮으면 탈락 판정 후보도 지켜지지 않는 것은 알려진 한계다.
	 *
	 * <p>🔴 <b>갈래를 안 고른 사람에게는 뒤쪽을 여행마다 다르게 채운다</b> (S15P21E201-1463).
	 * 이 엔진에는 무작위가 하나도 없어서, 조건이 비슷하면 <b>늘 같은 곳이 같은 순서로</b> 나왔다.
	 * 갈래를 고른 사람은 그 뜻이 점수에 실리지만 안 고른 사람에게는 개성이 들어갈 자리가 없다.
	 *
	 * <p>🔴 <b>씨앗은 {@code tripId} 다. 그냥 난수가 아니다.</b> 이 저장소의 추천은 재현
	 * 가능하게 만들어져 있고(엔진 버전 다섯을 들고 다니는 이유가 그것이다), 난수를 쓰면 같은
	 * 여행을 다시 열 때마다 다른 곳이 나와 <b>문제가 생겨도 그 결과를 다시 만들어 볼 수 없다.</b>
	 * 씨앗을 여행 번호로 고정하면 여행마다 다르면서 같은 여행은 늘 같다.
	 *
	 * <p>🔴 <b>들어온 뒤의 순서는 점수 순이다.</b> 무작위는 「어느 곳이 들어오나」에만 쓴다 —
	 * {@code ItineraryDraftCommand.places} 의 계약이 「rank 오름차순」이고, 화면도 앞쪽을 더 잘
	 * 맞는 곳으로 읽는다.
	 *
	 * @param chosenCategories 사용자가 고른 갈래. 비었으면 뒤쪽을 섞고, 골랐으면 섞지 않고 갈래 몫을 먼저 준다 —
	 *     「카페를 골랐는데 카페가 적네」가 생기면 안 된다
	 * @param seed 섞기의 씨앗. 같은 값이면 같은 결과다
	 */
	private static List<EngineCandidate> keepBestScoring(List<EngineCandidate> candidates, int limit,
			List<String> chosenCategories, UUID seed) {
		if (candidates.size() <= limit) {
			return candidates;
		}
		List<EngineCandidate> sorted = new ArrayList<>(candidates);
		sorted.sort(scoreOrder());
		if (!chosenCategories.isEmpty()) {
			return keepWithCategoryShares(sorted, limit, chosenCategories);
		}

		int anchor = Math.max(1, (int) Math.round(limit * ANCHOR_SHARE));
		int poolEnd = Math.min(sorted.size(), Math.max(limit, limit * VARIETY_POOL_FACTOR));
		List<EngineCandidate> tail = new ArrayList<>(sorted.subList(anchor, poolEnd));
		// 씨앗이 같으면 같은 순서가 나온다. 들어오는 목록도 점수 순으로 정해져 있어야 그렇다.
		Collections.shuffle(tail, new Random(seed == null ? 0L : seed.getMostSignificantBits() ^ seed.getLeastSignificantBits()));

		List<EngineCandidate> kept = new ArrayList<>(sorted.subList(0, anchor));
		kept.addAll(tail.subList(0, Math.min(limit - anchor, tail.size())));
		// 고르기는 섞어서 했어도 내보내는 순서는 점수 순이다.
		kept.sort(scoreOrder());
		return kept;
	}

	/**
	 * 고른 갈래가 남기는 후보에서 차지하는 몫의 합. 앱이 「고른 갈래가 하루 정차지의 약 70%를 차지해요」라고
	 * 약속한다(frontend {@code planOptions.ts}) — 후보에 그만큼이 없으면 그 약속을 일정이 지킬 수 없다.
	 */
	static final double CHOSEN_SHARE = 0.7;

	/** 고르지 않았어도 끼니 자리를 채울 밥집의 몫. 바다·자연만 고른 사람도 밥은 먹는다. */
	static final double MEAL_SHARE = 0.15;

	/** 고르지 않았어도 쉼 자리(카페)의 몫. 일정이 카페를 하루 한 곳까지 앉힌다(S15P21E201-1573). */
	static final double REST_SHARE = 0.05;

	static final String MEAL_CATEGORY = "FOOD";

	static final String REST_CATEGORY = "CAFE_HEALING";

	/**
	 * 갈래를 고른 사람의 자르기 — 갈래마다 최소 몫을 점수 순으로 먼저 채우고, 남은 자리를 전체 점수 순으로 채운다.
	 *
	 * <p>🔴 <b>왜.</b> 고른 갈래는 채점에서 «가산점» 이다(-1535). 그런데 운영 장소는 밥집이 압도적이라(광안리+해운대
	 * 범위: 밥집 1,074 · 카페 156 · 자연 10 · 바다 2) 맛집도 고르면 수천 곳이 같은 가산점을 받고, 밥집에만 붙는
	 * 인기도·예산 가산까지 얹혀 상위 200 을 밥집이 다 채웠다. 바다·자연·맛집을 고른 운영 여행의 후보가
	 * <b>밥집 199 · 꼭 갈 곳 1</b> 이었고 코스 셋이 전부 식당으로 찼다(2026-09-24).
	 *
	 * <p>한 갈래의 후보가 몫보다 적으면 있는 만큼만 넣는다 — 바다가 2곳이면 2곳이다. 몫의 합이 {@code limit} 의
	 * 90% 이하라 점수 순으로 채우는 자리가 늘 남는다(꼭 갈 곳은 점수가 맨 위라 거기서 들어온다).
	 */
	private static List<EngineCandidate> keepWithCategoryShares(List<EngineCandidate> sorted, int limit,
			List<String> chosenCategories) {
		Map<String, Integer> shares = new LinkedHashMap<>();
		int perChosen = Math.max(1, (int) (limit * CHOSEN_SHARE / chosenCategories.size()));
		for (String category : chosenCategories) {
			shares.put(category, perChosen);
		}
		shares.putIfAbsent(MEAL_CATEGORY, Math.max(1, (int) (limit * MEAL_SHARE)));
		shares.putIfAbsent(REST_CATEGORY, Math.max(1, (int) (limit * REST_SHARE)));

		Map<UUID, EngineCandidate> kept = new LinkedHashMap<>();
		Map<String, Integer> taken = new HashMap<>();
		for (EngineCandidate candidate : sorted) {
			Object category = candidate.featureValues() == null ? null : candidate.featureValues().get("category");
			Integer share = (category == null) ? null : shares.get(category.toString());
			if (share != null && taken.getOrDefault(category.toString(), 0) < share) {
				kept.put(candidate.placeId(), candidate);
				taken.merge(category.toString(), 1, Integer::sum);
			}
		}
		for (EngineCandidate candidate : sorted) {
			if (kept.size() >= limit) {
				break;
			}
			kept.putIfAbsent(candidate.placeId(), candidate);
		}
		List<EngineCandidate> out = new ArrayList<>(kept.values());
		// 몫으로 골랐어도 내보내는 순서는 점수 순이다 — ItineraryDraftCommand.places 의 계약.
		out.sort(scoreOrder());
		return out;
	}

	/** 점수 내림차순, 동점은 {@code placeId}. 두 곳에서 같은 순서를 써야 해서 따로 뺐다. */
	private static Comparator<EngineCandidate> scoreOrder() {
		return Comparator
				// 점수가 없는 후보를 0 으로 치지 않는다 — 없는 것과 낮은 것은 다르다. 맨 뒤로
				// 보내되 자리가 남으면 들어온다.
				.comparing(EngineCandidate::preRankScore,
						Comparator.nullsLast(Comparator.<Double>reverseOrder()))
				.thenComparing(EngineCandidate::placeId);
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
	 * {@code datasetVersion} 은 설정이 아니라 실제로 조회된 장소들이 어느 수집분에서 왔는지로
	 * 정한다. 비어 있으면 {@code null} 을 그대로 돌려주고 {@code RecommendationService} 가
	 * {@code VERSION_UNRESOLVED} 로 요청을 실패시킨다.
	 */
	String resolveDatasetVersion(List<String> datasetVersions) {
		if (datasetVersions == null || datasetVersions.isEmpty()) {
			return null;
		}
		// TreeSet 이 정렬과 중복 제거를 한 번에 한다.
		String joined = String.join(",", new TreeSet<>(datasetVersions));
		if (joined.length() <= DATASET_VERSION_MAX) {
			return joined;
		}
		return digestOf(joined);
	}

	/** {@code dataset_version} 컬럼 폭. 마이그레이션 다섯 곳이 모두 VARCHAR(100) 이다. */
	private static final int DATASET_VERSION_MAX = 100;

	/** 지문임을 값만 보고도 알 수 있게 붙이는 머리말. */
	private static final String DATASET_VERSION_DIGEST_PREFIX = "sha256:";

	/**
	 * 이어 붙인 값이 칸보다 길면 지문으로 줄여서 적는다. 자르면 다른 수집분 조합과 겹쳐
	 * 보이지만 지문은 조합이 다르면 값도 다르다. 짧을 때는 사람이 읽는 값이 그대로 들어가므로
	 * 이미 쌓인 값들의 뜻이 바뀌지 않는다.
	 */
	private String digestOf(String joined) {
		String hex;
		try {
			byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
					.digest(joined.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(32);
			// 16바이트(32자)면 충돌은 현실에서 일어나지 않는다. 머리말까지 39자라 칸에 넉넉히 든다.
			for (int i = 0; i < 16; i++) {
				sb.append(String.format("%02x", bytes[i]));
			}
			hex = sb.toString();
		}
		catch (java.security.NoSuchAlgorithmException e) {
			// SHA-256 은 모든 JVM 이 갖고 있어야 하는 알고리즘이다. 없으면 환경이 깨진 것이고,
			// 그것을 추천 실패로 덮지 않는다.
			throw new IllegalStateException("SHA-256 을 쓸 수 없다 — JVM 설치가 온전하지 않다", e);
		}
		String digest = DATASET_VERSION_DIGEST_PREFIX + hex;
		LOGGER.info("datasetVersion 이 {}자를 넘어 지문으로 적는다({}자). digest={} 원문={}",
				DATASET_VERSION_MAX, joined.length(), digest, joined);
		return digest;
	}

	private static long elapsedMs(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000L;
	}

	/**
	 * 고른 여행 범위 안에서 후보를 고른다. 범위를 안 골랐으면 출발지 하나를 중심으로 한 번
	 * 훑고, 골랐으면 지역마다 한 번씩 훑어 합친다 — 중심 하나에 반경을 키우면 떨어진 두 곳을
	 * 덮는 원이 도시 전체가 되어 범위를 골랐다는 말이 뜻을 잃는다. 모자라면 출발지 기준
	 * 조회로 채운다. 거리는 여전히 출발지 기준이고 이 자리는 무엇을 채점할 것인가만 정한다.
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
			// 범위 안이 비었다. 출발지 기준 후보로 채우고 그 사실을 appliedFilters 에 남긴다 —
			// 조용히 채우면 "해운대를 골랐는데 왜 서면이 나오냐" 를 설명할 수 없다.
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

	/**
	 * 후보 풀에 없는 씨앗을 풀에 끼워 넣는다.
	 *
	 * <p><b>왜 필요한가.</b> 후보는 출발지·여행 범위를 중심으로 한 반경
	 * ({@code gabolle.recommendation.baseline.radius-m}, 기본 5,000m) 안에서만 뽑는다. 부산은
	 * 동서로 30km 가 넘어서, 서면에서 출발하는 여행에 해운대를 「꼭 가고 싶은 장소」로 적으면
	 * 그 장소는 후보에 들어오지도 못하고 {@link SeedBoost} 도 볼 수 없다. 사용자가 이름을 직접
	 * 적어 넣은 유일한 답이 조용히 사라지는 자리였다.
	 *
	 * <p><b>어떻게.</b> 빠진 장소를 <b>그 장소 자신을 중심으로</b> 다시 조회한다. 원래 조회의
	 * 갈래·표식 조건은 걸지 않는다 — 이름을 적어 넣은 곳을 갈래로 거를 이유가 없고, 제약 판정은
	 * 뒤에서 채점기와 {@code CandidateAssembler} 가 그대로 한다(알레르기 같은 하드 필터에
	 * 걸리는 곳은 지금처럼 빠진다). 여기서 하는 일은 <b>판정할 기회를 주는 것</b>뿐이다.
	 *
	 * <p>좌표를 모르는 장소는 넣지 않는다 — 거리 점수를 매길 수 없고, 0 을 넣으면 "출발지에
	 * 붙어 있다" 는 다른 주장이 된다.
	 */
	private PlaceCandidateResponse includeMissingSeeds(PlaceCandidateResponse response, List<TripSeedPlace> seeds) {
		if (seeds.isEmpty()) {
			return response;
		}
		Set<UUID> inPool = new LinkedHashSet<>();
		for (PlaceCandidateResponse.Candidate candidate : response.candidates()) {
			inPool.add(candidate.placeId());
		}
		List<UUID> missing = new ArrayList<>();
		for (TripSeedPlace seed : seeds) {
			UUID placeId = UUID.fromString(seed.placeId());
			if (!inPool.contains(placeId)) {
				missing.add(placeId);
			}
		}
		if (missing.isEmpty()) {
			return response;
		}

		List<PlaceCandidateResponse.Candidate> added = new ArrayList<>();
		for (Place place : this.placeRepository.findByPlaceIdIn(missing)) {
			if (!place.hasCoordinates()) {
				LOGGER.warn("씨앗 장소에 좌표가 없어 후보에 못 넣었다 — placeId={}", place.getPlaceId());
				continue;
			}
			findSelf(place).ifPresent(added::add);
		}
		if (added.isEmpty()) {
			return response;
		}

		List<PlaceCandidateResponse.Candidate> merged = new ArrayList<>(response.candidates());
		merged.addAll(added);
		List<String> appliedFilters = new ArrayList<>(response.appliedFilters());
		// 조용히 넣지 않는다. 뒤에서 "반경 밖 장소가 왜 여기 있냐" 를 설명할 수 있어야 한다.
		appliedFilters.add(SEED_INJECTED_FILTER);
		return new PlaceCandidateResponse(merged, merged.size(), response.minimumRequired(),
				merged.size() < response.minimumRequired(), appliedFilters, response.notApplied(),
				response.scanTruncated(), response.datasetVersions());
	}

	/** 여행 날짜에 하루도 안 여는 행사 장소를 뺐다는 표시. 응답의 appliedFilters 에 남는다. */
	static final String EVENT_DATES_FILTER = "EVENT_OPEN_ON_TRIP_DATES";

	/**
	 * 기간표가 있는데 여행 기간에 하루도 안 여는 장소(끝난 축제·아직 안 한 축제)를 후보에서 뺀다 (S15P21E201-1618).
	 *
	 * <p>🔴 전에는 이 거르기가 일정 조립에만 있었다({@code ItineraryDraftService.eventDaysOf}). 축제는 갈래가 비어
	 * 후보에 아예 안 들어와서 문제가 안 됐는데, 축제 갈래(FESTIVAL_EVENT)를 채우면 날짜가 안 맞는 축제가 추천 결과
	 * 목록에 뜬다. 조립과 같은 판정이고, 조립처럼 사용자가 직접 고른 「꼭 갈 곳」은 빼지 않는다.
	 */
	private PlaceCandidateResponse withoutClosedEvents(PlaceCandidateResponse response, Trip trip,
			List<TripSeedPlace> seeds) {
		if (response.candidates().isEmpty()) {
			return response;
		}
		Set<UUID> mustVisit = new HashSet<>();
		for (TripSeedPlace seed : seeds) {
			mustVisit.add(UUID.fromString(seed.placeId()));
		}
		List<UUID> ids = response.candidates().stream()
				.map(PlaceCandidateResponse.Candidate::placeId)
				.filter((id) -> !mustVisit.contains(id))
				.toList();
		if (ids.isEmpty()) {
			return response;
		}
		Set<UUID> closed = new HashSet<>(
				this.placeRepository.findEventPlacesClosedThroughout(ids, trip.startDate(), trip.finishDate()));
		if (closed.isEmpty()) {
			return response;
		}
		List<PlaceCandidateResponse.Candidate> open = response.candidates().stream()
				.filter((candidate) -> !closed.contains(candidate.placeId()))
				.toList();
		List<String> appliedFilters = new ArrayList<>(response.appliedFilters());
		appliedFilters.add(EVENT_DATES_FILTER);
		return new PlaceCandidateResponse(open, open.size(), response.minimumRequired(),
				open.size() < response.minimumRequired(), appliedFilters, response.notApplied(),
				response.scanTruncated(), response.datasetVersions());
	}

	/**
	 * 장소 하나를 자기 좌표를 중심으로 다시 조회해서 후보 모양으로 받아 온다.
	 * 직접 만들지 않고 조회를 거치는 이유는, 채점기가 보는 표식 값들이 이 조회에서 채워지기
	 * 때문이다 — 손으로 만들면 표식이 빈 후보가 되어 취향 점수가 통째로 0 이 된다.
	 */
	private Optional<PlaceCandidateResponse.Candidate> findSelf(Place place) {
		PlaceCandidateRequest self = new PlaceCandidateRequest(
				new PlaceCandidateRequest.Center(place.getLat(), place.getLng()), SEED_LOOKUP_RADIUS_M,
				null, null, null, null, null, SEED_LOOKUP_LIMIT);
		for (PlaceCandidateResponse.Candidate candidate : this.placeCandidateQueryService.findCandidates(self)
				.candidates()) {
			if (candidate.placeId().equals(place.getPlaceId())) {
				return Optional.of(candidate);
			}
		}
		LOGGER.warn("씨앗 장소를 자기 좌표로도 못 찾았다 — placeId={}", place.getPlaceId());
		return Optional.empty();
	}

	/**
	 * 후보마다의 가격대. 출처가 둘이고 <b>사람이 매긴 등급이 먼저</b>다.
	 *
	 * <ul>
	 * <li>{@code PRICE_LEVEL} — {@code {"band":"MID","raw":"mid"}} 모양. {@code band} 만 꺼낸다
	 * <li>{@code MENU_PRICE_WON} — {@code {"priceWon":39000,"menu":"…"}} 모양. 원 단위 값을
	 *     {@link BudgetFit#bandOfWon} 이 등급으로 접는다
	 * </ul>
	 *
	 * <p>🔴 <b>왜 둘째가 필요한가.</b> 2026-09-22 실측으로 운영에 {@code PRICE_LEVEL} 은
	 * <b>0행</b>이다. 그래서 이 표가 늘 비었고, {@link BudgetFit#apply} 가 첫 줄에서 그대로
	 * 돌아 나가 <b>예산이 순위에 한 번도 안 닿았다</b>(S15P21E201-1495). 실제로 실려 있는 것은
	 * {@code MENU_PRICE_WON} 189곳이다.
	 *
	 * <p>가격을 모르는 곳은 <b>표에 열쇠를 만들지 않는다</b> — 「모른다」를 빈 문자열이나 기본
	 * 등급으로 채우면 조사 안 된 곳이 특정 등급인 것처럼 점수를 받는다. 값이 있는 곳이 아직
	 * 6,866곳 중 189곳뿐이라 이 구분이 특히 중요하다.
	 */
	private static Map<UUID, String> priceBandsOf(PlaceCandidateResponse response) {
		Map<UUID, String> bandByPlace = new LinkedHashMap<>();
		for (PlaceCandidateResponse.Candidate candidate : response.candidates()) {
			String band = null;
			String fromWon = null;
			for (PlaceFeatureView feature : candidate.features()) {
				if (feature.value() == null) {
					continue;
				}
				if (BudgetFit.FEATURE_TYPE.equals(feature.featureType())) {
					String value = feature.value().path("band").asText("");
					if (!value.isBlank()) {
						band = value;
						// 사람이 매긴 등급이 있으면 더 볼 것이 없다.
						break;
					}
				}
				else if (BudgetFit.WON_FEATURE_TYPE.equals(feature.featureType()) && fromWon == null) {
					JsonNode won = feature.value().path("priceWon");
					if (won.isNumber()) {
						fromWon = BudgetFit.bandOfWon(won.asInt());
					}
				}
			}
			String resolved = (band != null) ? band : fromWon;
			if (resolved != null) {
				bandByPlace.put(candidate.placeId(), resolved);
			}
		}
		return bandByPlace;
	}

}
