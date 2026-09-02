package com.gabolle.backend.recommendation.adapter;

/**
 * 추천 엔진이 각 단계에 쓴 시간(밀리초). 모르는 단계는 {@code null} 로 둔다 —
 * 0 으로 채우면 "쟀는데 0 이었다" 와 "안 쟀다" 가 구분되지 않는다.
 */
public record EngineLatencies(
		Long candidateGenerationMs,
		Long ontologyMs,
		Long featureLookupMs,
		Long rankingMs,
		Long optimizationMs) {

	public static EngineLatencies unmeasured() {
		return new EngineLatencies(null, null, null, null, null);
	}
}
