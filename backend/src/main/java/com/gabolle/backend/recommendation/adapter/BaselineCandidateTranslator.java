package com.gabolle.backend.recommendation.adapter;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.place.api.PlaceCandidateRequest;
import com.gabolle.backend.place.domain.UserInputKind;
import com.gabolle.backend.place.repository.UserPlaceCodeMapRepository;
import com.gabolle.backend.recommendation.config.BaselineEngineProperties;
import com.gabolle.backend.recommendation.domain.RequestLocation;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripConstraint;

import tools.jackson.databind.ObjectMapper;

/**
 * 여행 맥락 + 취향/제약 스냅샷 → {@link PlaceCandidateRequest} (S15P21E201-604).
 *
 * <p>🔴 <b>{@code requiredFeatures}·{@code excludedFeatures} 를 절대 채우지 않는다.</b>
 * {@link com.gabolle.backend.recommendation.adapter.EngineCandidateBatch} javadoc 이
 * "엔진이 스스로 탈락시킨 것도 포함한다 — 여기서 빠지면 그 후보가 있었다는 사실 자체가
 * 영영 사라진다" 고 못 박아 뒀다. 질의에서 알레르기·이동 제약으로 걸러 버리면 탈락한
 * 후보의 행이 {@code recommendation_candidate} 에 남지 않는다 — "왜 빈손이었나" 를 영영
 * 못 묻게 된다. 그래서 이 클래스는 지리(중심+반경)·카테고리만 좁히고, 제약 판정은
 * {@link BaselineCandidateScorer} 가 후보 하나하나에 대해 <b>전부 담아서</b> 한다.
 */
/*
 * 🔴 배선 조건이 두 개인 이유. 이 클래스는 user_place_code_map 을 읽어야 하는데, 그 표의
 *    리포지토리는 db·dev 프로필에서만 만들어지고(no-db 는 JPA 자체를 뺀다) 추천만 올리는
 *    테스트 슬라이스에서는 place 패키지를 스캔하지 않아 아예 없다. 조건을
 *    PlaceCandidateQueryService(같은 @Component)가 아니라 리포지토리에 거는 것이
 *    의도적이다 — 리포지토리는 @EnableJpaRepositories 가 컴포넌트 스캔보다 먼저 등록하므로
 *    @ConditionalOnBean 의 평가 순서 문제(빈이 아직 없어서 조건이 거짓이 되는 것)에 걸리지
 *    않는다. 그래도 이 빈이 조용히 빠지면 엔진 생성이 실패해 기동이 멈춘다 —
 *    BaselineEngineStartupValidator 와 같은 방향으로, 조용한 오작동보다 시끄러운 실패를 택한다.
 */
