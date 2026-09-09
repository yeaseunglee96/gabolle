package com.gabolle.backend.recommendation.adapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.gabolle.backend.recommendation.domain.FallbackMode;
import com.gabolle.backend.recommendation.domain.RequestLocation;

/**
 * 추천 엔진이 한 요청에 대해 돌려준 것 전부.
 *
 * @param candidates 생성된 후보 전부. 🔴 엔진이 스스로 탈락시킨 것도 포함한다 — 여기서 빠지면
 *     그 후보가 있었다는 사실 자체가 영영 사라진다
 * @param versions 이 결과를 만든 모델·피처·온톨로지·데이터셋 버전
 * @param latencies 단계별 소요 시간
 * @param fallbackMode 무엇이 순위를 매겼는가
 * @param fallbackReason 정상 경로를 못 쓴 이유. 정상 경로였다면 {@code null}
 * @param resolvedLocation 🔴 S15P21E201-550 — 엔진이 <b>실제로 중심으로 쓴</b> 출발지.
 *     <p>요청이 현재 위치를 안 주면 엔진이 여행 출발지로 대신하는데
 *     ({@link RequestLocation.LocationSource#TRIP_ORIGIN}), 그 선택을 아는 것은 엔진뿐이다.
 *     {@code RecommendationService} 가 요청에 실려 온 값만 기록하면 <b>대부분의 요청이
 *     "출발지를 모르는 요청" 으로 남는다.</b> 그래서 고른 결과를 되돌려 받는다.
 *     <p>🔴 이 값 자체는 저장되지 않는다 — 저장되는 것은 여기서 뽑은 1km 칸과 출처뿐이다
 *     ({@code RecommendationJob.applyOrigin})
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
		// 🔴 resolvedLocation 은 필수가 아니다. 위치라는 개념이 없는 엔진도 있을 수 있고
		//    (Editor's Pick 기준선이 그렇다 — 편집자가 고른 목록에는 중심이 없다), 그때
		//    null 인 것이 사실이다. 여기서 강제하면 그 엔진이 좌표를 지어내게 된다.
	}

	/**
	 * 중심 좌표라는 개념이 없는 엔진용 — S15P21E201-550 이전의 모양 그대로다.
	 *
	 * <p>Editor's Pick 기준선이 이것을 쓴다. 편집자가 고른 목록은 "어디에서 가까운가" 로
	 * 만든 것이 아니라 사람이 정한 코스라, 중심이 없는 것이 정상이다.
	 */
	public EngineCandidateBatch(List<EngineCandidate> candidates, EngineVersions versions,
			EngineLatencies latencies, FallbackMode fallbackMode, String fallbackReason) {
		this(candidates, versions, latencies, fallbackMode, fallbackReason, null);
	}
}
