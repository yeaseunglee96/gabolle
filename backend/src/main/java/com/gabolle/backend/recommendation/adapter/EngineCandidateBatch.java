package com.gabolle.backend.recommendation.adapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.gabolle.backend.recommendation.domain.FallbackMode;

/**
 * 추천 엔진이 한 요청에 대해 돌려준 것 전부.
 *
 * @param candidates 생성된 후보 전부. 🔴 엔진이 스스로 탈락시킨 것도 포함한다 — 여기서 빠지면
 *     그 후보가 있었다는 사실 자체가 영영 사라진다
 * @param versions 이 결과를 만든 모델·피처·온톨로지·데이터셋 버전
 * @param latencies 단계별 소요 시간
 * @param fallbackMode 무엇이 순위를 매겼는가
 * @param fallbackReason 정상 경로를 못 쓴 이유. 정상 경로였다면 {@code null}
 */
public record EngineCandidateBatch(
		List<EngineCandidate> candidates,
		EngineVersions versions,
		EngineLatencies latencies,
		FallbackMode fallbackMode,
		String fallbackReason) {

	public EngineCandidateBatch {
		candidates = (candidates == null)
				? List.of()
				: Collections.unmodifiableList(new ArrayList<>(candidates));
		if (versions == null) {
			throw new IllegalArgumentException(
					"versions 는 필수다 — 비어 있더라도 무엇이 비었는지는 알아야 실패로 남길 수 있다");
		}
		if (latencies == null) {
			latencies = EngineLatencies.unmeasured();
		}
		if (fallbackMode == null) {
			throw new IllegalArgumentException(
					"fallbackMode 는 필수다 — 무엇이 순위를 매겼는지는 결과의 일부다");
		}
	}
}
