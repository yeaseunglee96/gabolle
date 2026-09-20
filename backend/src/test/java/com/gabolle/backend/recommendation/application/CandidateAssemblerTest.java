package com.gabolle.backend.recommendation.application;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.common.json.JsonPayloads;
import com.gabolle.backend.common.privacy.SensitivePayloadGuard;
import com.gabolle.backend.recommendation.adapter.EngineCandidate;
import com.gabolle.backend.recommendation.config.DiversityProperties;
import com.gabolle.backend.recommendation.domain.CandidateStage;
import com.gabolle.backend.recommendation.domain.ConstraintSeverity;
import com.gabolle.backend.recommendation.domain.ConstraintVerdict;
import com.gabolle.backend.recommendation.domain.RecommendationCandidate;
import com.gabolle.backend.recommendation.domain.UnknownExclusionThreshold;
import com.gabolle.backend.recommendation.support.FakeRecommendationEngine;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 후보를 저장 가능한 행으로 바꾸는 단계의 불변식. 도커 없이 도는 단위 테스트다.
 *
 * <p>같은 불변식을 통합 테스트와 DB 제약에서도 다시 본다. 세 층이 같은 것을 보는 이유는,
 * 어느 한 층을 나중에 누가 걷어내도 나머지가 남기 때문이다.
 */
class CandidateAssemblerTest {

	/**
	 * 재정렬기가 붙어 있어도 순서가 점수 순 그대로인 것은 {@code FakeRecommendationEngine} 의
	 * 후보에 다양성 키({@code category}·{@code localityBucket})가 없어서다 — 키가 없는 후보는
	 * 서로 겹치지 않는 것으로 보므로 벌점이 0 이다. 재정렬 자체는 {@code DiversityRerankTest}
	 * 가 본다.
	 */
	private final CandidateAssembler assembler = new CandidateAssembler(
			new JsonPayloads(JsonMapper.builder().build()), new SensitivePayloadGuard(),
			new DiversityReranker(new DiversityProperties(null, null, null)));

	private final OffsetDateTime now = OffsetDateTime.now();

	@Test
	@DisplayName("전체 후보 10개에 Top-K 5개면 행은 10개, 반환은 5개다")
	void keepsEveryCandidateAndReturnsOnlyTopK() {
		List<EngineCandidate> candidates = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			candidates.add(FakeRecommendationEngine.passing(UUID.randomUUID(), 1.0 - (i * 0.05)));
		}

		CandidateAssembly assembly = assemble(candidates, 5);

