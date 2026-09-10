package com.gabolle.backend.assistant.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

	private static final Map<String, String> TRANSPORT_LABELS = Map.of(
			"TRANSIT", "대중교통",
			"WALK", "도보",
			"CAR", "자동차");

	private static final String SYSTEM_PROMPT = """
			너는 여행 앱 '가볼래'의 여행 일정 도우미다. 사용자의 한국어 메시지를 아래 네 가지 중
			정확히 하나로 분류해 답한다.

			- plan: 여행 일정/조건(지역·인원·날짜·예산·음식·분위기·이동수단)을 짜 달라거나 바꿔
			  달라는 요청. reply·summary 는 이 앱이 직접 조립하니 너는 patch 에 실제로 언급된
			  값만 채운다. 언급하지 않은 값은 patch 에서 null 로 둔다.
			- phrase: 특정 한국어 표현/문구를 물어보거나 번역을 원하는 요청. korean·pronunciation
			  을 채운다.
			- navigate: 이 앱의 다른 화면(현장 번역 또는 내 여행 목록)으로 보내 달라는 요청.
			  href 는 '/field/translate' 또는 '/trips' 중 하나만 쓴다.
			- help: 위 셋에 해당하지 않거나 애매한 요청. reply 에만 답한다.

			🔴 알레르기·접근성·휠체어·유모차·짐·식단 제약은 이 응답 형식에 그 값을 담을 자리가
			없다 — 언급이 있어도 무시하고 reply 로만 안내한다.

			🔴 어떤 kind 에서도 구체적인 가게·식당·관광지 이름을 답하지 않는다(예: "OO집",
			"OO해수욕장 근처 XX식당"). 이 앱이 보여주는 장소는 전부 실제 설문·현지인 추천으로
			검증된 데이터에서만 나온다 — 네가 학습한 일반 지식으로 특정 장소를 지어내거나
			추천하면 그 보증이 깨진다. 장소를 묻는 질문에는 지역·음식 종류 같은 조건만 확인하고,
			실제 목록은 앱 화면이 보여준다고 안내해라.
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

		if (kind == AssistantActionKind.PLAN) {
			PlanPatch patch = toDomainPatch(parsed.patch());
			List<String> summary = buildPlanSummary(patch);
			String reply = summary.isEmpty()
					? "말씀하신 내용에서 반영할 조건을 찾지 못했어요. 지역·인원·날짜·예산 중 하나라도 말씀해 주세요."
					: "말씀하신 조건으로 일정을 반영했어요.";
			return new AssistantReply(kind, reply, summary, patch, null, null, null, null);
		}

		return new AssistantReply(
				kind,
				parsed.reply(),
				List.of(),
				null,
				kind == AssistantActionKind.PHRASE ? parsed.korean() : null,
				kind == AssistantActionKind.PHRASE ? parsed.pronunciation() : null,
				kind == AssistantActionKind.NAVIGATE ? parsed.label() : null,
				kind == AssistantActionKind.NAVIGATE ? href : null);
	}

	/**
	 * {@code plan} 응답의 {@code reply}·{@code summary} 는 모델이 쓴 자유 텍스트를 그대로
	 * 내보내지 않는다 — {@code patch} 에 실제로 채워진 값만 가지고 이 메서드가 직접 조립한다.
	 *
	 * <p>🔴 프롬프트로 "장소 이름을 대지 마라"고 시키는 것은 확률적이다(모델이 지시를 놓칠 수
	 * 있다). 이 메서드는 애초에 모델이 쓴 문장을 화면에 보내지 않으므로, 특정 가게·식당 이름이
	 * 섞여 나갈 통로 자체가 없다 — 안전 필드를 타입으로 막은 것과 같은 종류의 보장이다.
	 */
	List<String> buildPlanSummary(PlanPatch patch) {
		List<String> summary = new ArrayList<>();
		if (patch.travelAreas() != null && !patch.travelAreas().isEmpty()) {
			summary.add("지역: " + String.join(", ", patch.travelAreas()));
		}
		if (patch.startDate() != null || patch.endDate() != null) {
			String start = patch.startDate() != null ? patch.startDate() : "미정";
			String end = patch.endDate() != null ? patch.endDate() : "미정";
			summary.add("기간: " + start + " ~ " + end);
		}
		if (patch.travelers() != null) {
			summary.add("인원: " + patch.travelers() + "명");
		}
		else if (patch.adults() != null || patch.children() != null) {
			int adults = patch.adults() != null ? patch.adults() : 0;
			int children = patch.children() != null ? patch.children() : 0;
			summary.add("인원: 성인 " + adults + "명, 아동 " + children + "명");
		}
		if (patch.foods() != null && !patch.foods().isEmpty()) {
			summary.add("음식: " + String.join(", ", patch.foods()));
		}
		if (patch.atmospheres() != null && !patch.atmospheres().isEmpty()) {
			summary.add("분위기: " + String.join(", ", patch.atmospheres()));
		}
		if (patch.transport() != null) {
			summary.add("이동수단: " + TRANSPORT_LABELS.getOrDefault(patch.transport(), patch.transport()));
		}
		if (patch.budgetKrw() != null) {
			summary.add("예산: " + patch.budgetKrw() + "원");
		}
		return summary;
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
