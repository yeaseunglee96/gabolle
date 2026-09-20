package com.gabolle.backend.assistant.application;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.assistant.config.AssistantProperties;
import com.gabolle.backend.assistant.domain.AssistantChatRequest;
import com.gabolle.backend.assistant.domain.AssistantReply;
import com.gabolle.backend.assistant.domain.AssistantTurn;
import com.gabolle.backend.user.application.ConsentGuard;

/**
 * 사용자 메시지 하나를 AI 업체에 넘기고 답을 그대로 돌려준다.
 *
 * 대화 기록은 서버가 저장하지 않는다 — 화면이 최근 몇 턴을 매 요청마다 함께 보내고 여기서
 * 개수·길이만 다듬는다. 무상태라 인스턴스를 늘려도 되는 대신 새로고침하면 대화가 끊긴다.
 *
 * 원문을 로그로 남기지 않으려고 이 클래스는 로거를 아예 갖지 않는다.
 *
 * itineraryId·dayIndex 가 함께 오면 그 하루치 일정을 읽어 벤더에 얹는다. 사용자가 직접 쓴
 * message 와는 다른 종류의 노출이라 AI_ASSISTANT_ACCESS 동의를 확인한 뒤에만 한다. 동의가
 * 없으면 일정만 빼고 답하는 것이 아니라 요청 전체를 막는다 — 조용히 무시하면 사용자가 왜
 * 원하는 답을 못 받았는지 알 수 없다.
 */
@Service
@Profile({ "db", "dev" })
public class AssistantChatService {

	private static final int MAX_MESSAGE_LENGTH = 1000;

	/** 히스토리 한 턴도 같은 한도를 쓴다. */
	private static final int MAX_TURN_LENGTH = 1000;

	private final AssistantVendorPort vendor;

	private final AssistantRateLimiter rateLimiter;

	private final AssistantProperties properties;

	private final ConsentGuard consentGuard;

	private final AssistantTripContextBuilder tripContextBuilder;

	public AssistantChatService(AssistantVendorPort vendor, AssistantRateLimiter rateLimiter,
			AssistantProperties properties, ConsentGuard consentGuard,
			AssistantTripContextBuilder tripContextBuilder) {
		this.vendor = vendor;
		this.rateLimiter = rateLimiter;
		this.properties = properties;
		this.consentGuard = consentGuard;
		this.tripContextBuilder = tripContextBuilder;
	}

	/**
	 * 메시지가 비었거나 너무 길면 400, 1분 한도를 넘겼으면 429, itineraryId 를 보냈는데
	 * AI_ASSISTANT_ACCESS 동의가 없으면 403, 그 일정이 없거나 회원이 아니면 404,
	 * 업체 호출이 실패하면 502 다.
	 */
	public AssistantReply chat(UUID userId, String message, String language, List<AssistantTurn> history,
			String itineraryId, Integer dayIndex) {
		if (message == null || message.isBlank()) {
			throw new IllegalArgumentException("message 는 비어 있을 수 없습니다.");
		}
		if (message.length() > MAX_MESSAGE_LENGTH) {
			throw new IllegalArgumentException("message 는 " + MAX_MESSAGE_LENGTH + "자를 넘을 수 없습니다.");
		}

		// 벤더를 부르기 전에 막는다. 한도를 넘긴 요청이 무료 티어 호출을 쓰면 안 된다.
		this.rateLimiter.checkAndRecord(userId);

		String normalizedLanguage = normalizeLanguage(language);
		List<AssistantTurn> trimmedHistory = trimHistory(history);

		String tripContext = null;
		if (itineraryId != null && dayIndex != null) {
			this.consentGuard.requireAiAssistantAccess(userId);
			tripContext = this.tripContextBuilder.build(itineraryId, dayIndex, userId);
		}

		// 실패하면 여기서 던진 AssistantVendorException 이 그대로 위로 올라간다.
		return this.vendor.reply(
				new AssistantChatRequest(message, normalizedLanguage, trimmedHistory, tripContext));
	}

	/**
	 * 화면이 보내는 Accept-Language 는 다섯 언어(ko-KR·en-US·ja-JP·zh-CN·zh-TW) 중 하나다.
	 *
	 * 콤마로 잘라 첫 언어 태그만 본다. 헤더 전체를 contains 로 보면 우선순위가 낮은 뒤쪽
	 * 태그가 앞쪽 태그의 판정을 덮어쓴다.
	 */
	private String normalizeLanguage(String language) {
		if (language == null || language.isBlank()) {
			return "ko";
		}
		String primary = language.split(",")[0].split(";")[0].trim().toLowerCase(Locale.ROOT);
		if (primary.startsWith("en")) {
			return "en";
		}
		if (primary.startsWith("ja")) {
			return "ja";
		}
		if (primary.startsWith("zh")) {
			return (primary.contains("hant") || primary.contains("-tw") || primary.contains("-hk")
					|| primary.contains("-mo")) ? "zh-Hant" : "zh-Hans";
		}
		return "ko";
	}

	private List<AssistantTurn> trimHistory(List<AssistantTurn> history) {
		if (history == null || history.isEmpty()) {
			return List.of();
		}
		int maxTurns = this.properties.getMaxHistoryTurns();
		int fromIndex = Math.max(0, history.size() - maxTurns);
		return history.subList(fromIndex, history.size()).stream()
				.map(turn -> new AssistantTurn(normalizeRole(turn.role()), truncate(turn.text())))
				.toList();
	}

	private String normalizeRole(String role) {
		return "assistant".equalsIgnoreCase(role) ? "assistant" : "user";
	}

	private String truncate(String text) {
		if (text == null) {
			return "";
		}
		return text.length() > MAX_TURN_LENGTH ? text.substring(0, MAX_TURN_LENGTH) : text;
	}
}
