package com.gabolle.backend.route.transit;

import org.springframework.stereotype.Component;

/**
 * 노선망이 아직 없을 때 쓰는 자리 — S15P21E201-1104.
 *
 * <h2>🔴 왜 "없음" 을 구현체로 만드나</h2>
 *
 * 없을 때 빈을 아예 안 두면, 부르는 쪽이 <b>"있을 수도 없을 수도 있다" 를 매번 다뤄야
 * 한다.</b> 그 분기는 빠뜨리기 쉽고 빠뜨려도 조용하다. 대신 <b>빈 노선망을 정상 답으로
 * 돌려주는 구현</b>을 하나 두면, 탐색기는 빈 답을 내고 호출자는 직선거리 어림값으로 간다 —
 * 흐름이 하나다.
 *
 * <p>{@code NoOpEventPublisher} 가 같은 모양이다. 발행할 곳이 없는 단계에서 발행자를 비워
 * 두는 대신 "아무것도 안 하는 발행자" 를 둔 것과 같은 이유다.
 *
 * <h2>🔴 진짜 노선망 구현을 더할 때 — {@code @Primary} 를 붙여라</h2>
 *
 * 처음에는 이 클래스에 {@code @ConditionalOnMissingBean} 을 붙였는데 <b>그건 컴포넌트
 * 스캔에서 동작하지 않는다.</b> 그 애너테이션은 자동설정(auto-configuration) 전용이고,
 * 스캔 순서가 정해져 있지 않은 자리에서는 조용히 무시된다 — 실제로 빈이 아예 등록되지
 * 않아 배선 시험이 빨개졌다.
 *
 * <p>그래서 조건 없이 등록한다. 구현이 둘이 되면 스프링이 <b>"어느 것을 쓸지 모르겠다" 로
 * 기동을 실패시킨다.</b> 시끄럽지만 그편이 낫다 — 조용히 엉뚱한 쪽을 고르면 대중교통 경로가
 * 늘 빈 답을 내면서도 아무 오류가 안 난다.
 */
@Component
public class EmptyTransitNetworkPort implements TransitNetworkPort {

	@Override
	public TransitNetwork network() {
		return TransitNetwork.empty();
	}
}
