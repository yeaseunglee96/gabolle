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

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.gabolle.backend.assistant.application.AssistantVendorException;
import com.gabolle.backend.assistant.application.AssistantVendorPort;
import com.gabolle.backend.assistant.config.AssistantProperties;
import com.gabolle.backend.assistant.domain.AssistantActionKind;
import com.gabolle.backend.assistant.domain.AssistantChatRequest;
import com.gabolle.backend.assistant.domain.AssistantReply;
import com.gabolle.backend.assistant.domain.AssistantTurn;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 실제 Google Gemini 호출 — S15P21E201-802.
 *
 * <p>{@code TranslationVendorAdapter} 와 같은 자리다 — 키가 비어 있으면 호출을 시도하지 않고
 * 즉시 명확한 실패를 던지고({@code ASSISTANT_VENDOR_NOT_CONFIGURED} 와 같은 모양), 원문을
 * 로그에 남기지 않는다.
 *
 * <h2>🔴 MVP 범위 — 일정을 대신 짜지 않는다</h2>
 * 이 도우미는 은행 앱 챗봇처럼 <b>안내</b>만 한다 — 사용자의 말을 알아듣고 이 앱의 관련
 * 화면으로 가는 버튼을 만들어 주거나(navigate), 현장 문구를 알려주거나(phrase), 그 외에는
 * 짧게 안내한다(help). 실제 여행 일정·장소 추천은 이 앱의 기존 화면(설문·현지인 추천 데이터
 * 기반)이 한다 — AI 가 일정 조건을 만들어 대신 채우지 않는다.
 *
 * <p>구조화 출력을 {@link #RESPONSE_SCHEMA}(JSON 스키마)로 강제해 파싱을 어렵게 만들지
 * 않는다 — 모델이 잘못된 모양으로 답할 수가 없다. Claude 의 {@code StructuredMessageCreateParams}
 * 와 달리 이 SDK 는 POJO 를 직접 만들어 주지 않아서, 응답 원문(JSON 문자열)을
 * {@link GeminiStructuredReply} 로 수동 파싱한다.
 */
@Component
@Profile({ "db", "dev" })
public class GeminiAssistantAdapter implements AssistantVendorPort {

	private static final Logger log = LoggerFactory.getLogger(GeminiAssistantAdapter.class);

	static final String PROVIDER_NAME = "GEMINI";

	static final Set<String> ALLOWED_HREFS = Set.of("/plan/basic", "/trips", "/field/translate");

	private static final Schema RESPONSE_SCHEMA = Schema.builder()
			.type(Type.Known.OBJECT)
			.properties(Map.of(
					"kind", stringEnum("navigate", "phrase", "help"),
					"reply", string(),
					"korean", string(),
					"pronunciation", string(),
					"label", string(),
					"href", stringEnum("/plan/basic", "/trips", "/field/translate")))
			.required("kind", "reply")
			.build();

	private static Schema string() {
		return Schema.builder().type(Type.Known.STRING).build();
	}

	private static Schema stringEnum(String... values) {
		return Schema.builder().type(Type.Known.STRING).enum_(values).build();
	}

	private static final String SYSTEM_PROMPT = """
			너는 여행 앱 '가볼래'의 안내 도우미다. 은행 앱 챗봇처럼, 사용자의 말을 알아듣고
			이 앱의 관련 기능으로 가는 버튼을 만들어 주는 것이 네 역할이다 — 일정을 직접 짜거나
			장소를 추천하지 않는다. 사용자의 한국어 메시지를 아래 세 가지 중 정확히 하나로
			분류해 답한다.

			- navigate: 이 앱의 다른 화면으로 보내 달라는 요청(여행/일정을 만들고 싶다, 내
			  여행을 보고 싶다, 현장에서 쓸 번역이 필요하다 등). href 는 아래 셋 중 하나만 쓴다.
			    '/plan/basic'      — 새 여행 만들기(일정·조건 입력 시작)
			    '/trips'           — 내 여행 목록
			    '/field/translate' — 현장 번역
			  label 에는 그 화면으로 가는 짧은 한국어 버튼 문구를 채운다(예: "여행 만들기").
			- phrase: 특정 한국어 표현/문구를 물어보거나 번역을 원하는 요청. korean·pronunciation
			  을 채운다.
			- help: 위 둘에 해당하지 않거나 애매한 요청, 또는 이 앱이 못 하는 것을 물었을 때.
			  reply 에만 답한다.

			🔴 어떤 kind 에서도 구체적인 가게·식당·관광지 이름을 답하지 않는다(예: "OO집",
			"OO해수욕장 근처 XX식당"). 이 앱이 보여주는 장소는 전부 실제 설문·현지인 추천으로
			검증된 데이터에서만 나온다 — 네가 학습한 일반 지식으로 특정 장소를 지어내거나
			추천하면 그 보증이 깨진다. 장소·일정 관련 요청은 항상 navigate('/plan/basic' 또는
			'/trips')로 안내하고, 알레르기·접근성 같은 안전 조건도 이 앱 화면에서 직접 입력받으니
			네가 대신 판단하지 않는다.

			🔴 이 앱에 대한 사실 — "이 앱 뭐야?" 같은 질문에는 아래 사실만 근거로 답한다. 여기
			없는 기능을 지어내서 답하지 않는다.
			- 이름은 '가볼래'다. 부산 여행에 특화된 여행 서비스다.
			- 가장 큰 특징: 장소·코스 추천이 AI 가 지어낸 것이 아니라, 실제 설문과 부산 현지인
			  추천 데이터를 바탕으로 한다.
			- 이 챗봇이 안내할 수 있는 기능은 여행 일정 만들기, 내 여행 관리, 현장 번역 세 가지다.

			🔴 이 앱·여행과 무관한 요청(날씨·시사·다른 서비스·코딩·일반 상식·잡담 등)은 help 로
			답하되, 아는 척 답하지 않는다 — "저는 가볼래 여행 관련해서만 도와드릴 수 있어요" 처럼
			정중히 범위를 안내하고, 이 챗봇이 실제로 할 수 있는 것으로 돌아오게 유도한다.
			""";

	private static final String ENGLISH_DIRECTIVE = """


			🔴 답변 언어 — 이번 요청은 영어 사용자다. reply · label · korean(원문은 한국어 그대로
			두되 설명은 영어로) · pronunciation 등 사용자에게 보여줄 모든 텍스트를 영어로 써라.
			kind 값과 href 값 자체는 위에서 정한 그대로(navigate/phrase/help,
			'/plan/basic' 등)를 그대로 쓴다 — 번역하지 않는다.
			""";

	private final AssistantProperties properties;

	private final ObjectMapper objectMapper;

	public GeminiAssistantAdapter(AssistantProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	@Override
	public String providerName() {
		return PROVIDER_NAME;
	}

	@Override
	public AssistantReply reply(AssistantChatRequest request) {
		String apiKey = this.properties.getApiKey();
		if (apiKey == null || apiKey.isBlank()) {
			throw new AssistantVendorException("ASSISTANT_VENDOR_NOT_CONFIGURED",
					"AI 여행 도우미가 설정되지 않았습니다.", HttpStatus.BAD_GATEWAY);
		}

		Client client = Client.builder().apiKey(apiKey).build();

		String systemPrompt = "en".equals(request.language()) ? SYSTEM_PROMPT + ENGLISH_DIRECTIVE : SYSTEM_PROMPT;
		Content systemInstruction = Content.fromParts(Part.fromText(systemPrompt));
		GenerateContentConfig config = GenerateContentConfig.builder()
				.systemInstruction(systemInstruction)
				.responseMimeType("application/json")
				.responseSchema(RESPONSE_SCHEMA)
				.maxOutputTokens((int) this.properties.getMaxTokens())
				.build();

		List<Content> conversation = toConversation(request);

		String rawJson;
		try {
			GenerateContentResponse response = client.models.generateContent(this.properties.getModel(), conversation,
					config);
			rawJson = response.text();
		}
		catch (RuntimeException exception) {
			// 🔴 원문을 찍지 않는다 — 실패했다는 사실만 남긴다.
			log.warn("AI 여행 도우미 호출 실패");
			throw new AssistantVendorException("ASSISTANT_VENDOR_UNAVAILABLE",
					"AI 여행 도우미 호출에 실패했습니다.", HttpStatus.BAD_GATEWAY, exception);
		}

		if (rawJson == null || rawJson.isBlank()) {
			log.warn("AI 여행 도우미가 결과를 주지 않음");
			throw new AssistantVendorException("ASSISTANT_VENDOR_UNAVAILABLE",
					"AI 여행 도우미가 결과를 주지 않았습니다.", HttpStatus.BAD_GATEWAY);
		}

		GeminiStructuredReply parsed;
		try {
			parsed = this.objectMapper.readValue(rawJson, GeminiStructuredReply.class);
		}
		catch (JacksonException exception) {
			log.warn("AI 여행 도우미 응답을 해석하지 못함");
			throw new AssistantVendorException("ASSISTANT_VENDOR_UNAVAILABLE",
					"AI 여행 도우미 응답을 해석하지 못했습니다.", HttpStatus.BAD_GATEWAY, exception);
		}

		if (parsed.reply() == null || parsed.reply().isBlank()) {
			log.warn("AI 여행 도우미가 결과를 주지 않음");
			throw new AssistantVendorException("ASSISTANT_VENDOR_UNAVAILABLE",
					"AI 여행 도우미가 결과를 주지 않았습니다.", HttpStatus.BAD_GATEWAY);
		}

		return toDomain(parsed);
	}

	/**
	 * 이전 대화({@code request.history()}) 뒤에 이번 메시지를 이어 붙인다 — 이래야 모델이
	 * "그거 말고 다른 데는?" 같은 이어지는 말을 알아듣는다. {@code AssistantChatService} 가
	 * 이미 개수·길이를 다듬어 둔 값이라 여기서는 벤더 API 모양(역할 있는 {@link Content} 목록)
	 * 으로 옮기기만 한다.
	 */
	private List<Content> toConversation(AssistantChatRequest request) {
		List<Content> conversation = new ArrayList<>();
		for (AssistantTurn turn : request.history()) {
			String role = "assistant".equals(turn.role()) ? "model" : "user";
			conversation.add(Content.builder().role(role).parts(Part.fromText(turn.text())).build());
		}
		conversation.add(Content.builder().role("user").parts(Part.fromText(request.message())).build());
		return conversation;
	}

	AssistantReply toDomain(GeminiStructuredReply parsed) {
		AssistantActionKind kind = parseKind(parsed.kind());

		if (kind == AssistantActionKind.NAVIGATE && !ALLOWED_HREFS.contains(parsed.href())) {
			// 🔴 모델이 허용 목록 밖의 경로를 지어내면 안내만 하는 HELP 로 낮춘다 — 화면이 모르는
			// 경로로 이동을 시도하게 두지 않는다.
			return new AssistantReply(AssistantActionKind.HELP, parsed.reply(), null, null, null, null);
		}

		return new AssistantReply(
				kind,
				parsed.reply(),
				kind == AssistantActionKind.PHRASE ? parsed.korean() : null,
				kind == AssistantActionKind.PHRASE ? parsed.pronunciation() : null,
				kind == AssistantActionKind.NAVIGATE ? parsed.label() : null,
				kind == AssistantActionKind.NAVIGATE ? parsed.href() : null);
	}

	AssistantActionKind parseKind(String raw) {
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
