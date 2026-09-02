package com.gabolle.backend.recommendation.application;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.gabolle.backend.common.json.JsonPayloads;
import com.gabolle.backend.common.privacy.SensitivePayloadGuard;
import com.gabolle.backend.recommendation.adapter.EngineCandidate;
import com.gabolle.backend.recommendation.adapter.EngineCandidateBatch;
import com.gabolle.backend.recommendation.domain.CandidateStage;
import com.gabolle.backend.recommendation.domain.ConstraintSeverity;
import com.gabolle.backend.recommendation.domain.ConstraintVerdict;
import com.gabolle.backend.recommendation.domain.RecommendationCandidate;
import com.gabolle.backend.recommendation.domain.UnknownExclusionThreshold;

/**
 * 엔진이 돌려준 후보를 <b>전부</b> 저장 가능한 행으로 바꾸고, 그 중 반환할 것을 고른다.
 *
 * <p>여기서 지키는 불변식은 다섯이다.
 * <ol>
 * <li>FAIL 후보는 랭킹 대상도 아니고 반환되지도 않는다</li>
 * <li>UNKNOWN 은 PASS 로 바뀌지 않는다 — 판정은 UNKNOWN 그대로 저장된다</li>
 * <li>🔴 <b>안전 제약(hard)이 미확인인 후보는 반환하지 않는다.</b> 소프트 조건의 미확인만
 *     경고를 달고 순위에 태운다</li>
 * <li>점수가 없는 후보에는 순위를 붙이지 않는다 — 결측을 순위로 바꾸지 않는다</li>
 * <li>Top-K 에 못 든 후보도, 탈락한 후보도 행으로는 남는다</li>
 * </ol>
 */
@Component
public class CandidateAssembler {

	/**
	 * 미확인 사실 하나에 붙는 제약 등급의 키 이름. 온톨로지 명세서가 제약 속성으로 쓰는
	 * {@code severity} 와 같은 이름이다.
	 *
	 * <p>🔴 등급 <b>이름</b>은 REC/AI 소유다({@link ConstraintSeverity} 참고). 여기서는
	 * 그 값을 읽기만 한다.
	 */
	static final String SEVERITY_FACT_KEY = "severity";

	private final JsonPayloads jsonPayloads;

	private final SensitivePayloadGuard sensitivePayloadGuard;

	public CandidateAssembler(JsonPayloads jsonPayloads, SensitivePayloadGuard sensitivePayloadGuard) {
		this.jsonPayloads = jsonPayloads;
		this.sensitivePayloadGuard = sensitivePayloadGuard;
	}

