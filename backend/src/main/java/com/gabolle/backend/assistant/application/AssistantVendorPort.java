package com.gabolle.backend.assistant.application;

import com.gabolle.backend.assistant.domain.AssistantReply;

/**
 * 자연어 메시지를 AI 업체(Claude)에 맡기는 자리 — S15P21E201-802.
 *
 * <p>{@code TranslationVendorPort} 와 같은 모양이다 — 실패는 빈 값이 아니라
 * {@link AssistantVendorException} 으로 던진다.
 */
public interface AssistantVendorPort {

	/**
	 * @throws AssistantVendorException 키가 없거나, 호출이 실패했거나, 업체가 결과를 못 줬다
	 */
	AssistantReply reply(String message);

	/** 로그와 응답 진단에 남길 이름. */
	String providerName();
}
