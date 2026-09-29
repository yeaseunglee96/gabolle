package com.gabolle.backend.recommendation.application;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import com.gabolle.backend.recommendation.domain.SourceMode;
import com.gabolle.backend.recommendation.domain.UnknownExclusionThreshold;

/**
 * 엔진이 돌려준 후보를 전부 저장 가능한 행으로 바꾸고, 그 중 반환할 것을 고른다.
 *
 * <p>여기서 지키는 불변식은 다섯이다.
 * <ol>
 * <li>FAIL 후보는 랭킹 대상도 아니고 반환되지도 않는다</li>
 * <li>UNKNOWN 은 PASS 로 바뀌지 않는다 — 판정은 UNKNOWN 그대로 저장된다</li>
 * <li>안전 제약(hard)이 미확인인 후보는 반환하지 않는다. 소프트 조건의 미확인만 경고를 달고
 *     순위에 태운다</li>
 * <li>점수가 없는 후보에는 순위를 붙이지 않는다 — 결측을 순위로 바꾸지 않는다</li>
 * <li>Top-K 에 못 든 후보도, 탈락한 후보도 행으로는 남는다</li>
 * </ol>
 */
@Component
public class CandidateAssembler {

	/**
	 * 미확인 사실 하나에 붙는 제약 등급의 키 이름. 등급 이름 자체는 {@link ConstraintSeverity}
	 * 가 정하고 여기서는 값을 읽기만 한다.
	 */
	static final String SEVERITY_FACT_KEY = "severity";

	private final JsonPayloads jsonPayloads;

	private final SensitivePayloadGuard sensitivePayloadGuard;

	/** 다양성 재정렬. 순서만 바꾸고 무엇을 저장하는지는 바꾸지 않는다. */
	private final DiversityReranker reranker;

	public CandidateAssembler(JsonPayloads jsonPayloads, SensitivePayloadGuard sensitivePayloadGuard,
			DiversityReranker reranker) {
		this.jsonPayloads = jsonPayloads;
		this.sensitivePayloadGuard = sensitivePayloadGuard;
		this.reranker = reranker;
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
				// 점수가 없으면 애초에 랭킹 가능이 아니었다. 순위를 붙여 두면 "몇 위라서 못
				// 들어갔다" 로 읽히는데 실제로는 점수 자체가 없었던 것이다.
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

		// 여기가 original_rank 와 final_rank 가 갈라지는 자리다. 점수는 건드리지 않는다 —
		// final_score 는 여전히 preRankScore 그대로이고 바뀐 것은 순서뿐이다. 벌점을 점수에
		// 반영하면 "취향에 얼마나 맞는가" 와 "고르게 만들려고 깎았는가" 가 한 숫자에 섞인다.
		DiversityReranker.Reranked reranked = this.reranker.rerank(rankable);
		List<EngineCandidate> ordered = reranked.ordered();

		// 반환될 것을 먼저 정해야 대조 기여도의 기준선을 잴 수 있다 — 그 기준선이 이 결과
		// 안에서의 평균이라, 반환되지 않는 후보까지 넣으면 보지도 않은 장소가 평균을 끈다.
		List<EngineCandidate> returnedCandidates = DiversityMetrics.topOf(ordered, topK);
		Map<String, Double> cohortMeans = ReasonRanking.cohortMeans(returnedCandidates);

		List<RecommendationCandidate> rows = new ArrayList<>(generated.size());
		List<RecommendedPlace> returnedItems = new ArrayList<>();
		int returnedCount = 0;

		for (int index = 0; index < ordered.size(); index++) {
			EngineCandidate candidate = ordered.get(index);
			int finalRank = index + 1;
			int originalRank = reranked.scoreRankByPlace().getOrDefault(candidate.placeId(), finalRank);
			Double finalScore = candidate.preRankScore();
			boolean returned = finalRank <= topK;

			List<String> warnings = new ArrayList<>(candidate.warningCodes());
			if (candidate.constraintVerdict() == ConstraintVerdict.UNKNOWN) {
				warnings.add(RecommendationCodes.WARNING_CONSTRAINT_UNKNOWN);
			}

			EngineCandidate annotated =
					annotate(candidate, reranked, originalRank, finalRank, cohortMeans, returned);

			CandidateStage stage = returned ? CandidateStage.RETURNED : CandidateStage.RANKED;
			rows.add(toRow(requestId, annotated, stage, true, originalRank, finalScore, finalRank, returned,
					warnings, batch, createdAt, SourceMode.PERSONALIZED));

			if (returned) {
				returnedCount++;
				returnedItems.add(new RecommendedPlace(candidate.placeId(), finalRank, finalScore,
						annotated.reasonCodes(), List.copyOf(warnings), categoryOf(candidate)));
			}
		}

		for (Filtered exclusion : filtered) {
			rows.add(toRow(requestId, exclusion.candidate(), exclusion.stage(), false, null, null, null, false,
					exclusion.warnings(), batch, createdAt, SourceMode.PERSONALIZED));
		}

		Map<String, Object> metrics = DiversityMetrics.beforeAndAfter(
				DiversityMetrics.topOf(rankable, topK), returnedCandidates,
				reranked.parameters(), reranked.applied());

		return new CandidateAssembly(List.copyOf(rows), List.copyOf(returnedItems),
				generated.size(), rankable.size(), returnedCount, metrics);
	}

