package com.gabolle.backend.recommendation.adapter;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.place.api.PlaceCandidateRequest;
import com.gabolle.backend.place.domain.UserInputKind;
import com.gabolle.backend.place.repository.UserPlaceCodeMapRepository;
import com.gabolle.backend.recommendation.config.BaselineEngineProperties;
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
@ConditionalOnBean(UserPlaceCodeMapRepository.class)
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
	 * @param trip 출발지(originLat/originLng)를 여기서 읽는다. 좌표가 없으면 이 메서드를 부르기
	 *     전에 {@code BaselineRecommendationEngine} 이 이미 {@code ENGINE_ORIGIN_MISSING} 으로
	 *     막는다 — 여기서는 좌표가 있다고 가정한다
	 * @param preferenceSnapshot 취향 스냅샷. 카테고리 필터는 이 안의 {@code CATEGORY} 답에서만
	 *     가져온다. 없으면(취향을 하나도 안 답했으면) 카테고리로 좁히지 않는다
	 * @param constraints 제약 스냅샷의 낱개 제약들. 🔴 <b>일부러 쓰지 않는다</b> — 위 클래스
	 *     주석 참고. 파라미터로는 받아 두는데, 나중에 "왜 제약을 안 쓰냐" 는 질문에 이 자리가
	 *     "받았지만 의도적으로 안 썼다" 는 증거로 남게 하기 위해서다
	 */
	public PlaceCandidateRequest translate(Trip trip, PreferenceSnapshot preferenceSnapshot,
			List<TripConstraint> constraints) {

		PlaceCandidateRequest.Center center = new PlaceCandidateRequest.Center(trip.originLat(), trip.originLng());
		List<String> categories = extractCategoryCodes(preferenceSnapshot);

		return new PlaceCandidateRequest(
				center,
				this.properties.radiusM(),
				categories,
				List.of(), // requiredFeatures — 🔴 절대 채우지 않는다
				List.of(), // excludedFeatures — 🔴 절대 채우지 않는다
				null, // openNowAt — 영업시간 필터는 아직 없다
				null, // minimumCount — 모자라면 모자란 채로 돌려받는다
				this.properties.candidateLimit());
	}

	/**
	 * 취향의 {@code CATEGORY} 답에서 카테고리 코드를 뽑는다.
	 *
	 * <p>🔴 사용자 입력 코드 → 장소 표식 유형 대조는 {@link UserPlaceCodeMapRepository} 로 DB 에서
	 * 읽는다 — 자바에 갈래를 하드코딩하지 않는다. 이 대조표가 {@code PREFERENCE/CATEGORY} 를
	 * 아직 {@code INTEREST_TAG} 와 잇지 않았다면(온톨로지 배선이 안 끝났다면), 존재하지 않는
	 * 관계를 자바가 지어내 카테고리로 후보를 좁히지 않는다.
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