	/**
	 * @param threshold 미확인 제약이 어느 등급 이상일 때 결과에서 뺄 것인가
	 * @param unspecifiedSeverity 온톨로지가 등급을 안 보냈을 때 무엇으로 볼 것인가
	 */
	public CandidateAssembly assemble(UUID requestId, EngineCandidateBatch batch, int topK,
			UnknownExclusionThreshold threshold, ConstraintSeverity unspecifiedSeverity,
			OffsetDateTime createdAt) {

		List<EngineCandidate> generated = batch.candidates();
		rejectDuplicatePlaces(generated);

		// 순위를 매길 수 있는 것과 그렇지 않은 것을 가른다. 판정 자체는 어느 쪽도 바꾸지 않는다.
		List<EngineCandidate> rankable = new ArrayList<>();
		List<Filtered> filtered = new ArrayList<>();

		for (EngineCandidate candidate : generated) {
			List<String> warnings = new ArrayList<>(candidate.warningCodes());
			Filtered exclusion = appraise(candidate, threshold, unspecifiedSeverity, warnings);
			if (exclusion != null) {
				filtered.add(exclusion);
			}
			else if (candidate.preRankScore() == null) {
				// 🔴 점수가 없으면 애초에 "랭킹 가능" 이 아니었다. 순위를 붙여 두면 나중에
				//    "몇 위라서 못 들어갔다" 로 읽히는데, 실제로는 점수 자체가 없었던 것이다.
				warnings.add(RecommendationCodes.WARNING_SCORE_MISSING);
				filtered.add(new Filtered(candidate, CandidateStage.QUALITY_FILTERED, warnings));
			}
			else {
				rankable.add(candidate);
			}
		}

		// 점수 높은 순. 동점은 place_id 로 갈라 결과를 재현 가능하게 만든다 — 순서가 실행마다
		// 달라지면 나중에 비교가 불가능해진다.
		rankable.sort(Comparator.comparingDouble((EngineCandidate candidate) -> candidate.preRankScore())
				.reversed()
				.thenComparing(EngineCandidate::placeId));

		List<RecommendationCandidate> rows = new ArrayList<>(generated.size());
		List<RecommendedPlace> returnedItems = new ArrayList<>();
		int returnedCount = 0;

		for (int index = 0; index < rankable.size(); index++) {
			EngineCandidate candidate = rankable.get(index);
			int rank = index + 1;
			// 재정렬기가 아직 없다. 있게 되면 final_score · final_rank 만 이 자리에서 갈라진다.
			Double finalScore = candidate.preRankScore();
			boolean returned = rank <= topK;

			List<String> warnings = new ArrayList<>(candidate.warningCodes());
			if (candidate.constraintVerdict() == ConstraintVerdict.UNKNOWN) {
				warnings.add(RecommendationCodes.WARNING_CONSTRAINT_UNKNOWN);
			}

			CandidateStage stage = returned ? CandidateStage.RETURNED : CandidateStage.RANKED;
			rows.add(toRow(requestId, candidate, stage, true, rank, finalScore, rank, returned, warnings,
					batch, createdAt));

			if (returned) {
				returnedCount++;
				returnedItems.add(new RecommendedPlace(candidate.placeId(), rank, finalScore,
						candidate.reasonCodes(), List.copyOf(warnings)));
			}
		}

		for (Filtered exclusion : filtered) {
			rows.add(toRow(requestId, exclusion.candidate(), exclusion.stage(), false, null, null, null, false,
					exclusion.warnings(), batch, createdAt));
		}

		return new CandidateAssembly(List.copyOf(rows), List.copyOf(returnedItems),
				generated.size(), rankable.size(), returnedCount);
	}

	/**
	 * 이 후보를 랭킹에서 빼야 하는가. 빼야 하면 어떤 단계로 빼는지와 경고까지 함께 정한다.
	 *
	 * @return 빼야 하면 그 사유, 순위를 매겨도 되면 {@code null}
	 */
	private Filtered appraise(EngineCandidate candidate, UnknownExclusionThreshold threshold,
			ConstraintSeverity unspecifiedSeverity, List<String> warnings) {

		if (candidate.constraintVerdict() == ConstraintVerdict.PASS) {
			return null;
		}
		if (candidate.constraintVerdict() == ConstraintVerdict.FAIL) {
			// 제약 위반이 확인됐다. 점수로 되살리지 않는다 (FR-REC-02).
			return new Filtered(candidate, CandidateStage.HARD_FILTERED, warnings);
		}

		// 여기부터 UNKNOWN. 🔴 판정을 PASS 로 바꾸지 않는다 — 정하는 것은 결과에 담을지뿐이다.
		warnings.add(RecommendationCodes.WARNING_CONSTRAINT_UNKNOWN);

		ConstraintSeverity worst = worstUnknownSeverity(candidate, unspecifiedSeverity);
		if (threshold.excludes(worst)) {
			// 🔴 이 등급의 제약을 확인하지 못했다. 경고로 내보내지 않는다 — "확인 필요" 는
			//    사용자가 대신 확인할 수 있을 때만 정보이고, 그렇지 않으면 책임 전가다.
			warnings.add(RecommendationCodes.WARNING_UNKNOWN_CONSTRAINT_EXCLUDED);
			warnings.add(RecommendationCodes.warningForSeverity(worst));
			return new Filtered(candidate, CandidateStage.QUALITY_FILTERED, warnings);
		}
		return null;
	}