	/**
	 * 후보 하나에 재정렬 흔적과 기여도를 붙인 복사본을 만든다. 원본을 고치지 않는 이유는
	 * {@link EngineCandidate} 가 엔진이 돌려준 것이어서, 조립 단계에서 손대면 엔진이 준 것과
	 * 우리가 덧붙인 것을 가를 수 없기 때문이다.
	 */
	private EngineCandidate annotate(EngineCandidate candidate, DiversityReranker.Reranked reranked,
			int originalRank, int finalRank, Map<String, Double> cohortMeans, boolean returned) {

		Map<String, Object> components = new LinkedHashMap<>(
				(candidate.scoreComponents() == null) ? Map.of() : candidate.scoreComponents());

		Map<String, Object> diversity = new LinkedHashMap<>(reranked.parameters());
		diversity.put("originalRank", originalRank);
		diversity.put("finalRank", finalRank);
		diversity.put("moved", originalRank != finalRank);
		components.put("diversity", diversity);

		List<String> reasonCodes = new ArrayList<>(candidate.reasonCodes());

		// 기여도는 반환되는 후보에만 붙인다. 대조 기준선이 반환 집합의 평균이라, 그 집합 밖의
		// 후보에 붙이면 다른 기준으로 잰 값이 같은 칸에 섞인다.
		if (returned) {
			components.put(ReasonRanking.COMPONENT_KEY, ReasonRanking.of(candidate, cohortMeans));
			String topAxis = ReasonRanking.distinctiveAxisOf(candidate, cohortMeans);
			if (topAxis != null) {
				reasonCodes.add(RecommendationCodes.REASON_TOP_CONTRIBUTOR_PREFIX + topAxis);
			}
		}
		if (originalRank != finalRank) {
			reasonCodes.add(RecommendationCodes.REASON_DIVERSITY_RERANKED);
		}

		return new EngineCandidate(candidate.placeId(), candidate.candidateSource(),
				candidate.constraintVerdict(), candidate.violations(), candidate.unknownFacts(),
				candidate.constraintConfidence(), candidate.featureValues(), components,
				candidate.preRankScore(), reasonCodes, candidate.warningCodes());
	}

	/**
	 * 후보의 갈래({@code place.category}). {@code BaselineCandidateScorer} 가 이미
	 * {@code featureValues} 에 {@code "category"} 로 실어 둔다. 없으면 {@code null} 이고,
	 * 그때는 일정 쪽이 갈래를 모르는 것으로 다룬다 — 지어내지 않는다.
	 */
	private static String categoryOf(EngineCandidate candidate) {
		Object value = candidate.featureValues() == null ? null : candidate.featureValues().get("category");
		return (value instanceof String text && !text.isBlank()) ? text : null;
	}

