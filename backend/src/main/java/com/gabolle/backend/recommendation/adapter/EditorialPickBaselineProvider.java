package com.gabolle.backend.recommendation.adapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.editorial.domain.EditorialPick;
import com.gabolle.backend.editorial.domain.EditorialPickPlace;
import com.gabolle.backend.editorial.domain.EditorialPickScope;
import com.gabolle.backend.editorial.domain.EditorialPickStatus;
import com.gabolle.backend.editorial.repository.EditorialPickPlaceRepository;
import com.gabolle.backend.editorial.repository.EditorialPickRepository;
import com.gabolle.backend.place.api.PlaceCandidateResponse;
import com.gabolle.backend.place.api.PlaceFeatureView;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.domain.UserInputKind;
import com.gabolle.backend.place.domain.UserPlaceCodeMap;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.repository.UserPlaceCodeMapRepository;
import com.gabolle.backend.recommendation.application.RecommendationCodes;
import com.gabolle.backend.recommendation.config.BaselineEngineProperties;
import com.gabolle.backend.recommendation.domain.FallbackMode;
import com.gabolle.backend.trip.domain.TripConstraint;
import com.gabolle.backend.trip.domain.TripRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 발행된 Editor's Pick 하나를 <b>제약 판정까지 끝난 후보 묶음</b>으로 바꾼다
 * (S15P21E201-555).
 *
 * <p>취향을 전부 건너뛴 계정과 추천 엔진이 죽은 요청에 빈 화면 대신 내보낼 기준선이다.
 * FR-REC-03 · FR-REC-11 · FR-REC-15.
 *
 * <p><b>{@link RecommendationEnginePort} 를 구현하지 않는다.</b> 그 포트의 빈이 둘이
 * 되면 {@code RecommendationService} 의 {@code ObjectProvider.getIfAvailable()} 이 어느
 * 것을 줄지 정할 수 없어 터진다. 이것은 엔진의 대안이 아니라 <b>엔진이 실패한 뒤에</b>
 * 부르는 별개의 통로다.
 *
 * <p>{@code @ConditionalOnBean} 을 남긴 이유는 애노테이션 위 주석에 있다 — S15P21E201-808
 * 에서 엔진 쪽은 조건을 걷어냈고 이 클래스만 남겼다.
 */
@Component
@Profile({ "db", "dev" })
// S15P21E201-808 — 조건을 EditorialPickRepository 로 바꿨다.
//
// 엔진 쪽 셋(BaselineRecommendationEngine · BaselineCandidateTranslator ·
// BaselineEngineStartupValidator)에서는 이 조건을 걷어내고 슬라이스의 스캔 목록으로
// 배선을 정했다. 이 클래스만 조건을 남기는 이유는 기대는 대상이 다르기 때문이다 —
// 앞의 셋은 place 를 필요로 하고 이 클래스는 editorial 을 필요로 하는데, editorial 은
// 추천 경로의 필수 조각이 아니라 후보가 없을 때의 대체 목록이다. 그 패키지를 안 올리는
// 컨텍스트가 66개 검사에 걸쳐 있고, 그쪽까지 스캔을 넓히면 "인증만 올린다" 같은
// 슬라이스의 뜻이 사라진다.
//
// 대신 조건 대상을 자기가 실제로 기대는 것(EditorialPickRepository)으로 바꿔서, 조건이
// 참인데 빈이 없는 상태가 안 생기게 한다.
@ConditionalOnBean(EditorialPickRepository.class)
public class EditorialPickBaselineProvider {

	/** {@code EngineCandidate.candidateSource} — 어디서 왔는지 알 수 있는 값. */
	static final String CANDIDATE_SOURCE = "EDITORIAL_PICK";

	/**
	 * 🔴 출발지 기준 거리를 봐야 하는 제약. Pick 에는 그 값이 없어서 판정을 건너뛴다
	 * (건너뛴 사실은 {@link RecommendationCodes#WARNING_WALKING_LIMIT_NOT_CHECKED} 로 남는다).
	 */
	private static final String DISTANCE_DEPENDENT_CONSTRAINT_KEY = "MAX_WALKING_METERS";