@Component
@Profile({ "db", "dev" })
// S15P21E201-808 — @ConditionalOnBean 을 걷어냈다. 이 조건은 자동 설정에서 쓰라고 만든
// 것이라 사용자가 직접 스캔하는 @Component 에서는 평가 시점이 스캔 순서에 달려 있고,
// 실측해 보니 dev 프로필 전체 앱에서도 이 빈들이 안 만들어지고 있었다. 배선은 조건이
// 아니라 슬라이스의 스캔 목록으로 정한다.
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
	 * @param location 🔴 S15P21E201-550 — 후보 조회의 중심. 요청이 준 현재 위치이거나 여행
	 *     출발지다({@code BaselineRecommendationEngine} 이 골라서 넘긴다). 둘 다 없으면 이
	 *     메서드를 부르기 전에 엔진이 {@code ENGINE_ORIGIN_MISSING} 으로 막는다.
	 *     <p>🔴 거리 계산에만 쓰이고 저장되지 않는다
	 * @param trip 여행. 🔴 중심 좌표는 여기서 읽지 않는다 — {@code location} 이 정본이다.
	 *     요청이 현재 위치를 준 경우 여행 출발지와 다르기 때문이다
	 * @param preferenceSnapshot 취향 스냅샷. 카테고리 필터는 이 안의 {@code CATEGORY} 답에서만
	 *     가져온다. 없으면(취향을 하나도 안 답했으면) 카테고리로 좁히지 않는다
	 * @param constraints 제약 스냅샷의 낱개 제약들. 🔴 <b>일부러 쓰지 않는다</b> — 위 클래스
	 *     주석 참고. 파라미터로는 받아 두는데, 나중에 "왜 제약을 안 쓰냐" 는 질문에 이 자리가
	 *     "받았지만 의도적으로 안 썼다" 는 증거로 남게 하기 위해서다
	 */
	public PlaceCandidateRequest translate(RequestLocation location, Trip trip,
			PreferenceSnapshot preferenceSnapshot, List<TripConstraint> constraints) {

		PlaceCandidateRequest.Center center = new PlaceCandidateRequest.Center(location.lat(), location.lng());
		List<String> categories = extractCategoryCodes(preferenceSnapshot);

		return new PlaceCandidateRequest(
				center,
				this.properties.radiusM(),
				categories,
				List.of(), // requiredFeatures — 🔴 절대 채우지 않는다
				List.of(), // excludedFeatures — 🔴 절대 채우지 않는다
				null, // openNowAt — 영업시간 필터는 아직 없다
				null, // minimumCount — 모자라면 모자란 채로 돌려받는다
				// 🔴 candidateLimit(200) 이었다 (S15P21E201-724). 장소 조회는 점수를 모르므로
				//    limit 을 "가까운 순" 으로 자른다. 여기에 200 을 주면 채점기는 가까운
				//    200곳만 보게 되고, 부산에서는 그것이 중앙값 304m 였다 — 반경 5km 를
				//    잡아 놓고 300m 를 본 셈이다. 채점 대상은 반경 안 전부여야 하고,
				//    "상위 200" 은 채점을 마친 뒤 BaselineRecommendationEngine 이 자른다.
				this.properties.candidateScanLimit());
	}

	/**
	 * 취향의 {@code CATEGORY} 답에서 카테고리 코드를 뽑는다.
	 *
	 * <p>🔴 사용자 입력 코드 → 장소 표식 유형 대조는 {@link UserPlaceCodeMapRepository} 로 DB 에서
	 * 읽는다 — 자바에 갈래를 하드코딩하지 않는다. 이 대조표가 {@code PREFERENCE/CATEGORY} 를
	 * 아직 {@code INTEREST_TAG} 와 잇지 않았다면(온톨로지 배선이 안 끝났다면), 존재하지 않는
	 * 관계를 자바가 지어내 카테고리로 후보를 좁히지 않는다.
	 *
	 * <h2>🔴 2026-09-07 — 이 필터가 <b>지금부터 실제로 동작한다</b> (S15P21E201-635)</h2>
	 *
	 * 지금까지 {@code PreferenceJson} 이 앱이 보내는 맨 배열을 못 읽어서 이 목록이 <b>언제나
	 * 비어 있었고</b>, 그래서 카테고리 필터는 사실상 죽어 있었다. 그 결함을 고치면서 필터가
	 * 살아난다 — 여기 담기는 값은 앱의 코드({@code SEA_BEACH}·{@code CITY}·{@code CAFE_HEALING}·
	 * {@code CULTURE_TEMPLE}·{@code FOOD}·{@code NATURE_WALK})이고, 그것이 {@code place.category}
	 * 와 <b>글자 그대로</b> 비교된다.
	 *
	 * <p>그러니 <b>{@code place} 를 채우는 쪽이 {@code category} 에 앱과 같은 코드를 넣어야 한다.</b>
	 * 안 그러면 후보가 0건이 되고, 그 0건은 "조건에 맞는 곳이 없다" 로 보이지 "어휘가 안 맞는다"
	 * 로는 안 보인다. 지금 적재되는 것은 상가정보 음식 업종뿐이라 {@code category} 는 {@code FOOD}
	 * 하나이고, <b>나머지 다섯 갈래를 고른 사용자는 후보가 없다</b> — 그 갈래의 장소를 아직 안
	 * 넣었기 때문이고, 그것은 사실이다 (S15P21E201-636).
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
