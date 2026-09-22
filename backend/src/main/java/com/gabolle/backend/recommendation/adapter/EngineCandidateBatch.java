package com.gabolle.backend.recommendation.adapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.gabolle.backend.recommendation.domain.FallbackMode;
import com.gabolle.backend.recommendation.domain.RequestLocation;

/**
 * 추천 엔진이 한 요청에 대해 돌려준 것 전부.
 *
 * {@code candidates} 에는 엔진이 스스로 탈락시킨 후보도 포함한다 — 여기서 빠지면 그 후보가
 * 있었다는 사실 자체가 사라진다. {@code resolvedLocation} 은 엔진이 실제로 중심으로 쓴
 * 출발지다. 요청에 현재 위치가 없으면 엔진이 여행 출발지
 * ({@link RequestLocation.LocationSource#TRIP_ORIGIN})로 대신하는데, 그 선택을 아는 것은
 * 엔진뿐이라 되돌려 받는다. 이 값 자체는 저장되지 않고 여기서 뽑은 1km 칸과 출처만 남는다
 * ({@code RecommendationJob.applyOrigin}).
 */
public record EngineCandidateBatch(
		List<EngineCandidate> candidates,
		EngineVersions versions,
		EngineLatencies latencies,
		FallbackMode fallbackMode,
		String fallbackReason,
		RequestLocation resolvedLocation) {

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
		// resolvedLocation 은 필수가 아니다. 위치라는 개념이 없는 엔진(Editor's Pick
		// 기준선)에서는 null 이 사실이고, 강제하면 그 엔진이 좌표를 지어내게 된다.
	}

	/** 중심 좌표라는 개념이 없는 엔진용. Editor's Pick 기준선이 이것을 쓴다. */
	public EngineCandidateBatch(List<EngineCandidate> candidates, EngineVersions versions,
			EngineLatencies latencies, FallbackMode fallbackMode, String fallbackReason) {
		this(candidates, versions, latencies, fallbackMode, fallbackReason, null);
	}
}
