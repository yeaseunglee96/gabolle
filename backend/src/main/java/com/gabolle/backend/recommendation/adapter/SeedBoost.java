package com.gabolle.backend.recommendation.adapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.gabolle.backend.trip.domain.TripSeedPlace;

/**
 * 복제 씨앗 우대 — S15P21E201-338 (F-COL-04).
 *
 * <p>공유 일정을 복제한 여행에는 원본의 장소 구성이 {@code trip_seed_place} 에 남아 있다. 추천이 후보를
 * 매길 때 그 장소들을 앞세운다 — "장소 구성만 가져오고 시간표는 내 조건으로 새로 계산한다" 의 앞부분이
 * 여기다.
 *
 * <p>🔴 <b>점수만 올리고 판정은 건드리지 않는다.</b> 씨앗이라도 내 제약(알레르기·이동 제약)에 걸리면
 * {@code constraintVerdict} 가 FAIL 이고, 그 후보는 {@code CandidateAssembler} 가 여전히 뺀다. "맞지 않는
 * 일정은 안 만드는 것이 이 서비스의 원칙" 이라 씨앗도 예외가 아니다. {@code preRankScore} 가 {@code null}
 * 인 후보(순위를 매길 수 없는 것)도 그대로 둔다.
 *
 * <p>{@link #BOOST} 가 1.0 인 이유 — 기본 점수는 가중치 합이 1.0 인 0~1 스케일이라, 1.0 을 더하면 씨앗이
 * 씨앗 아닌 어떤 후보보다 앞서면서 씨앗들 사이의 순서는 원래 점수가 정한다.
 *
 * <p>후보 풀에 없는 씨앗(출발지 반경 밖·취향 카테고리 밖)은 여기서 끼워 넣지 않는다. 풀은 장소 조회
 * 서비스가 만들고 이 클래스는 그 결과에 손만 댄다 — 알려진 한계로 적어 둔다.
 */
public final class SeedBoost {

	public static final double BOOST = 1.0;

	public static final String REASON_CODE = "SEED_FROM_SHARED_ITINERARY";

	private SeedBoost() {
	}

	public static List<EngineCandidate> apply(List<EngineCandidate> candidates, List<TripSeedPlace> seeds) {
		if (seeds == null || seeds.isEmpty()) {
			return candidates;
		}
		Map<UUID, Integer> sequenceByPlace = seeds.stream()
				.collect(Collectors.toMap(s -> UUID.fromString(s.placeId()), TripSeedPlace::sequence, (a, b) -> a));

		List<EngineCandidate> boosted = new ArrayList<>(candidates.size());
		for (EngineCandidate candidate : candidates) {
			Integer sequence = sequenceByPlace.get(candidate.placeId());
			boosted.add(sequence == null ? candidate : boost(candidate, sequence));
		}
		return boosted;
	}

	private static EngineCandidate boost(EngineCandidate c, int sequence) {
		Double score = (c.preRankScore() == null) ? null : c.preRankScore() + BOOST;

		Map<String, Object> components = new LinkedHashMap<>(c.scoreComponents());
		components.put("seed", Map.of("boost", BOOST, "sourceSequence", sequence));

		List<String> reasons = new ArrayList<>(c.reasonCodes());
		reasons.add(REASON_CODE);

		return new EngineCandidate(c.placeId(), c.candidateSource(), c.constraintVerdict(), c.violations(),
				c.unknownFacts(), c.constraintConfidence(), c.featureValues(), components, score, reasons,
				c.warningCodes());
	}
}