	/**
	 * Editor's Pick 을 그대로 결과로 만든다 — 순위를 다시 매기지 않는다.
	 *
	 * <p>{@link #assemble} 과 별도 메서드인 것은 두 경로가 순위를 정하는 방식이 정반대이기
	 * 때문이다. 깃발 하나로 합치면 이 클래스가 약속한 불변식 다섯 중 둘이 "깃발에 따라
	 * 다르다" 가 된다. 다만 제약 판정과 행 만들기는 {@link #appraise}·{@link #toRow} 를
	 * 그대로 쓴다 — 같은 것을 두 번 구현하면 한쪽만 고쳐지는 날이 오고, 그 한쪽이 알레르기
	 * 필터다. 그래서 Pick 도 FAIL 이면 반환되지 않고 하드 제약이 미확인이면 빠진다.
	 *
	 * <p>여기서만 다른 불변식이 둘이다. 점수가 없어도 순위를 붙인다 — Pick 은 순위가 점수와
	 * 무관하게 정해져 있어 점수 없음이 결측이 아니라 사실이다. {@code finalScore} 는
	 * {@code null} 로 남긴다: 1/rank 같은 숫자를 채우면 final_score 로 집계하는 질의가 그
	 * 가짜 값을 개인화 점수와 섞어 센다. 그리고 순위는 살아남은 것들로 촘촘하게 다시 붙여
	 * 목록에 구멍이 생기지 않게 한다 — 편집자가 적은 원래 자리는 {@code original_rank} 에 남는다.
	 *
	 * @param batch 후보가 편집자가 정한 순서대로 들어 있어야 한다. 들어온 순서가 곧 순위다 —
	 *     여기서 정렬하지 않는다
	 */
	public CandidateAssembly assembleFixedOrder(UUID requestId, EngineCandidateBatch batch, int topK,
			UnknownExclusionThreshold threshold, ConstraintSeverity unspecifiedSeverity,
			OffsetDateTime createdAt) {

		List<EngineCandidate> generated = batch.candidates();
		rejectDuplicatePlaces(generated);

		List<Ordered> keepable = new ArrayList<>();
		List<Filtered> filtered = new ArrayList<>();

		for (int position = 0; position < generated.size(); position++) {
			EngineCandidate candidate = generated.get(position);
			List<String> warnings = new ArrayList<>(candidate.warningCodes());
			Filtered exclusion = appraise(candidate, threshold, unspecifiedSeverity, warnings);
			if (exclusion != null) {
				filtered.add(exclusion);
			}
			else {
				// 점수가 없다고 빼지 않는다 — assemble 과 여기가 갈리는 지점이다.
				keepable.add(new Ordered(candidate, position + 1));
			}
		}

		List<RecommendationCandidate> rows = new ArrayList<>(generated.size());
		List<RecommendedPlace> returnedItems = new ArrayList<>();
		int returnedCount = 0;

		for (int index = 0; index < keepable.size(); index++) {
			Ordered ordered = keepable.get(index);
			EngineCandidate candidate = ordered.candidate();
			int rank = index + 1;
			boolean returned = rank <= topK;

			List<String> warnings = new ArrayList<>(candidate.warningCodes());
			if (candidate.constraintVerdict() == ConstraintVerdict.UNKNOWN) {
				warnings.add(RecommendationCodes.WARNING_CONSTRAINT_UNKNOWN);
			}

			CandidateStage stage = returned ? CandidateStage.RETURNED : CandidateStage.RANKED;
			// originalRank = 편집자가 적은 자리, finalRank = 살아남은 것들 사이의 자리.
			rows.add(toRow(requestId, candidate, stage, true, ordered.editorialPosition(), null, rank,
					returned, warnings, batch, createdAt, SourceMode.EDITORIAL_PICK));

			if (returned) {
				returnedCount++;
				returnedItems.add(new RecommendedPlace(candidate.placeId(), rank, null,
						candidate.reasonCodes(), List.copyOf(warnings), categoryOf(candidate)));
			}
		}

		for (Filtered exclusion : filtered) {
			rows.add(toRow(requestId, exclusion.candidate(), exclusion.stage(), false, null, null, null, false,
					exclusion.warnings(), batch, createdAt, SourceMode.EDITORIAL_PICK));
		}

		// Pick 은 재정렬하지 않으므로 before·after 가 같다. 그래도 남긴다 — 지표가 아예 없으면
		// "Pick 이라 안 쟀다" 와 "재정렬이 아무것도 안 바꿨다" 를 가를 수 없다.
		List<EngineCandidate> returnedCandidates = returnedItems.stream()
				.map((item) -> keepable.stream()
						.map(Ordered::candidate)
						.filter((c) -> c.placeId().equals(item.placeId()))
						.findFirst()
						.orElse(null))
				.filter((c) -> c != null)
				.toList();
		Map<String, Object> metrics = DiversityMetrics.unchanged(returnedCandidates);

		return new CandidateAssembly(List.copyOf(rows), List.copyOf(returnedItems),
				generated.size(), keepable.size(), returnedCount, metrics);
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
		if (candidate.hardFailed()) {
			// 제약 위반이 확인됐다. 점수로 되살리지 않는다.
			return new Filtered(candidate, CandidateStage.HARD_FILTERED, warnings);
		}

		// 여기부터 UNKNOWN. 판정을 PASS 로 바꾸지 않는다 — 정하는 것은 결과에 담을지뿐이다.
		warnings.add(RecommendationCodes.WARNING_CONSTRAINT_UNKNOWN);

		ConstraintSeverity worst = worstUnknownSeverity(candidate, unspecifiedSeverity);
		if (threshold.excludes(worst)) {
			// 이 등급의 제약을 확인하지 못했다. 경고로 내보내지 않는다 — "확인 필요" 는
			// 사용자가 대신 확인할 수 있을 때만 정보이고, 그렇지 않으면 책임 전가다.
			warnings.add(RecommendationCodes.WARNING_UNKNOWN_CONSTRAINT_EXCLUDED);
			warnings.add(RecommendationCodes.warningForSeverity(worst));
			return new Filtered(candidate, CandidateStage.QUALITY_FILTERED, warnings);
		}
		return null;
	}

