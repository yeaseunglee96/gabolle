package com.gabolle.backend.recommendation.adapter;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.place.api.PlaceCandidateRequest;
import com.gabolle.backend.place.domain.UserInputKind;
import com.gabolle.backend.place.repository.UserPlaceCodeMapRepository;
import com.gabolle.backend.preference.application.PreferenceJson;
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
 * 에 남지 않아 "왜 빈손이었나" 를 물을 수 없게 된다. 이 클래스는 지리(중심+반경)·카테고리만
 * 좁히고, 제약 판정은 {@link BaselineCandidateScorer} 가 후보 하나하나에 대해 전부 담아서 한다.
 *
 * <p>배선은 {@code @ConditionalOnBean} 이 아니라 슬라이스의 스캔 목록으로 정한다 — 그 조건은
 * 자동 설정용이라 직접 스캔하는 {@code @Component} 에서는 평가 시점이 스캔 순서에 달린다.
 */
@Component
@Profile({ "db", "dev" })
public class BaselineCandidateTranslator {

	private final BaselineEngineProperties properties;

	private final UserPlaceCodeMapRepository codeMapRepository;

	private final ObjectMapper objectMapper;

	public BaselineCandidateTranslator(BaselineEngineProperties properties,
			UserPlaceCodeMapRepository codeMapRepository, ObjectMapper objectMapper) {
		this.properties = properties;
		this.codeMapRepository = codeMapRepository;
		this.objectMapper = objectMapper;
	}

	/**
	 * @param location 후보 조회의 중심. 요청이 준 현재 위치이거나 여행 출발지이고, 둘 다
	 *     없으면 엔진이 이 메서드를 부르기 전에 막는다. 거리 계산에만 쓰이고 저장되지 않는다
	 * @param trip 여행. 중심 좌표는 여기서 읽지 않는다 — {@code location} 이 정본이다
	 * @param preferenceSnapshot 취향 스냅샷. 카테고리 필터는 이 안의 {@code CATEGORY} 답에서만
	 *     가져오고, 없으면 카테고리로 좁히지 않는다
	 * @param constraints 제약 스냅샷의 낱개 제약들. 일부러 쓰지 않는다(클래스 주석 참고) —
	 *     받아 두는 것은 의도적으로 안 썼다는 증거로 남기기 위해서다
	 */
	public PlaceCandidateRequest translate(RequestLocation location, Trip trip,
			PreferenceSnapshot preferenceSnapshot, List<TripConstraint> constraints) {

		PlaceCandidateRequest.Center center = new PlaceCandidateRequest.Center(location.lat(), location.lng());
		List<String> categories = extractCategoryCodes(preferenceSnapshot);

		return new PlaceCandidateRequest(
				center,
				this.properties.radiusM(),
				categories,
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

	/**
	 * 취향의 {@code CATEGORY} 답에서 카테고리 코드를 뽑는다.
	 *
	 * <p>사용자 입력 코드 → 장소 표식 유형 대조는 {@link UserPlaceCodeMapRepository} 로 DB 에서
	 * 읽는다 — 자바에 갈래를 하드코딩하지 않는다. 대조표가 {@code PREFERENCE/CATEGORY} 를 아직
	 * 잇지 않았으면 존재하지 않는 관계를 지어내 후보를 좁히지 않고 빈 목록을 돌려준다.
	 *
	 * <p>여기 담기는 값은 앱의 코드({@code SEA_BEACH}·{@code CITY}·{@code CAFE_HEALING}·
	 * {@code CULTURE_TEMPLE}·{@code FOOD}·{@code NATURE_WALK})이고 {@code place.category} 와
	 * 글자 그대로 비교된다. 그러니 {@code place} 를 채우는 쪽이 같은 코드를 넣어야 한다 —
	 * 안 그러면 후보가 0건이 되고, 그 0건은 "조건에 맞는 곳이 없다" 로 보이지 "어휘가 안
	 * 맞는다" 로는 안 보인다.
	 *
	 * <p>적재분이 음식에 크게 쏠려 있어 다른 갈래를 고른 사용자는 후보가 거의 안 나온다. 이건
	 * 정상이 아니라 알려진 결함이고, 갈래별 최소 정원이 필요하다 — 그 자리는 이 클래스가 아니라
	 * 일정 조립 쪽({@code ItineraryDraftService})이다.
	 */
	private List<String> extractCategoryCodes(PreferenceSnapshot preferenceSnapshot) {
		if (preferenceSnapshot == null) {
			return List.of();
		}
		boolean categoryIsMapped = !this.codeMapRepository
				.findByIdUserInputKindAndIdUserInputCode(UserInputKind.PREFERENCE, "CATEGORY")
				.isEmpty();
		if (!categoryIsMapped) {
			return List.of();
		}
		return PreferenceJson.codesFor(preferenceSnapshot, "CATEGORY", this.objectMapper);
	}
}
