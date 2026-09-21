package com.gabolle.backend.route.transit;

import org.springframework.stereotype.Component;

/**
 * 노선망이 아직 없을 때 쓰는 자리. 빈을 아예 안 두는 대신 빈 노선망을 정상 답으로
 * 돌려주면, 탐색기는 빈 답을 내고 호출자는 직선거리 어림값으로 가서 흐름이 하나가 된다.
 *
 * 조건 없이 등록한다. @ConditionalOnMissingBean 은 자동설정 전용이라 컴포넌트 스캔에서는
 * 조용히 무시된다. 그래서 진짜 노선망 구현을 더할 때는 그쪽에 @Primary 를 붙여야 하고,
 * 안 붙이면 스프링이 어느 빈을 쓸지 몰라 기동을 실패시킨다.
 */
@Component
public class EmptyTransitNetworkPort implements TransitNetworkPort {

	@Override
	public TransitNetwork network() {
		return TransitNetwork.empty();
	}
}
