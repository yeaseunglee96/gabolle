package com.gabolle.backend.recommendation.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.recommendation.domain.ConstraintVerdict;
import com.gabolle.backend.trip.domain.TripSeedPlace;

/** 씨앗 우대는 점수만 올리고 판정은 건드리지 않는다. */
class SeedBoostTest {

	private static final UUID SEED_A = UUID.randomUUID();
	private static final UUID SEED_B = UUID.randomUUID();
	private static final UUID OTHER = UUID.randomUUID();

	@Test
	@DisplayName("🔴 씨앗 장소는 점수가 1.0 오르고 이유 코드가 붙는다 — 씨앗 아닌 후보는 그대로다")
	void seedsAreBoostedOthersUntouched() {
		List<EngineCandidate> in = List.of(candidate(OTHER, 0.9, ConstraintVerdict.PASS),
				candidate(SEED_A, 0.2, ConstraintVerdict.PASS));

		List<EngineCandidate> out = SeedBoost.apply(in, List.of(seed(SEED_A, 1)));

		EngineCandidate other = out.get(0);
		EngineCandidate seed = out.get(1);
		assertThat(other).isSameAs(in.get(0));
		assertThat(seed.preRankScore()).isEqualTo(1.2);
		assertThat(seed.reasonCodes()).contains(SeedBoost.REASON_CODE);
		assertThat(seed.scoreComponents()).containsKey("seed");
		// 원래 점수 0.9 짜리보다 씨앗(0.2+1.0)이 앞선다 — 이것이 "장소 구성을 가져온다" 의 뜻이다.
		assertThat(seed.preRankScore()).isGreaterThan(other.preRankScore());
	}

	@Test
	@DisplayName("🔴 제약 판정은 그대로다 — FAIL 인 씨앗은 FAIL 로 남고 점수 없는 후보는 점수 없이 남는다")
	void verdictAndNullScoreArePreserved() {
		List<EngineCandidate> out = SeedBoost.apply(
				List.of(candidate(SEED_A, 0.5, ConstraintVerdict.FAIL), candidate(SEED_B, null, ConstraintVerdict.UNKNOWN)),
				List.of(seed(SEED_A, 1), seed(SEED_B, 2)));

		assertThat(out.get(0).constraintVerdict()).isEqualTo(ConstraintVerdict.FAIL);
		assertThat(out.get(0).preRankScore()).isEqualTo(1.5);
		assertThat(out.get(1).preRankScore()).isNull();
		assertThat(out.get(1).reasonCodes()).contains(SeedBoost.REASON_CODE);
	}

	@Test
	@DisplayName("씨앗이 없으면 입력을 그대로 돌려준다")
	void noSeedsNoChange() {
		List<EngineCandidate> in = List.of(candidate(OTHER, 0.3, ConstraintVerdict.PASS));
		assertThat(SeedBoost.apply(in, List.of())).isSameAs(in);
		assertThat(SeedBoost.apply(in, null)).isSameAs(in);
	}

	private static EngineCandidate candidate(UUID placeId, Double score, ConstraintVerdict verdict) {
		return new EngineCandidate(placeId, "BASELINE", verdict, List.of(), List.of(), null, Map.of(),
				Map.of("distance", 0.1), score, List.of("NEAR_ORIGIN"), List.of());
	}

	private static TripSeedPlace seed(UUID placeId, int sequence) {
		return new TripSeedPlace(UUID.randomUUID().toString(), placeId.toString(), sequence, null, null, Instant.now());
	}
}