	/**
	 * 확인하지 못한 제약 중 가장 위험한 등급.
	 *
	 * <p>등급이 안 왔거나 모르는 이름이면 {@code unspecifiedSeverity} 가 정한다 — 없는 값을
	 * 낙관적으로 채우지 않는다. 미확인 사실이 하나도 없는데 판정만 UNKNOWN 인 경우도 같다.
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
			List<String> warnings, EngineCandidateBatch batch, OffsetDateTime createdAt,
			SourceMode sourceMode) {

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
				.sourceMode(sourceMode)
				.createdAt(createdAt)
				.build();
	}

	/** JSONB 로 들어가는 것 넷을 저장 전에 본다. 들어간 뒤에는 지우는 비용이 다르다. */
	private void verifyNoSensitiveData(EngineCandidate candidate) {
		String prefix = "candidate[" + candidate.placeId() + "]";
		this.sensitivePayloadGuard.verify(candidate.featureValues(), prefix + ".feature_values");
		this.sensitivePayloadGuard.verify(candidate.scoreComponents(), prefix + ".score_components");
		this.sensitivePayloadGuard.verify(candidate.violations(), prefix + ".violations");
		this.sensitivePayloadGuard.verify(candidate.unknownFacts(), prefix + ".unknown_facts");
	}

	/**
	 * 같은 요청에 같은 장소가 두 번 오면 여기서 멈춘다. DB 의 UNIQUE 도 같은 것을 막지만,
	 * 거기까지 가면 예외가 JDBC 안쪽에서 나서 어느 후보가 겹쳤는지를 알려주지 못한다.
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

	/**
	 * Pick 후보 하나와 편집자가 적어 둔 자리. 걸러진 것 때문에 최종 순위가 당겨져도 원래
	 * 자리를 잃지 않으려고 함께 들고 다닌다 — 그 값이 {@code original_rank} 로 남는다.
	 */
	private record Ordered(EngineCandidate candidate, int editorialPosition) {
	}
}
