package com.gabolle.backend.assistant.application;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.assistant.domain.AssistantReply;

/**
 * 사용자 메시지 하나를 AI 업체에 넘기고 답을 그대로 돌려준다 — S15P21E201-802.
 *
 * <p>대화 기록을 서버가 들고 있지 않는다 — S15P21E201-628 계약의 {@code conversationId} 는
 * 화면이 대화창 안에서만 쓰는 값이고, 이번 티켓 범위는 메시지 한 통을 답 하나로 바꾸는
 * 것까지다. 여러 턴을 서버가 기억해야 하는 요구가 생기면 그때 별도 티켓으로 늘린다.
 *
 * <p>🔴 원문을 로그로 남기지 않는다 — {@code TranslationService} 와 같은 이유로 이 클래스는
 * 로거를 아예 갖지 않는다.
 */
@Service
@Profile({ "db", "dev" })
public class AssistantChatService {

	private static final int MAX_MESSAGE_LENGTH = 1000;

	private final AssistantVendorPort vendor;

	public AssistantChatService(AssistantVendorPort vendor) {
		this.vendor = vendor;
	}

	public AssistantReply chat(String message) {
		if (message == null || message.isBlank()) {
			throw new IllegalArgumentException("message 는 비어 있을 수 없습니다.");
		}
		if (message.length() > MAX_MESSAGE_LENGTH) {
			throw new IllegalArgumentException("message 는 " + MAX_MESSAGE_LENGTH + "자를 넘을 수 없습니다.");
		}

		// 🔴 실패하면 여기서 던진 AssistantVendorException 이 그대로 위로 올라간다.
		return this.vendor.reply(message);
	}
}
