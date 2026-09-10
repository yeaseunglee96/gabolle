package com.gabolle.backend.recommendation.adapter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 기동 시점에 추천 엔진이 실제로 배선됐는지 확인한다 (S15P21E201-604).
 *
 * <p><b>왜 필요한가.</b> 엔진 빈이 조용히 안 붙어도 애플리케이션은 정상적으로 뜬다. 그러면
 * 아무도 모르는 채로 며칠 동안 모든 추천 요청이 {@code ENGINE_NOT_CONFIGURED} 로 실패한다 —
 * {@code RecommendationService} 가 {@code enginePort.getIfAvailable() == null} 일 때 그렇게
 * 실패시키기 때문이다. <b>안 뜨는 배포가 낫다</b> — 기동 시점에 크게 실패하면 배포
 * 파이프라인이 잡고, 실행 중에 조용히 실패하면 사용자가 잡는다.
 *
 * <p>{@code AuthStartupValidator}({@code auth/config})가 같은 패턴(기동 시 필수 배선 확인,
 * {@code @PostConstruct} 에서 {@code IllegalStateException})을 쓴다 — 그 패턴을 그대로
 * 따른다.
 *
 * <h2>S15P21E201-808 — 조건을 걷어낸 뒤의 자리</h2>
 * 전에는 이 검사기가 {@link BaselineRecommendationEngine} 과 <b>같은</b>
 * {@code @ConditionalOnBean} 을 달고 있었다. 그래서 엔진이 조용히 빠지는 바로 그 상황에서
 * 검사기도 함께 빠졌다 — 감시하려던 실패에 감시자가 걸린 것이고, 실제로 그렇게 됐다.
 * 조건을 리포지토리에 걸면 순서 문제를 피한다고 적어 뒀었지만 실측은 반대였다.
 *
 * <p>지금은 둘 다 조건이 없다. 엔진이 기대는 패키지를 안 올리는 컨텍스트는 아예 기동하지
 * 못하고, 엔진만 골라 뺀 컨텍스트는 이 검사기가 멈춘다. 어느 쪽도 조용하지 않다.
 */
@Component
@Profile({ "db", "dev" })
// S15P21E201-808 — @ConditionalOnBean 을 걷어냈다. 이 조건은 자동 설정에서 쓰라고 만든
// 것이라 사용자가 직접 스캔하는 @Component 에서는 평가 시점이 스캔 순서에 달려 있고,
// 실측해 보니 dev 프로필 전체 앱에서도 이 빈들이 안 만들어지고 있었다. 배선은 조건이
// 아니라 슬라이스의 스캔 목록으로 정한다.
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