	private final EditorialPickRepository pickRepository;

	private final EditorialPickPlaceRepository pickPlaceRepository;

	private final PlaceRepository placeRepository;

	private final PlaceFeatureRepository placeFeatureRepository;

	private final UserPlaceCodeMapRepository codeMapRepository;

	private final BaselineCandidateScorer scorer;

	private final BaselineEngineProperties properties;

	private final TripRepository tripRepository;

	private final ObjectMapper objectMapper;

	public EditorialPickBaselineProvider(EditorialPickRepository pickRepository,
			EditorialPickPlaceRepository pickPlaceRepository, PlaceRepository placeRepository,
			PlaceFeatureRepository placeFeatureRepository, UserPlaceCodeMapRepository codeMapRepository,
			BaselineCandidateScorer scorer, BaselineEngineProperties properties,
			TripRepository tripRepository, ObjectMapper objectMapper) {
		this.pickRepository = pickRepository;
		this.pickPlaceRepository = pickPlaceRepository;
		this.placeRepository = placeRepository;
		this.placeFeatureRepository = placeFeatureRepository;
		this.codeMapRepository = codeMapRepository;
		this.scorer = scorer;
		this.properties = properties;
		this.tripRepository = tripRepository;
		this.objectMapper = objectMapper;
	}

	/**
	 * 지금 내보낼 수 있는 GLOBAL Pick 하나로 기준선을 만든다.
	 *
	 * <p>🔴 <b>GLOBAL 만 쓴다.</b> 이 통로를 타는 순간은 사용자에 대해 아는 것이 가장 적은
	 * 때다(신규 계정이거나 엔진이 죽었다). LOCAL Pick 은 지역이 맞는 사람에게만 의미가
	 * 있으므로, 고를 근거가 없는 상태에서 그것을 내보내면 추천이 아니라 아무거나가 된다.
	 *
	 * @param constraintSnapshotId 이 요청이 기록해 둔 제약 스냅샷. {@code null} 이면 제약 없음
	 * @param fallbackReason 왜 기준선으로 왔는가. 그대로 {@code recommendation_job.fallback_reason}
	 *     에 남는다 — 신규 계정이라 온 것과 엔진이 죽어서 온 것을 여기서 가른다
	 * @return 후보가 하나 이상인 기준선. 발행된 Pick 이 없거나 장소가 비어 있으면 빈 값
	 */
	public Optional<EditorialPickBaseline> loadGlobalBaseline(UUID constraintSnapshotId, String fallbackReason) {
		List<EditorialPick> published = this.pickRepository
				.findPublishedByScope(EditorialPickStatus.PUBLISHED, EditorialPickScope.GLOBAL);

		for (EditorialPick pick : published) {
			List<EditorialPickPlace> pickPlaces =
					this.pickPlaceRepository.findByIdPickIdOrderByPickRankAsc(pick.getPickId());
			if (pickPlaces.isEmpty()) {
				// 🔴 발행돼 있는데 장소가 없는 Pick 은 건너뛴다. 그것을 그대로 쓰면 후보 0건인
				//    기준선이 나가는데, 그건 기준선이 없는 것보다 나쁘다 — 성공으로 기록되면서
				//    화면은 여전히 비어 있고, 지표에는 "기준선이 동작했다" 로 남는다.
				continue;
			}
			EditorialPickBaseline baseline = build(pick, pickPlaces, constraintSnapshotId, fallbackReason);
			if (!baseline.batch().candidates().isEmpty()) {
				return Optional.of(baseline);
			}
		}
		return Optional.empty();
	}

