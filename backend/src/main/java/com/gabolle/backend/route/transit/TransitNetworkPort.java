package com.gabolle.backend.route.transit;

/**
 * 노선망을 어디서 읽는가 — S15P21E201-1104.
 *
 * <p>자료가 아직 없어서 지금은 {@link EmptyTransitNetworkPort} 하나뿐이다. 노선망 적재가
 * 끝나면 <b>이 뒤만 갈아 끼운다</b> — 탐색기도 어댑터도 안 고친다.
 *
 * <h2>🔴 왜 매번 묻게 두나</h2>
 *
 * 노선망은 크고 잘 안 바뀌므로 한 번 읽어 들고 있는 편이 빠르다. 그런데 그 판단을
 * <b>여기서</b> 하면 구현마다 다시 하게 된다. 캐시는 구현의 몫으로 두고, 이 자리는
 * "지금 쓸 노선망을 달라" 만 말한다.
 */
public interface TransitNetworkPort {

	/**
	 * 지금 쓸 노선망.
	 *
	 * <p>🔴 <b>자료가 없으면 예외가 아니라 {@link TransitNetwork#empty()} 를 돌려준다.</b>
	 * 노선망이 아직 없는 것은 고장이 아니라 정상 흐름의 한 갈래다 — 그때는 호출자가 직선거리
	 * 어림값으로 간다. 예외로 표현하면 부르는 쪽이 try-catch 로 흐름을 만들게 된다
	 * ({@code RouteProviderPort} 가 같은 이유로 빈 값을 쓴다).
	 */
	TransitNetwork network();
}