		assertThat(assembly.candidates()).hasSize(10);
		assertThat(assembly.generatedCount()).isEqualTo(10);
		assertThat(assembly.eligibleCount()).isEqualTo(10);
		assertThat(assembly.returnedCount()).isEqualTo(5);
		assertThat(assembly.returnedItems()).hasSize(5);
		assertThat(assembly.candidates()).filteredOn(RecommendationCandidate::isReturned).hasSize(5);
	}

	@Test
	@DisplayName("순위는 1 부터 빠짐없이 이어지고 점수 높은 순이다")
	void ranksAreDenseAndOrderedByScore() {
		UUID best = UUID.randomUUID();
		UUID worst = UUID.randomUUID();

		CandidateAssembly assembly = assemble(List.of(
				FakeRecommendationEngine.passing(worst, 0.1),
				FakeRecommendationEngine.passing(best, 0.9)), 5);

		assertThat(assembly.returnedItems()).extracting(RecommendedPlace::placeId).containsExactly(best, worst);
		assertThat(assembly.returnedItems()).extracting(RecommendedPlace::finalRank).containsExactly(1, 2);
	}

	@Test
	@DisplayName("🔴 FAIL 후보는 랭킹 대상도 아니고 반환되지도 않지만 행으로는 남는다")
	void failingCandidatesAreKeptButNeverRanked() {
		UUID banned = UUID.randomUUID();

		CandidateAssembly assembly = assemble(
				List.of(FakeRecommendationEngine.failing(banned, "ALLERGY_PEANUT")), 5);

		assertThat(assembly.returnedItems()).isEmpty();
		assertThat(assembly.eligibleCount()).isZero();

		RecommendationCandidate row = assembly.candidates().get(0);
		assertThat(row.getPlaceId()).isEqualTo(banned);
		assertThat(row.getConstraintVerdict()).isEqualTo(ConstraintVerdict.FAIL);
		assertThat(row.getCandidateStage()).isEqualTo(CandidateStage.HARD_FILTERED);
		assertThat(row.getViolations()).contains("ALLERGY_PEANUT");
	}

	@Test
	@DisplayName("🔴 기본 기준선에서 REQUIRED 미확인은 빠지고 PREFERRED 미확인은 경고를 달고 나간다")
	void thresholdSeparatesSafetyFromPreference() {
		UUID risky = UUID.randomUUID();
		UUID shady = UUID.randomUUID();

		CandidateAssembly assembly = assemble(List.of(
				FakeRecommendationEngine.unknownWith(risky, "ALLERGY_PEANUT", ConstraintSeverity.REQUIRED),
				FakeRecommendationEngine.unknownWith(shady, "SHADE_RATIO", ConstraintSeverity.PREFERRED)), 5);

		RecommendationCandidate excluded = row(assembly, risky);
		assertThat(excluded.isReturned()).isFalse();
		assertThat(excluded.isEligible()).isFalse();
		assertThat(excluded.getCandidateStage()).isEqualTo(CandidateStage.QUALITY_FILTERED);
		assertThat(excluded.getWarningCodes())
				.contains(RecommendationCodes.WARNING_UNKNOWN_CONSTRAINT_EXCLUDED,
						RecommendationCodes.warningForSeverity(ConstraintSeverity.REQUIRED));

		RecommendationCandidate warned = row(assembly, shady);
		assertThat(warned.isReturned()).isTrue();
		assertThat(warned.getWarningCodes()).contains(RecommendationCodes.WARNING_CONSTRAINT_UNKNOWN);
		assertThat(warned.getWarningCodes())
				.doesNotContain(RecommendationCodes.WARNING_UNKNOWN_CONSTRAINT_EXCLUDED);

		// 어느 쪽도 판정이 바뀌지 않았다.
		assertThat(excluded.getConstraintVerdict()).isEqualTo(ConstraintVerdict.UNKNOWN);
		assertThat(warned.getConstraintVerdict()).isEqualTo(ConstraintVerdict.UNKNOWN);
	}

	@Test
	@DisplayName("기준선을 내리면 같은 후보가 결과에 들어온다 — 코드를 안 고치고 정책만 바꾼다")
	void loweringTheThresholdLetsThemThrough() {
		UUID risky = UUID.randomUUID();
		List<EngineCandidate> candidates = List.of(
				FakeRecommendationEngine.unknownWith(risky, "ALLERGY_PEANUT", ConstraintSeverity.REQUIRED));

		CandidateAssembly strict = this.assembler.assemble(UUID.randomUUID(),
				FakeRecommendationEngine.batchOf(candidates), 5, UnknownExclusionThreshold.REQUIRED,
				ConstraintSeverity.REQUIRED, this.now);
		CandidateAssembly loose = this.assembler.assemble(UUID.randomUUID(),
				FakeRecommendationEngine.batchOf(candidates), 5, UnknownExclusionThreshold.NONE,
				ConstraintSeverity.REQUIRED, this.now);

		assertThat(strict.returnedCount()).isZero();
		assertThat(loose.returnedCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("기준선을 PREFERRED 로 올리면 선호 조건의 미확인까지 빠진다")
	void raisingTheThresholdExcludesPreferences() {
		UUID shady = UUID.randomUUID();

		CandidateAssembly assembly = this.assembler.assemble(UUID.randomUUID(),
				FakeRecommendationEngine.batchOf(List.of(
						FakeRecommendationEngine.unknownWith(shady, "SHADE_RATIO",
								ConstraintSeverity.PREFERRED))),
				5, UnknownExclusionThreshold.PREFERRED, ConstraintSeverity.REQUIRED, this.now);

		assertThat(assembly.returnedCount()).isZero();
		assertThat(row(assembly, shady).getWarningCodes())
				.contains(RecommendationCodes.warningForSeverity(ConstraintSeverity.PREFERRED));
	}

	@Test
	@DisplayName("🔴 미확인 제약이 여럿이면 가장 위험한 등급으로 판단한다")
	void theWorstSeverityWins() {
		UUID mixed = UUID.randomUUID();
		EngineCandidate candidate = new EngineCandidate(mixed, "ONTOLOGY_SEED", ConstraintVerdict.UNKNOWN,
				List.of(), List.of(
						java.util.Map.of("fact", "SHADE_RATIO", "severity", "PREFERRED"),
						java.util.Map.of("fact", "ALLERGY_PEANUT", "severity", "REQUIRED")),
				0.3, java.util.Map.of(), java.util.Map.of(), 0.9, List.of(), List.of());

		CandidateAssembly assembly = assemble(List.of(candidate), 5);

		assertThat(row(assembly, mixed).isReturned()).isFalse();
		assertThat(row(assembly, mixed).getWarningCodes())
				.contains(RecommendationCodes.warningForSeverity(ConstraintSeverity.REQUIRED));
	}

	@Test
	@DisplayName("🔴 등급이 안 온 미확인은 가장 위험한 것으로 본다 — 없는 값을 낙관적으로 채우지 않는다")
	void unratedUnknownsFallBackToTheConfiguredSeverity() {
		UUID unrated = UUID.randomUUID();

		CandidateAssembly strict = assemble(
				List.of(FakeRecommendationEngine.unknownUnrated(unrated, "WHEELCHAIR_ACCESS")), 5);
		assertThat(strict.returnedCount()).isZero();

		// 등급 없는 것을 OPTIONAL 로 보기로 하면 기본 기준선(REQUIRED)에 안 걸린다.
		CandidateAssembly lenient = this.assembler.assemble(UUID.randomUUID(),
				FakeRecommendationEngine.batchOf(List.of(
						FakeRecommendationEngine.unknownUnrated(unrated, "WHEELCHAIR_ACCESS"))),
				5, UnknownExclusionThreshold.REQUIRED, ConstraintSeverity.OPTIONAL, this.now);
		assertThat(lenient.returnedCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("모르는 등급 이름이 오면 등급이 안 온 것과 같이 다룬다")
	void unrecognisedSeverityNamesAreTreatedAsUnrated() {
		UUID odd = UUID.randomUUID();
		EngineCandidate candidate = new EngineCandidate(odd, "ONTOLOGY_SEED", ConstraintVerdict.UNKNOWN,
				List.of(), List.of(java.util.Map.of("fact", "X", "severity", "SOMETHING_NEW")),
				0.3, java.util.Map.of(), java.util.Map.of(), 0.9, List.of(), List.of());

		CandidateAssembly assembly = assemble(List.of(candidate), 5);

		assertThat(assembly.returnedCount()).isZero();
	}

	@Test
	@DisplayName("🔴 점수가 없는 후보에는 순위를 붙이지 않고 랭킹 수에도 넣지 않는다")
	void scorelessCandidatesAreNotRanked() {
		UUID scoreless = UUID.randomUUID();

		CandidateAssembly assembly = assemble(List.of(
				FakeRecommendationEngine.passing(UUID.randomUUID(), 0.9),
				FakeRecommendationEngine.scoreless(scoreless)), 5);

		assertThat(assembly.generatedCount()).isEqualTo(2);
		assertThat(assembly.eligibleCount()).isEqualTo(1);
		assertThat(assembly.returnedCount()).isEqualTo(1);

		RecommendationCandidate row = row(assembly, scoreless);
		assertThat(row.isEligible()).isFalse();
		assertThat(row.isReturned()).isFalse();
		assertThat(row.getFinalRank()).isNull();
		assertThat(row.getOriginalRank()).isNull();
		assertThat(row.getCandidateStage()).isEqualTo(CandidateStage.QUALITY_FILTERED);
		assertThat(row.getWarningCodes()).contains(RecommendationCodes.WARNING_SCORE_MISSING);
	}

	@Test
	@DisplayName("같은 요청에 같은 place_id 가 두 번 오면 어느 장소인지 말하면서 거부한다")
	void duplicatePlaceIdsAreRejectedWithTheOffendingId() {
		UUID duplicated = UUID.randomUUID();

		assertThatThrownBy(() -> assemble(List.of(
				FakeRecommendationEngine.passing(duplicated, 0.9),
				FakeRecommendationEngine.passing(duplicated, 0.8)), 5))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining(duplicated.toString());
	}

	/** 운영 기본값 — 안전 제약 미확인만 제외, 등급 없는 것은 가장 위험한 것으로. */
	private CandidateAssembly assemble(List<EngineCandidate> candidates, int topK) {
		return this.assembler.assemble(UUID.randomUUID(), FakeRecommendationEngine.batchOf(candidates), topK,
				UnknownExclusionThreshold.REQUIRED, ConstraintSeverity.REQUIRED, this.now);
	}

	private RecommendationCandidate row(CandidateAssembly assembly, UUID placeId) {
		return assembly.candidates().stream()
				.filter((candidate) -> candidate.getPlaceId().equals(placeId))
				.findFirst()
				.orElseThrow(() -> new AssertionError("후보가 없다: " + placeId));
	}
}
