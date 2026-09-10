package com.gabolle.backend.tools.application;

import com.gabolle.backend.tools.domain.TranslationDirection;

/**
 * 바깥 번역 업체에 문장을 맡기는 자리 — S15P21E201-343.
 *
 * <p>🔴 {@code RouteProviderPort} 와 다르게 <b>실패를 빈 값이 아니라 예외로 던진다.</b> 경로는
 * 못 구해도 직선거리로 추정해서 계속 답할 수 있지만, 번역은 대신할 추정이 없다 — 업체가
 * 없는데 문장을 지어내면 그건 번역이 아니라 창작이고, 화면은 그것을 실제 번역인 줄 안다.
 * 그래서 실패는 {@link TranslationVendorException} 으로 명확히 올라가야 한다.
 */
public interface TranslationVendorPort {

	/**
	 * @throws TranslationVendorException 키가 없거나, 호출이 실패했거나, 업체가 결과를 못 줬다
	 */
	String translate(String sourceText, TranslationDirection direction);

	/** 로그와 응답 진단에 남길 이름. */
	String providerName();
}
