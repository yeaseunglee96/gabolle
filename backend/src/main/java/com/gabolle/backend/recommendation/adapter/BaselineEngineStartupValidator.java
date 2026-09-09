package com.gabolle.backend.recommendation.adapter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.place.repository.UserPlaceCodeMapRepository;

import jakarta.annotation.PostConstruct;

/**
 * 기동 시점에 추천 엔진이 실제로 배선됐는지 확인한다 (S15P21E201-604).
 *
 * <p>🔴 <b>왜 필요한가.</b> {@link BaselineRecommendationEngine} 은 {@code @ConditionalOnBean}
 * 으로 조건부 배선된다 — 조건부 배선은 <b>조용히 안 붙을 수 있다.</b> 예를 들어 이 클래스가
 * 다른 빈 정의 순서·프로필 조합 때문에 등록되지 않아도 애플리케이션은 정상적으로 뜬다.
 * 그러면 아무도 모르는 채로 며칠 동안 모든 추천 요청이 {@code ENGINE_NOT_CONFIGURED} 로
 * 실패한다 — {@code RecommendationService} 가 {@code enginePort.getIfAvailable() == null} 일
 * 때 그렇게 실패시키기 때문이다. <b>안 뜨는 배포가 낫다</b> — 기동 시점에 크게 실패하면
 * 배포 파이프라인이 잡고, 실행 중에 조용히 실패하면 사용자가 잡는다.
 *
 * <p>{@code AuthStartupValidator}({@code auth/config})가 같은 패턴(기동 시 필수 배선 확인,
 * {@code @PostConstruct} 에서 {@code IllegalStateException})을 쓴다 — 그 패턴을 그대로
 * 따른다.
 *
 * <p>🔴 <b>조건을 {@code PlaceCandidateQueryService} 가 아니라
 * {@link UserPlaceCodeMapRepository} 에 건다.</b> 앞의 것은 이 검사기와 똑같은
 * {@code @Component} 라, 스캔 순서가 어긋나 엔진이 조용히 빠지는 바로 그 상황에서
 * <b>검사기도 같이 빠진다</b> — 감시하려던 실패에 감시자가 함께 걸리는 것이다. 리포지토리는
 * {@code @EnableJpaRepositories} 가 컴포넌트 스캔보다 먼저 등록하므로 그 순서 문제에 걸리지
 * 않는다. 장소 데이터 계층이 올라온 컨텍스트라면 추천 엔진도 반드시 있어야 한다는 것이
 * 이 검사기의 주장이고, 리포지토리가 그 전제를 가장 이르게 확인해 준다.
 *
 * <p>{@code place} 패키지를 안 스캔하는 슬라이스 컨텍스트에서는 이 검사 자체가 의미 없다 —
 * 리포지토리가 없으니 검사기도 안 만들어진다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnBean(UserPlaceCodeMapRepository.class)
public class BaselineEngineStartupValidator {

	private final ObjectProvider<RecommendationEnginePort> enginePort;

	public BaselineEngineStartupValidator(ObjectProvider<RecommendationEnginePort> enginePort) {
		this.enginePort = enginePort;
	}

	@PostConstruct
	void validate() {
		if (this.enginePort.getIfAvailable() == null) {
			throw new IllegalStateException(
					"장소 데이터 계층은 올라왔는데 RecommendationEnginePort 빈이 없다 — "
							+ "이대로 뜨면 모든 추천 요청이 ENGINE_NOT_CONFIGURED 로 조용히 실패한다. "
							+ "BaselineRecommendationEngine 의 @ConditionalOnBean 배선을 확인해라.");
		}
	}
}
