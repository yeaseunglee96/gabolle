package com.gabolle.backend.route.application;

import java.util.Optional;

import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.domain.RouteQuery;
import com.gabolle.backend.route.domain.TravelMode;

/**
 * 바깥 길찾기 업체에 경로를 묻는 자리 — S15P21E201-184.
 *
 * <p>🔴 <b>실패를 예외가 아니라 빈 값으로 돌려준다.</b> 업체가 죽거나 느리거나 그 이동수단을
 * 지원하지 않는 것은 <b>정상 흐름의 한 갈래</b>다 — 그때는 직선거리 추정으로 답해야 하고
 * (S15P21E201-189), 그 갈래를 예외로 표현하면 부르는 쪽이 try-catch 로 흐름을 만들게 된다.
 * 진짜 예외(설정이 어긋났다 같은 것)만 던진다.
 */
public interface RouteProviderPort {

	/** 이 업체가 그 이동수단의 경로를 계산할 수 있나. */
	boolean supports(TravelMode mode);

	/**
	 * @return 경로. 업체가 못 주면(호출 실패·경로 없음·미지원) 빈 값
	 */
	Optional<RouteLeg> find(RouteQuery query);

	/** 로그와 응답에 남길 이름. */
	String providerName();
}
