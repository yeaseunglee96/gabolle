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
 * 사용자 메시지 하나를 AI 업체에 넘기고 답을 그대로 돌려준다 — S15P21E201-802.
 *
 * <p>대화 기록을 서버가 저장하지 않는다 — 화면이 이미 갖고 있는 최근 몇 턴({@code history})을
 * 매 요청마다 함께 보내고, 이 서비스는 그것을 다듬어(개수·길이 제한) 벤더에 그대로 넘길
 * 뿐이다. 무상태라 서버 재시작·여러 인스턴스 사이에서도 문제가 없다 — 대신 대화가 화면을
 * 새로고침하면 끊긴다(이 티켓 범위에서는 받아들이는 트레이드오프).
 *
 * <p>🔴 원문을 로그로 남기지 않는다 — {@code TranslationService} 와 같은 이유로 이 클래스는
 * 로거를 아예 갖지 않는다.
 *
 * <h2>🔴 2026-09-16 — 일정 참조(S15P21E201-987)</h2>
 * {@code itineraryId}·{@code dayIndex} 가 함께 오면, 그 하루치 일정을 읽어 벤더에 얹는다.
 * 이건 사용자가 직접 쓴 {@code message} 와는 다른 종류의 노출이라({@code ConsentGuard}
 * javadoc 참고) 별도 동의({@code AI_ASSISTANT_ACCESS})를 확인한 뒤에만 한다. 동의가 없으면
 * 이 요청 전체를 막는다 — "동의한 부분만 쏙 빼고 나머지는 그냥 답한다" 처럼 조용히 기능을
 * 줄이지 않는다. 사용자가 명시적으로 일정 참조를 요청했는데 그 요청이 조용히 무시되면,
 * 사용자는 자기가 왜 원하는 답을 못 받았는지 알 방법이 없다.
 */
@Service
@Profile({ "db", "dev" })
public class AssistantChatService {

	private static final int MAX_MESSAGE_LENGTH = 1000;

	/** 히스토리 한 턴도 같은 한도를 쓴다 — 화면이 보내는 값이라 별도로 더 열어 둘 이유가 없다. */
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
	 * @throws IllegalArgumentException 메시지가 비었거나 너무 길다 — 400
	 * @throws AssistantRateLimitExceededException 이 사용자가 1분 한도를 넘겼다 — 429
	 * @throws com.gabolle.backend.auth.service.AuthException {@code itineraryId} 를 보냈는데
	 *     {@code AI_ASSISTANT_ACCESS} 동의가 없다 — 403
	 * @throws com.gabolle.backend.itinerary.presentation.ItineraryQueryController.ItineraryNotFoundException
	 *     그 일정이 없거나 요청자가 회원이 아니다 — 404
	 * @throws AssistantVendorException 업체 호출 실패 — 502
	 */
	public AssistantReply chat(UUID userId, String message, String language, List<AssistantTurn> history,
			String itineraryId, Integer dayIndex) {
		if (message == null || message.isBlank()) {
			throw new IllegalArgumentException("message 는 비어 있을 수 없습니다.");
		}
		if (message.length() > MAX_MESSAGE_LENGTH) {
			throw new IllegalArgumentException("message 는 " + MAX_MESSAGE_LENGTH + "자를 넘을 수 없습니다.");
		}

		// 🔴 벤더를 부르기 전에 막는다 — 한도를 넘긴 요청이 무료 티어 호출을 쓰면 안 된다.
		this.rateLimiter.checkAndRecord(userId);

		String normalizedLanguage = normalizeLanguage(language);
		List<AssistantTurn> trimmedHistory = trimHistory(history);

		String tripContext = null;
		if (itineraryId != null && dayIndex != null) {
			this.consentGuard.requireAiAssistantAccess(userId);
			tripContext = this.tripContextBuilder.build(itineraryId, dayIndex, userId);
		}

		// 🔴 실패하면 여기서 던진 AssistantVendorException 이 그대로 위로 올라간다.
		return this.vendor.reply(
				new AssistantChatRequest(message, normalizedLanguage, trimmedHistory, tripContext));
	}

	/**
	 * 프론트가 5개 언어(ko/en/ja/zh-Hans/zh-Hant)를 지원한다 — {@code frontend/src/i18n/languages.ts}
	 * 의 {@code toBcp47()} 가 만드는 표기({@code ko-KR}·{@code en-US}·{@code ja-JP}·{@code zh-CN}·
	 * {@code zh-TW})를 Accept-Language 헤더로 그대로 보낸다.
	 *
	 * <p>🔴 <b>첫 언어 태그만</b> 본다 — {@code RequestLanguage.prefersEnglish} 와 같은 단순화다.
	 * 처음에는 헤더 전체 문자열에 {@code contains}를 써서, 우선순위가 낮은 뒤쪽 태그(예:
	 * {@code "zh-Hans,zh-Hant;q=0.5"} 의 {@code zh-Hant})가 앞쪽 태그의 판정을 덮어쓰는 결함이
	 * 있었다(MR !1066 AI 리뷰로 발견) — 그래서 콤마로 먼저 자른다.
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