	private EditorialPickBaseline build(EditorialPick pick, List<EditorialPickPlace> pickPlaces,
			UUID constraintSnapshotId, String fallbackReason) {

		List<UUID> placeIds = pickPlaces.stream().map(EditorialPickPlace::getPlaceId).toList();

		Map<UUID, Place> places = new LinkedHashMap<>();
		for (Place place : this.placeRepository.findByPlaceIdIn(placeIds)) {
			places.put(place.getPlaceId(), place);
		}
		Map<UUID, List<PlaceFeature>> featuresByPlace = new LinkedHashMap<>();
		for (PlaceFeature feature : this.placeFeatureRepository.findByPlaceIdIn(placeIds)) {
			featuresByPlace.computeIfAbsent(feature.getPlaceId(), (key) -> new ArrayList<>()).add(feature);
		}

		List<TripConstraint> allConstraints = (constraintSnapshotId == null) ? List.of()
				: this.tripRepository.findConstraintsBySnapshotId(constraintSnapshotId.toString());

		// 🔴 거리 의존 제약을 빼고 판정한다. 자세한 이유는 WARNING_WALKING_LIMIT_NOT_CHECKED.
		List<TripConstraint> evaluable = new ArrayList<>();
		boolean walkingLimitSkipped = false;
		for (TripConstraint constraint : allConstraints) {
			if (DISTANCE_DEPENDENT_CONSTRAINT_KEY.equals(constraint.constraintKey())
					&& constraint.answerStatus() == TripConstraint.AnswerStatus.SELECTED) {
				walkingLimitSkipped = true;
				continue;
			}
			evaluable.add(constraint);
		}
		List<String> sharedWarnings = new ArrayList<>();
		if (walkingLimitSkipped) {
			sharedWarnings.add(RecommendationCodes.WARNING_WALKING_LIMIT_NOT_CHECKED);
		}
		// 편집자가 발행 시점에 적어 둔 경고도 그대로 싣는다.
		sharedWarnings.addAll(pick.getWarningCodes());

		List<UserPlaceCodeMap> constraintCodeMap =
				this.codeMapRepository.findByIdUserInputKindOrderByIdUserInputCodeAsc(UserInputKind.CONSTRAINT);

		long startedNanos = System.nanoTime();
		List<EngineCandidate> candidates = new ArrayList<>(pickPlaces.size());
		for (EditorialPickPlace pickPlace : pickPlaces) {
			Place place = places.get(pickPlace.getPlaceId());
			if (place == null) {
				// 🔴 조용히 건너뛴다. 외래키가 있어서 정상적으로는 일어나지 않고, 일어났다면
				//    장소가 지워진 것이다 — 그 한 칸 때문에 기준선 전체를 버릴 이유는 없다.
				continue;
			}
			PlaceCandidateResponse.Candidate candidate = toCandidate(place,
					featuresByPlace.getOrDefault(place.getPlaceId(), List.of()));
			candidates.add(this.scorer.evaluateWithoutScoring(candidate, evaluable, constraintCodeMap,
					CANDIDATE_SOURCE, List.of(RecommendationCodes.REASON_EDITORIAL_PICK), sharedWarnings));
		}
		long candidateGenerationMs = (System.nanoTime() - startedNanos) / 1_000_000L;

		EngineVersions versions = new EngineVersions(this.properties.modelVersion(),
				this.properties.featureVersion(), this.properties.ontologyVersion(),
				this.properties.policyVersion(), datasetVersionOf(pick));
		// 🔴 rankingMs 는 null 이다 — 순위를 매기지 않았다. 0 을 넣으면 "0ms 에 매겼다" 가 되고
		//    랭킹 지연 분포에 0 이 섞인다.
		EngineLatencies latencies = new EngineLatencies(candidateGenerationMs, null, null, null, null);

		EngineCandidateBatch batch = new EngineCandidateBatch(candidates, versions, latencies,
				FallbackMode.EDITORIAL_PICK, fallbackReason);
		return new EditorialPickBaseline(pick.getPickId(), pick.getPickKey(), pick.getContentVersion(), batch);
	}

