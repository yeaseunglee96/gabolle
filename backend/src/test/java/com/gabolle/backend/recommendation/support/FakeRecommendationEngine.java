package com.gabolle.backend.recommendation.support;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.gabolle.backend.recommendation.adapter.EngineCandidate;
import com.gabolle.backend.recommendation.adapter.EngineCandidateBatch;
import com.gabolle.backend.recommendation.adapter.EngineLatencies;
import com.gabolle.backend.recommendation.adapter.EngineRequest;
import com.gabolle.backend.recommendation.adapter.EngineVersions;
import com.gabolle.backend.recommendation.adapter.RecommendationEngineException;
import com.gabolle.backend.recommendation.adapter.RecommendationEnginePort;
import com.gabolle.backend.recommendation.domain.ConstraintSeverity;
import com.gabolle.backend.recommendation.domain.ConstraintVerdict;
import com.gabolle.backend.recommendation.domain.FallbackMode;

/**
 * 테스트가 원하는 답을 그대로 돌려주는 엔진 대역. 마지막으로 받은 {@link EngineRequest} 를
 * 들고 있어서 백엔드가 만든 request_id 가 엔진까지 그대로 가는지도 확인할 수 있다.
 */
public class FakeRecommendationEngine implements RecommendationEnginePort {

	private EngineCandidateBatch nextBatch = emptyBatch();

	private RuntimeException nextFailure;

	private EngineRequest lastRequest;

	@Override
	public EngineCandidateBatch generate(EngineRequest request) {
		this.lastRequest = request;
		if (this.nextFailure != null) {
			throw this.nextFailure;
		}
		return this.nextBatch;
	}

	public void willReturn(EngineCandidateBatch batch) {
		this.nextBatch = batch;
		this.nextFailure = null;
	}

	public void willFail(RuntimeException failure) {
		this.nextFailure = failure;
	}

	public void willFailWith(String errorCode, boolean timeout) {
		willFail(new RecommendationEngineException(errorCode, "테스트용 엔진 실패", timeout, null));
	}

	public void reset() {
		this.nextBatch = emptyBatch();
		this.nextFailure = null;
		this.lastRequest = null;
	}

	public EngineRequest lastRequest() {
		return this.lastRequest;
	}

	public static EngineVersions versions() {
		return new EngineVersions("model-1.2.0", "feature-3", "ontology-2026-09-01", "policy-2026-09-01",
				"dataset-2026-08-31");
	}

	public static EngineCandidateBatch emptyBatch() {
		return new EngineCandidateBatch(List.of(), versions(), EngineLatencies.unmeasured(), FallbackMode.RULE, null);
	}

	public static EngineCandidateBatch batchOf(List<EngineCandidate> candidates) {
		return new EngineCandidateBatch(candidates, versions(),
				new EngineLatencies(12L, 3L, 4L, 5L, null), FallbackMode.RULE, null);
	}

	/** 통과한 후보 하나. 점수가 높을수록 위로 간다. */
	public static EngineCandidate passing(UUID placeId, double score) {
		return new EngineCandidate(placeId, "ONTOLOGY_SEED", ConstraintVerdict.PASS, List.of(), List.of(), 0.99,
				Map.of("quietness_score", 0.7, "category_code", "CAFE"),
				Map.of("base", score),
				score, List.of("QUIET_PLACE"), List.of());
	}

	/** 하드 제약을 위반한 후보. 어떤 경로로도 노출되면 안 된다. */
	public static EngineCandidate failing(UUID placeId, String violationCode) {
		return new EngineCandidate(placeId, "ONTOLOGY_SEED", ConstraintVerdict.FAIL,
				List.of(Map.of("code", violationCode)), List.of(), 1.0,
				Map.of("category_code", "RESTAURANT"), Map.of(), null, List.of(), List.of());
	}

	/**
	 * 확인하지 못한 제약에 등급이 붙어 온 후보. 예: 땅콩 여부 모름({@code REQUIRED}),
	 * 그늘 여부 모름({@code PREFERRED}).
	 */
	public static EngineCandidate unknownWith(UUID placeId, String unknownFactCode,
			ConstraintSeverity severity) {
		return unknown(placeId, Map.of("fact", unknownFactCode, "severity", severity.name()));
	}

	/**
	 * 등급 없이 온 미확인 후보. 온톨로지가 아직 severity 를 안 실어 보낼 때의 모습이고,
	 * 기본 설정에서는 가장 위험한 등급으로 간주된다.
	 */
	public static EngineCandidate unknownUnrated(UUID placeId, String unknownFactCode) {
		return unknown(placeId, Map.of("fact", unknownFactCode));
	}

	/** 판정에 필요한 사실을 확인하지 못한 후보. PASS 로 바뀌면 안 된다. */
	private static EngineCandidate unknown(UUID placeId, Map<String, Object> unknownFact) {
		return new EngineCandidate(placeId, "ONTOLOGY_SEED", ConstraintVerdict.UNKNOWN, List.of(),
				List.of(unknownFact), 0.3,
				Map.of("category_code", "MUSEUM"), Map.of(), 0.5, List.of(), List.of());
	}

	/** 점수를 못 받은 후보. 순위를 붙이면 안 된다. */
	public static EngineCandidate scoreless(UUID placeId) {
		return new EngineCandidate(placeId, "ONTOLOGY_SEED", ConstraintVerdict.PASS, List.of(), List.of(), 1.0,
				Map.of("category_code", "PARK"), Map.of(), null, List.of(), List.of());
	}
}
