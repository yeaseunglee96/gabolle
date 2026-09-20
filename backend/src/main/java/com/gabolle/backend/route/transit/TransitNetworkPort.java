package com.gabolle.backend.route.transit;

/**
 * 노선망을 어디서 읽는가. 캐시는 구현의 몫이고 이 자리는 "지금 쓸 노선망을 달라" 만 말한다.
 */
public interface TransitNetworkPort {

	/**
	 * 지금 쓸 노선망. 자료가 없으면 예외가 아니라 TransitNetwork.empty() 다 — 노선망이 없는
	 * 것은 정상 흐름이고, 예외로 표현하면 부르는 쪽이 try-catch 로 흐름을 만들게 된다.
	 */
	TransitNetwork network();
}
