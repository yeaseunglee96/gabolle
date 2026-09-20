package com.gabolle.backend.assistant.application;

import com.gabolle.backend.assistant.domain.AssistantChatRequest;
import com.gabolle.backend.assistant.domain.AssistantReply;

/**
 * 자연어 메시지를 AI 업체에 맡기는 자리. 실패는 빈 값이 아니라 AssistantVendorException 이다.
 */
public interface AssistantVendorPort {

	/** 키가 없거나 호출이 실패했거나 업체가 결과를 못 주면 AssistantVendorException. */
	AssistantReply reply(AssistantChatRequest request);

	/** 로그와 응답 진단에 남길 이름. */
	String providerName();
}
