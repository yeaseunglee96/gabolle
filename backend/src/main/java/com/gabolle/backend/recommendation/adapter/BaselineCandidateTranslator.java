package com.gabolle.backend.recommendation.adapter;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.place.api.PlaceCandidateRequest;
import com.gabolle.backend.place.repository.UserPlaceCodeMapRepository;
import com.gabolle.backend.recommendation.config.BaselineEngineProperties;
import com.gabolle.backend.recommendation.domain.RequestLocation;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripConstraint;

import tools.jackson.databind.ObjectMapper;

/**
 * 여행 맥락 + 취향/제약 스냅샷 → {@link PlaceCandidateRequest}.
 *
 * <p>{@code requiredFeatures}·{@code excludedFeatures} 를 채우지 않는다. 질의에서
 * 알레르기·이동 제약으로 걸러 버리면 탈락한 후보의 행이 {@code recommendation_candidate}
 * 에 남지 않아 "왜 빈손이었나" 를 물을 수 없게 된다. 이 클래스는 지리(중심+반경)만 좁히고,
 * 제약 판정은 {@link BaselineCandidateScorer} 가 후보 하나하나에 대해 전부 담아서 한다.
 *
 * <p>🔴 **카테고리로도 좁히지 않는다** (2026-09-23, S15P21E201-1535). 전에는 사용자가 고른
 * 여행 테마를 {@code place.category} 와 글자 그대로 비교해 후보를 잘랐다. 그런데 운영 장소가
 * 갈래마다 크게 기울어 있어서(실측: FOOD 4,387 · SEA_BEACH 16 · CITY 85) 「바다」 하나를 고른
 * 사람은 후보가 16곳 이하가 되어 일정이 실패하거나 식당 없는 하루가 됐다. 테마는 이제
 * {@link BaselineCandidateScorer} 의 관심 항에서 «가산점» 으로 반영된다 — 고른 갈래가 위로
 * 올라오되 끼니·쉼 자리는 남는다.
 *
 * <p>배선은 {@code @ConditionalOnBean} 이 아니라 슬라이스의 스캔 목록으로 정한다 — 그 조건은
 * 자동 설정용이라 직접 스캔하는 {@code @Component} 에서는 평가 시점이 스캔 순서에 달린다.
 */
@Component
@Profile({ "db", "dev" })
public class BaselineCandidateTranslator {

	private final BaselineEngineProperties properties;

	/**
	 * 대조표·JSON 은 테마로 후보를 좁히던 시절에 쓰던 것이다. 지금은 안 쓰지만 생성자 모양은 그대로
	 * 둔다 — 엔진 시험 넷이 이 모양으로 조립하고, 바꿀 이유가 이 PR 의 목적 밖이다.
	 */
	public BaselineCandidateTranslator(BaselineEngineProperties properties,
			UserPlaceCodeMapRepository codeMapRepository, ObjectMapper objectMapper) {
		this.properties = properties;
	}

	/**
	 * @param location 후보 조회의 중심. 요청이 준 현재 위치이거나 여행 출발지이고, 둘 다
	 *     없으면 엔진이 이 메서드를 부르기 전에 막는다. 거리 계산에만 쓰이고 저장되지 않는다
	 * @param trip 여행. 중심 좌표는 여기서 읽지 않는다 — {@code location} 이 정본이다
	 * @param preferenceSnapshot 취향 스냅샷. 후보를 좁히는 데 쓰지 않는다(클래스 주석) — 테마는 채점에서 본다
	 * @param constraints 제약 스냅샷의 낱개 제약들. 일부러 쓰지 않는다(클래스 주석 참고) —
	 *     받아 두는 것은 의도적으로 안 썼다는 증거로 남기기 위해서다
	 */
	public PlaceCandidateRequest translate(RequestLocation location, Trip trip,
			PreferenceSnapshot preferenceSnapshot, List<TripConstraint> constraints) {

		PlaceCandidateRequest.Center center = new PlaceCandidateRequest.Center(location.lat(), location.lng());

		return new PlaceCandidateRequest(
				center,
				this.properties.radiusM(),
				List.of(), // categories — 좁히지 않는다(클래스 주석)
				List.of(), // requiredFeatures — 채우지 않는다
				List.of(), // excludedFeatures — 채우지 않는다
				// openNowAt 은 비운 채로 둔다. 이 조회는 여행 전체에 한 번 부르고 시각 칸은
				// 한 순간이라, 첫날 아침을 넣으면 화요일 오후에 방문할 곳까지 월요일 아침
				// 기준으로 걸러진다. 영업시간은 항목을 자리에 앉히는 단계에서 본다.
				null,
				null, // minimumCount — 모자라면 모자란 채로 돌려받는다
				// candidateLimit 이 아니라 scanLimit 이다. 장소 조회는 점수를 모르므로 limit 을
				// 가까운 순으로 자르고, 그러면 채점 대상이 반경이 아니라 근접 N곳이 된다.
				// 상위 N 은 채점을 마친 뒤 BaselineRecommendationEngine 이 자른다.
				this.properties.candidateScanLimit());
	}

}
