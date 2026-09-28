package com.gabolle.backend.recommendation.adapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.gabolle.backend.recommendation.domain.ConstraintVerdict;
import com.gabolle.backend.trip.domain.TripSeedPlace;

/**
 * 씨앗 우대. {@code trip_seed_place} 에 담긴 장소를 후보 점수에서 앞세운다.
 *
 * 씨앗은 두 갈래로 들어온다.
 * <ul>
 * <li><b>사용자가 직접 적은 「꼭 가고 싶은 장소」</b> — {@code TripCreationService.saveMustVisitPlaces}
 *     가 출처 없이({@code sourceTripId}·{@code sourceShareLinkId} 둘 다 {@code null}) 넣는다</li>
 * <li><b>공유 일정 복제</b> — {@code ShareCloneService} 가 원본 여행을 {@code sourceTripId} 에 적어 넣는다</li>
 * </ul>
 * 둘을 이유 코드로 가른다. 한 코드로 뭉치면 공유 일정을 복제한 적도 없는 사람의 결과에
 * "공유 일정에서 왔다" 가 붙고, 나중에 그 기록을 읽는 사람이 없는 복제를 찾게 된다.
 *
 * 점수만 올리고 판정은 건드리지 않는다 — 씨앗이라도 제약에 걸리면 {@code constraintVerdict} 가 FAIL 이고
 * {@code CandidateAssembler} 가 뺀다. 🔴 예외 하나: 사용자가 직접 적은 곳이 <b>경사 하나 때문에만</b> 걸렸으면 빼지
 * 않고 경고로 돌린다({@link #keepMustVisitOverSlope}, S15P21E201-1625). {@code preRankScore} 가 {@code null} 인 후보도 그대로 둔다.
 * {@link #BOOST} 가 1.0 인 이유는 기본 점수가 가중치 합 1.0 인 0~1 스케일이라, 1.0 을 더하면 씨앗이 씨앗
 * 아닌 어떤 후보보다 앞서면서 씨앗들 사이의 순서는 원래 점수가 정하기 때문이다.
 *
 * <p>🔴 <b>후보 풀에 없는 씨앗을 여기서 끼워 넣지는 않는다.</b> 그것은 점수 문제가 아니라 조회 문제라
 * {@code BaselineRecommendationEngine.includeMissingSeeds} 가 먼저 한다 — 거기서 풀에 넣어 준 뒤라야
 * 이 클래스가 볼 수 있다.
 */
public final class SeedBoost {

	public static final double BOOST = 1.0;

	/** 공유 일정을 복제해서 따라온 장소. */
	public static final String REASON_CODE = "SEED_FROM_SHARED_ITINERARY";

	/** 사용자가 온보딩에서 직접 이름을 적어 넣은 장소. */
	public static final String REASON_CODE_MUST_VISIT = "MUST_VISIT_PLACE";

	private SeedBoost() {
	}

	public static List<EngineCandidate> apply(List<EngineCandidate> candidates, List<TripSeedPlace> seeds) {
		if (seeds == null || seeds.isEmpty()) {
			return candidates;
		}
		Map<UUID, TripSeedPlace> seedByPlace = seeds.stream()
				.collect(Collectors.toMap(s -> UUID.fromString(s.placeId()), Function.identity(), (a, b) -> a));

		List<EngineCandidate> boosted = new ArrayList<>(candidates.size());
		for (EngineCandidate candidate : candidates) {
			TripSeedPlace seed = seedByPlace.get(candidate.placeId());
			boosted.add(seed == null ? candidate : boost(candidate, seed));
		}
		return boosted;
	}

	/**
	 * 사용자가 직접 적어 넣은 곳이 경사 상한 하나 때문에만 탈락했으면 살리고 경고를 단다 — S15P21E201-1625.
	 *
	 * <p>🔴 왜. 경사는 둘레 길로 짐작한 추정값이다. 사용자가 이름을 적어 넣은 곳을 추정값으로 조용히 지우면 사용자는
	 * 적은 곳이 왜 없는지 모른다. 가고 싶다고 한 사람이 스스로 판단하게 경고로 남긴다. 다른 사유(알레르기·확인된
	 * 접근 불가 등)가 하나라도 섞여 있으면 그대로 둔다 — 그것들은 확인된 사실이다.
	 */
	static EngineCandidate keepMustVisitOverSlope(EngineCandidate c) {
		if (c.constraintVerdict() != ConstraintVerdict.FAIL || c.violations().isEmpty()
				|| !c.violations().stream().allMatch(v -> BaselineCandidateScorer.SLOPE_OVER_LIMIT.equals(v.get("code")))) {
			return c;
		}
		ConstraintVerdict verdict = c.unknownFacts().isEmpty() ? ConstraintVerdict.PASS : ConstraintVerdict.UNKNOWN;
		List<String> warnings = new ArrayList<>(c.warningCodes());
		if (!warnings.contains(BaselineCandidateScorer.SLOPE_OVER_LIMIT)) {
			warnings.add(BaselineCandidateScorer.SLOPE_OVER_LIMIT);
		}
		return new EngineCandidate(c.placeId(), c.candidateSource(), verdict, List.of(), c.unknownFacts(),
				c.constraintConfidence(), c.featureValues(), c.scoreComponents(), c.preRankScore(), c.reasonCodes(),
				warnings);
	}

	/**
	 * 사용자가 직접 적어 넣은 씨앗인가. 출처가 둘 다 비어 있으면 복제로 따라온 것이 아니다.
	 * 원본 여행이 지워지면 {@code sourceTripId} 가 {@code null} 이 되므로
	 * ({@code TripSeedPlace} 주석) 공유 주소 쪽도 함께 본다.
	 */
	static boolean isMustVisit(TripSeedPlace seed) {
		return seed.sourceTripId() == null && seed.sourceShareLinkId() == null;
	}

	private static EngineCandidate boost(EngineCandidate c, TripSeedPlace seed) {
		if (isMustVisit(seed)) {
			c = keepMustVisitOverSlope(c);
		}
		Double score = (c.preRankScore() == null) ? null : c.preRankScore() + BOOST;

		Map<String, Object> components = new LinkedHashMap<>(c.scoreComponents());
		components.put("seed", Map.of("boost", BOOST, "sourceSequence", seed.sequence()));

		List<String> reasons = new ArrayList<>(c.reasonCodes());
		reasons.add(isMustVisit(seed) ? REASON_CODE_MUST_VISIT : REASON_CODE);

		return new EngineCandidate(c.placeId(), c.candidateSource(), c.constraintVerdict(), c.violations(),
				c.unknownFacts(), c.constraintConfidence(), c.featureValues(), components, score, reasons,
				c.warningCodes());
	}
}
