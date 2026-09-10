package com.gabolle.backend.assistant.adapter;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.gabolle.backend.assistant.adapter.ClaudeStructuredReply.ClaudeStructuredPlanPatch;
import com.gabolle.backend.assistant.application.AssistantVendorException;
import com.gabolle.backend.assistant.application.AssistantVendorPort;
import com.gabolle.backend.assistant.config.AssistantProperties;
import com.gabolle.backend.assistant.domain.AssistantActionKind;
import com.gabolle.backend.assistant.domain.AssistantReply;
import com.gabolle.backend.assistant.domain.PlanPatch;

/**
 * 실제 Claude 호출 — S15P21E201-802.
 *
 * <p>{@code TranslationVendorAdapter} 와 같은 자리다 — 키가 비어 있으면 호출을 시도하지 않고
 * 즉시 명확한 실패를 던지고({@code TRANSLATE_VENDOR_NOT_CONFIGURED} 와 같은 모양), 원문을
 * 로그에 남기지 않는다.
 *
 * <p>구조화 출력({@link ClaudeStructuredReply})을 응답 스키마로 강제해 파싱을 직접 하지
 * 않는다 — 모델이 잘못된 모양으로 답할 수가 없다({@code StructuredMessageCreateParams}).
 */
@Component
@Profile({ "db", "dev" })
public class ClaudeAssistantAdapter implements AssistantVendorPort {

	private static final Logger log = LoggerFactory.getLogger(ClaudeAssistantAdapter.class);

	static final String PROVIDER_NAME = "CLAUDE";

	private static final Set<String> ALLOWED_HREFS = Set.of("/field/translate", "/trips");

	private static final String SYSTEM_PROMPT = """
			너는 여행 앱 '가볼래'의 여행 일정 도우미다. 사용자의 한국어 메시지를 아래 네 가지 중
			정확히 하나로 분류해 답한다.

			- plan: 여행 일정/조건(지역·인원·날짜·예산·음식·분위기·이동수단)을 짜 달라거나 바꿔
			  달라는 요청. reply 에는 한국어로 무엇을 반영했는지 답하고, summary 에 반영한 항목을
			  짧은 문장으로 나열하고, patch 에 실제로 언급된 값만 채운다. 언급하지 않은 값은
			  patch 에서 null 로 둔다.
			- phrase: 특정 한국어 표현/문구를 물어보거나 번역을 원하는 요청. korean·pronunciation
			  을 채운다.
			- navigate: 이 앱의 다른 화면(현장 번역 또는 내 여행 목록)으로 보내 달라는 요청.
			  href 는 '/field/translate' 또는 '/trips' 중 하나만 쓴다.
			- help: 위 셋에 해당하지 않거나 애매한 요청. reply 에만 답한다.

			🔴 알레르기·접근성·휠체어·유모차·짐·식단 제약은 이 응답 형식에 그 값을 담을 자리가
			없다 — 언급이 있어도 무시하고 reply 로만 안내한다.
			""";

	private final AssistantProperties properties;

	public ClaudeAssistantAdapter(AssistantProperties properties) {
		this.properties = properties;
	}

	@Override
	public String providerName() {
		return PROVIDER_NAME;
	}

	@Override
	public AssistantReply reply(String message) {
		String apiKey = this.properties.getApiKey();
		if (apiKey == null || apiKey.isBlank()) {
			throw new AssistantVendorException("ASSISTANT_VENDOR_NOT_CONFIGURED",
					"AI 여행 도우미가 설정되지 않았습니다.", HttpStatus.BAD_GATEWAY);
		}

		AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();

		StructuredMessageCreateParams<ClaudeStructuredReply> params = MessageCreateParams.builder()
				.model(this.properties.getModel())
				.maxTokens(this.properties.getMaxTokens())
				.system(SYSTEM_PROMPT)
				.outputConfig(ClaudeStructuredReply.class)
				.addUserMessage(message)
				.build();

		ClaudeStructuredReply parsed;
		try {
			parsed = client.messages().create(params).content().stream()
					.flatMap(block -> block.text().stream())
					.map(structuredText -> structuredText.text())
					.findFirst()
					.orElse(null);
		}
		catch (RuntimeException exception) {
			// 🔴 원문을 찍지 않는다 — 실패했다는 사실만 남긴다.
			log.warn("AI 여행 도우미 호출 실패");
			throw new AssistantVendorException("ASSISTANT_VENDOR_UNAVAILABLE",
					"AI 여행 도우미 호출에 실패했습니다.", HttpStatus.BAD_GATEWAY, exception);
		}

		if (parsed == null || parsed.reply() == null || parsed.reply().isBlank()) {
			log.warn("AI 여행 도우미가 결과를 주지 않음");
			throw new AssistantVendorException("ASSISTANT_VENDOR_UNAVAILABLE",
					"AI 여행 도우미가 결과를 주지 않았습니다.", HttpStatus.BAD_GATEWAY);
		}

		return toDomain(parsed);
	}

	private AssistantReply toDomain(ClaudeStructuredReply parsed) {
		AssistantActionKind kind = parseKind(parsed.kind());

		String href = parsed.href();
		if (kind == AssistantActionKind.NAVIGATE && !ALLOWED_HREFS.contains(href)) {
			// 🔴 모델이 허용 목록 밖의 경로를 지어내면 안내만 하는 HELP 로 낮춘다 — 화면이 모르는
			// 경로로 이동을 시도하게 두지 않는다.
			return new AssistantReply(AssistantActionKind.HELP, parsed.reply(), List.of(), null, null, null, null,
					null);
		}

		return new AssistantReply(
				kind,
				parsed.reply(),
				kind == AssistantActionKind.PLAN && parsed.summary() != null ? parsed.summary() : List.of(),
				kind == AssistantActionKind.PLAN ? toDomainPatch(parsed.patch()) : null,
				kind == AssistantActionKind.PHRASE ? parsed.korean() : null,
				kind == AssistantActionKind.PHRASE ? parsed.pronunciation() : null,
				kind == AssistantActionKind.NAVIGATE ? parsed.label() : null,
				kind == AssistantActionKind.NAVIGATE ? href : null);
	}

	private PlanPatch toDomainPatch(ClaudeStructuredPlanPatch patch) {
		if (patch == null) {
			return new PlanPatch(null, null, null, null, null, null, null, null, null, null);
		}
		return new PlanPatch(
				patch.startDate(),
				patch.endDate(),
				patch.travelers(),
				patch.adults(),
				patch.children(),
				patch.travelAreas(),
				patch.foods(),
				patch.atmospheres(),
				patch.transport(),
				patch.budgetKrw());
	}

	private AssistantActionKind parseKind(String raw) {
		if (raw == null) {
			return AssistantActionKind.HELP;
		}
		try {
			return AssistantActionKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException exception) {
			// 🔴 모델이 모르는 kind 를 지어내면 HELP 로 낮춘다 — 화면이 모르는 kind 를 받고
			// 죽지 않게 한다.
			return AssistantActionKind.HELP;
		}
	}
}
