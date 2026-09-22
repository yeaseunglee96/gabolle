package com.gabolle.backend.tools.application;

import com.gabolle.backend.tools.domain.TranslationDirection;

/**
 * 바깥 번역 업체에 문장을 맡기는 자리. {@code RouteProviderPort} 와 달리 실패를 빈 값이 아니라 예외로
 * 던진다 — 경로는 직선거리로 추정할 수 있지만 번역은 대신할 추정이 없고, 지어낸 문장을 화면은 실제
 * 번역인 줄 안다.
 */
public interface TranslationVendorPort {

	/**
	 * @throws TranslationVendorException 키가 없거나, 호출이 실패했거나, 업체가 결과를 못 줬다
	 */
	String translate(String sourceText, TranslationDirection direction);

	/** 로그와 응답 진단에 남길 이름. */
	String providerName();
}