	/**
	 * 🔴 <b>Pick 에서는 데이터 판이 곧 Pick 의 판이다.</b>
	 *
	 * <p>개인화 추천의 {@code datasetVersion} 은 후보 장소들이 어느 수집분에서 왔는지를
	 * 말한다. Pick 은 다르다 — 무엇이 나왔는지를 정하는 것은 수집분이 아니라 <b>편집자가
	 * 발행한 판</b>이다. 같은 수집분에서도 판이 바뀌면 다른 목록이 나가고, 판이 같으면
	 * 수집분이 바뀌어도 같은 목록이 나간다.
	 *
	 * <p>그래서 장소들의 {@code dataset_version} 을 이어 붙이지 않고 판 자체를 적는다.
	 * {@code pickId} 를 쓰면 이름과 판이 함께 따라오고(그 둘의 조합이 곧 행이다) 길이가
	 * 늘 51자라 {@code VARCHAR(100)} 을 넘지 않는다 — 이름을 넣으면 100자 제한을 넘길 수
	 * 있고, 넘친 값을 자르면 서로 다른 판이 같아 보인다.
	 */
	private String datasetVersionOf(EditorialPick pick) {
		return ("editorial-pick:" + pick.getPickId()).toLowerCase(Locale.ROOT);
	}

	/**
	 * 🔴 {@code distanceM} 에 0 을 넣는다. 이 값은 <b>쓰이지 않는다</b> —
	 * {@code evaluateWithoutScoring} 은 거리 성분을 계산하지 않고, 거리를 보는 유일한 제약인
	 * {@code MAX_WALKING_METERS} 는 위에서 빼 뒀다. 기록에도 남지 않는다({@code featureValues}
	 * 가 비어 있다). {@code Candidate.distanceM} 이 원시형 {@code long} 이라 {@code null} 을
	 * 넣을 수 없어서 두는 값이고, 이 주석이 그 0 을 거리로 읽지 않게 하는 유일한 장치다.
	 */
	private PlaceCandidateResponse.Candidate toCandidate(Place place, List<PlaceFeature> features) {
		return new PlaceCandidateResponse.Candidate(place.getPlaceId(), place.getNameKo(), place.getCategory(),
				place.getLat(), place.getLng(), 0L, toViews(features));
	}

	/**
	 * 🔴 {@code PlaceCandidateQueryService} 에 같은 변환이 있지만 {@code private} 이라 쓸 수
	 * 없다. {@code place} 패키지는 다른 담당의 자리라 여기서 열지 않고 옮겨 왔다 —
	 * {@link PlaceFeatureView} 에 칸이 늘면 이쪽도 같이 고쳐야 한다.
	 *
	 * <p>🔴 <b>값 파싱이 실패한 행은 옮기지 않고 버린다 — 그쪽이 안전한 방향이다.</b>
	 * 원본({@code PlaceCandidateQueryService.readValue})은 파싱이 깨지면 {@code null} 을
	 * 값으로 넣는데, 그 동작을 그대로 가져오면 <b>안전 판정이 뒤집힌다.</b>
	 *
	 * <p>{@code ALLERGEN_TAG} 행이 {@code VERIFIED} 인데 값이 {@code null} 이면
	 * {@code BaselineCandidateScorer.bucketFor} 가 그것을 {@code ABSENT}("확인된 해당 없음")
	 * 로 읽는다. 즉 <b>JSON 이 깨졌다는 사실이 "땅콩 없음이 확인됐다" 로 바뀐다.</b>
	 * 행을 아예 빼면 같은 조회가 행을 못 찾아 {@code UNVERIFIED} 가 되고, 그러면 미확인
	 * 사실로 남아 REQUIRED 등급에서 후보가 빠진다 — 모르는 것을 모른다고 하는 쪽이다.
	 */
	private List<PlaceFeatureView> toViews(List<PlaceFeature> features) {
		List<PlaceFeatureView> views = new ArrayList<>(features.size());
		for (PlaceFeature feature : features) {
			String raw = feature.getValue();
			JsonNode value = readValue(raw);
			if (value == null && raw != null && !raw.isBlank()) {
				// 값이 있는데 읽지 못했다 — 이 행은 없는 것으로 다룬다 (위 설명).
				continue;
			}
			views.add(new PlaceFeatureView(feature.getFeatureType(), feature.getFeatureKey(),
					feature.getEvidenceStatus().name(), value, feature.getObservedAt(),
					feature.getSourceType()));
		}
		return views;
	}

	/** 못 읽으면 {@code null}. 부르는 쪽이 "값이 있었는가" 와 함께 보고 판단한다. */
	private JsonNode readValue(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return this.objectMapper.readTree(raw);
		}
		catch (JacksonException exception) {
			return null;
		}
	}
}
