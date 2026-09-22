package com.gabolle.backend.route.application;

import java.util.Optional;

import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.domain.RouteQuery;
import com.gabolle.backend.route.domain.TravelMode;

/**
 * 바깥 길찾기 업체에 경로를 묻는 자리. 업체가 죽거나 느리거나 그 이동수단을 지원하지 않는
 * 것은 정상 흐름이라 예외가 아니라 빈 값으로 돌려준다. 진짜 예외(설정이 어긋난 것)만 던진다.
 */
public interface RouteProviderPort {

	/** 이 업체가 그 이동수단의 경로를 계산할 수 있나. */
	boolean supports(TravelMode mode);

	/** 업체가 못 주면(호출 실패·경로 없음·미지원) 빈 값이다. */
	Optional<RouteLeg> find(RouteQuery query);

	/** 로그와 응답에 남길 이름. */
	String providerName();
}