	/**
	 * 확인하지 못한 제약 중 가장 위험한 등급.
	 *
	 * <p>🔴 등급이 안 왔거나 모르는 이름이면 {@code unspecifiedSeverity} 가 정한다 —
	 * 없는 값을 낙관적으로 채우지 않는다. 미확인 사실이 하나도 없는데 판정만 UNKNOWN 인
	 * 경우도 같다. 왜 모르는지조차 모르는 상태이기 때문이다.
	 */
	private ConstraintSeverity worstUnknownSeverity(EngineCandidate candidate,
			ConstraintSeverity unspecifiedSeverity) {

		if (candidate.unknownFacts().isEmpty()) {
			return unspecifiedSeverity;
		}
		ConstraintSeverity worst = null;
		for (Map<String, Object> fact : candidate.unknownFacts()) {
			Object raw = (fact == null) ? null : fact.get(SEVERITY_FACT_KEY);
			ConstraintSeverity severity = ConstraintSeverity.parseOrNull(raw);
			if (severity == null) {
				severity = unspecifiedSeverity;
			}
			worst = ConstraintSeverity.moreSevere(worst, severity);
		}
		return worst;
	}

	private RecommendationCandidate toRow(UUID requestId, EngineCandidate candidate, CandidateStage stage,
			boolean eligible, Integer originalRank, Double finalScore, Integer finalRank, boolean returned,
			List<String> warnings, EngineCandidateBatch batch, OffsetDateTime createdAt) {

		verifyNoSensitiveData(candidate);

		return RecommendationCandidate.builder()
				.candidateId(UUID.randomUUID())
				.requestId(requestId)
				.placeId(candidate.placeId())
				.candidateSource(candidate.candidateSource())
				.candidateStage(stage)
				.eligible(eligible)
				.constraintVerdict(candidate.constraintVerdict())
				.violations(this.jsonPayloads.writeArray(candidate.violations()))
				.unknownFacts(this.jsonPayloads.writeArray(candidate.unknownFacts()))
				.constraintConfidence(candidate.constraintConfidence())
				.featureValues(this.jsonPayloads.writeObject(candidate.featureValues()))
				.scoreComponents(this.jsonPayloads.writeObject(candidate.scoreComponents()))
				.preRankScore(candidate.preRankScore())
				.finalScore(finalScore)
				.originalRank(originalRank)
				.finalRank(finalRank)
				.returned(returned)
				.reasonCodes(candidate.reasonCodes().toArray(String[]::new))
				.warningCodes(warnings.toArray(String[]::new))
				.fallbackMode(batch.fallbackMode())
				.createdAt(createdAt)
				.build();
	}

	/** 🔴 JSONB 로 들어가는 것 넷을 저장 <b>전에</b> 본다. 들어간 뒤에는 지우는 비용이 다르다. */
	private void verifyNoSensitiveData(EngineCandidate candidate) {
		String prefix = "candidate[" + candidate.placeId() + "]";
		this.sensitivePayloadGuard.verify(candidate.featureValues(), prefix + ".feature_values");
		this.sensitivePayloadGuard.verify(candidate.scoreComponents(), prefix + ".score_components");
		this.sensitivePayloadGuard.verify(candidate.violations(), prefix + ".violations");
		this.sensitivePayloadGuard.verify(candidate.unknownFacts(), prefix + ".unknown_facts");
	}

	/**
	 * 같은 요청에 같은 장소가 두 번 오면 여기서 멈춘다. DB 의 UNIQUE 도 같은 것을 막지만,
	 * 거기까지 가면 예외가 JDBC 안쪽에서 나서 <b>어느 후보가 겹쳤는지</b>를 알려주지 못한다.
	 */
	private void rejectDuplicatePlaces(List<EngineCandidate> candidates) {
		Set<UUID> seen = new HashSet<>();
		Set<UUID> duplicates = new LinkedHashSet<>();
		for (EngineCandidate candidate : candidates) {
			if (!seen.add(candidate.placeId())) {
				duplicates.add(candidate.placeId());
			}
		}
		if (!duplicates.isEmpty()) {
			throw new IllegalArgumentException(
					"추천 엔진이 한 요청에 같은 place_id 를 두 번 이상 돌려줬다: " + duplicates);
		}
	}

	/** 랭킹에서 빠진 후보 하나와 그 사유. 행은 그대로 저장된다. */
	private record Filtered(EngineCandidate candidate, CandidateStage stage, List<String> warnings) {
	}
}
