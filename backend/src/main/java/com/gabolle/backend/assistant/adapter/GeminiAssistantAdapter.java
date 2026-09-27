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
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
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
 * 실제 Google Gemini 호출. 키가 비어 있으면 호출을 시도하지 않고 즉시 명확한 실패를 던지며, 원문을
 * 로그에 남기지 않는다.
 *
 * <p>이 도우미는 안내만 한다 — 화면으로 가는 버튼을 만들어 주거나(navigate), 현장 문구를
 * 알려주거나(phrase), 짧게 안내한다(help). 실제 여행 일정·장소 추천은 기존 화면이 한다.
 *
 * <p>출력을 {@link #RESPONSE_SCHEMA} 로 강제해 모델이 잘못된 모양으로 답할 수 없게 한다. 이 SDK 는
 * POJO 를 만들어 주지 않아 응답 원문을 {@link GeminiStructuredReply} 로 수동 파싱한다.
 */
@Component
@Profile({ "db", "dev" })
public class GeminiAssistantAdapter implements AssistantVendorPort {

	private static final Logger log = LoggerFactory.getLogger(GeminiAssistantAdapter.class);

	static final String PROVIDER_NAME = "GEMINI";

	/**
	 * 비서가 안내해도 되는 화면 주소. 앱의 허용 목록
	 * (frontend/src/assistant/assistantApi.ts 의 {@code ALLOWED_NAVIGATE_HREFS})과 같아야 한다 —
	 * 앱은 모르는 주소가 오면 이동 버튼을 지우고 안내문만 남긴다.
	 *
	 * <p>{@code /plan/basic} 을 넣지 않는다 — 그쪽은 {@code /plan} 으로 보내는 리다이렉트인데,
	 * 리다이렉트는 쿼리를 안 실어 나르므로 {@code withPrefill} 이 붙인 값이 버려진다.
	 */
	static final Set<String> ALLOWED_HREFS = Set.of("/plan", "/trips", "/field/translate", "/field/transit",
			"/field/exchange-rate");

	/** '/plan' 으로 갈 때만 채운다 — days 는 1~30, people 은 1~20 을 벗어나면 버린다. */
	private static final int MIN_DAYS = 1;
	private static final int MAX_DAYS = 30;
	private static final int MIN_PEOPLE = 1;
	private static final int MAX_PEOPLE = 20;

	/**
	 * 앱 조건 화면의 지역 코드(frontend/src/plan/planOptions.ts 의 {@code AREA_OPTIONS})와 같다.
	 * 목록 밖 값은 쿼리에 싣지 않는다 — S15P21E201-1825.
	 */
	static final List<String> AREA_CODES = List.of("HAEUNDAE", "GWANGALLI", "NAMPO", "SEOMYEON", "YEONGDO",
			"SONGJEONG");

	/** 앱의 여행 카테고리 코드({@code CATEGORY_OPTIONS})와 같다. */
	static final List<String> CATEGORY_CODES = List.of("SEA_BEACH", "CITY", "CAFE_HEALING", "CULTURE_TEMPLE", "FOOD",
			"NATURE_WALK", "FESTIVAL_EVENT");

	private static final java.util.regex.Pattern ISO_DATE = java.util.regex.Pattern.compile("20[0-9]{2}-[0-9]{2}-[0-9]{2}");

	/**
	 * {@code propertyOrdering} 을 명시한다 — Gemini 구조화 출력은 필드를 이 순서대로 생성하는데,
	 * {@code properties} 를 {@code Map.of()} 로 주면 반복 순서가 보장되지 않는다. 순서가 흐트러지면
	 * 모델이 {@code days}·{@code people} 같은 뒤쪽 필드를 빠뜨린다.
	 */
	private static final Schema RESPONSE_SCHEMA = Schema.builder()
			.type(Type.Known.OBJECT)
			.properties(Map.ofEntries(
					Map.entry("kind", stringEnum("navigate", "phrase", "help")),
					Map.entry("reply", string()),
					Map.entry("korean", string()),
					Map.entry("pronunciation", string()),
					Map.entry("label", string()),
					Map.entry("href", stringEnum("/plan", "/trips", "/field/translate", "/field/transit",
							"/field/exchange-rate")),
					Map.entry("days", integer()),
					Map.entry("people", integer()),
					Map.entry("areas", stringArray(AREA_CODES)),
					Map.entry("categories", stringArray(CATEGORY_CODES)),
					Map.entry("startDate", string())))
			.propertyOrdering("kind", "reply", "korean", "pronunciation", "label", "href", "days", "people", "areas",
					"categories", "startDate")
			.required("kind", "reply")
			.build();

	private static Schema stringArray(List<String> values) {
		return Schema.builder().type(Type.Known.ARRAY).items(stringEnum(values.toArray(String[]::new))).build();
	}

	private static Schema string() {
		return Schema.builder().type(Type.Known.STRING).build();
	}

	private static Schema integer() {
		return Schema.builder().type(Type.Known.INTEGER).build();
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
			  여행을 보고 싶다, 현장에서 쓸 번역이 필요하다, 버스가 언제 오는지 보고 싶다,
			  환율이 궁금하다 등). href 는 아래 다섯 중 하나만 쓴다.
			    '/plan'        — 새 여행 만들기(일정·조건 입력 시작)
			    '/trips'             — 내 여행 목록
			    '/field/translate'   — 현장 번역
			    '/field/transit'     — 근처 버스 정류소·실시간 도착정보
			    '/field/exchange-rate' — 오늘의 환율
			  label 에는 그 화면으로 가는 짧은 한국어 버튼 문구를 채운다(예: "여행 만들기").

			  🔴 버스·지하철 도착시간을 묻는 요청은 항상 navigate('/field/transit')로만
			  안내한다 — 네가 실시간 도착시간을 알거나 계산할 방법은 없다. "3번 버스 5분 뒤
			  도착"처럼 지어내서 답하지 않는다. 지하철은 이 화면이 아직 실시간 정보를 못 주니
			  (버스만 가능), 지하철을 구체적으로 물으면 help 로 "버스 실시간 정보만 아직
			  가능해요" 처럼 안내한다.

			  🔴 환율을 묻는 요청도 항상 navigate('/field/exchange-rate')로만 안내한다 —
			  "1달러에 1,350원이에요" 처럼 구체적인 환율 숫자를 네가 직접 답하지 않는다.
			  네가 아는 환율은 오래된 값일 수 있고, 실제 환율은 매 영업일 바뀐다.

			  🔴 href 가 '/plan' 일 때 반드시 확인한다 — 사용자 메시지에 여행 일수나
			  인원 숫자가 나와 있으면 반드시 days·people 을 채워야 한다(빠뜨리지 않는다). 아래
			  입력→출력 예시와 정확히 같은 방식으로 채운다.

			    입력: "부산 2박3일로 4명이서 여행 갈건데 만들어줘"
			    출력: {"days": 3, "people": 4}
			    (2박3일은 3일짜리 여행이다 — 밤을 잔 횟수가 아니라 날짜 수를 센다)

			    입력: "1박2일로 여행 만들어줘"
			    출력: {"days": 2}
			    (인원 언급이 없으므로 people 은 채우지 않는다 — 숫자 2 를 people 에 넣지 않는다)

			    입력: "당일치기로 혼자 여행 만들어줘"
			    출력: {"days": 1, "people": 1}

			    입력: "여행 만들고 싶어"
			    출력: {} (일수·인원 언급이 전혀 없으므로 둘 다 비운다)

			  🔴 지역·취향·출발일도 같은 방식으로 채운다(메시지에 나온 것만).
			    areas — 해운대 HAEUNDAE · 광안리 GWANGALLI · 남포(남포동·자갈치·국제시장) NAMPO ·
			            서면 SEOMYEON · 영도 YEONGDO · 송정 SONGJEONG. 영어 이름(Haeundae 등)도 같다.
			    categories — 맛집·먹거리 FOOD · 카페 CAFE_HEALING · 바다·해변 SEA_BEACH ·
			            자연·산책 NATURE_WALK · 문화·사찰 CULTURE_TEMPLE · 도심·야경·시장 CITY ·
			            축제 FESTIVAL_EVENT.
			    startDate — 사용자가 연·월·일을 분명히 말했을 때만 YYYY-MM-DD. "내일"처럼 모호하면 비운다.

			    입력: "광안리 맛집 위주로 2명 일정 짜줘"
			    출력: {"href": "/plan", "people": 2, "areas": ["GWANGALLI"], "categories": ["FOOD"]}

			    입력: "Rainy day places near Haeundae"
			    출력: {"href": "/plan", "areas": ["HAEUNDAE"]}

			  메시지에 없는 값은 절대 추측하지 않는다. 특히 사람 수 언급이 없으면 people 은
			  반드시 비운다 — 일수 숫자를 people 자리에 넣는 실수를 하지 않는다.
			- phrase: 특정 한국어 표현/문구를 물어보거나 번역을 원하는 요청. korean·pronunciation
			  을 채운다. 사전적인 단어 하나만이 아니라, 여행 중 실제로 부딪히는 상황도 여기에
			  해당한다 — 예를 들면:
			    - 택시·버스 기사에게 목적지·경로 말하기
			    - 알레르기·못 먹는 음식 있다고 알리기 (예: "저는 땅콩 알레르기가 있어요")
			    - 길을 잃었을 때 도움 요청하기
			    - 식당·가게에서 환불·교환 요청하기
			    - 아프거나 다쳤을 때 도움 요청하기, 약국 찾기
			  이런 상황을 물으면 그 상황에서 실제로 쓸 수 있는 자연스러운 한국어 문장 하나를
			  korean 에 채운다 — 단어 하나가 아니라 완결된 문장으로 답한다. 응급 상황이라도
			  의학적 판단(무슨 약을 먹어라 등)은 하지 않는다 — 도움을 요청하는 말만 알려준다.
			- help: 위 둘에 해당하지 않거나 애매한 요청, 또는 이 앱이 못 하는 것을 물었을 때.
			  reply 에만 답한다.

			🔴 어떤 kind 에서도 구체적인 가게·식당·관광지 이름을 <b>지어내지</b> 않는다(예: "OO집",
			"OO해수욕장 근처 XX식당"). 이 앱이 보여주는 장소는 전부 실제 설문·현지인 추천으로
			검증된 데이터에서만 나온다 — 네가 학습한 일반 지식으로 새 장소를 지어내거나 추천하면
			그 보증이 깨진다. 장소·일정 관련 요청은 원칙적으로 navigate('/plan' 또는
			'/trips')로 안내하고, 알레르기·접근성 같은 안전 조건도 이 앱 화면에서 직접 입력받으니
			네가 대신 판단하지 않는다.

			🔴 예외 — 아래에 "[실제 일정]" 데이터가 함께 주어지면, 그건 지어낸 것이 아니라
			사용자가 이미 만들어 둔 진짜 일정이다. 그 목록 안에 있는 장소·시각을 근거로 조언(예:
			"이 시간대엔 뭘 챙겨야 해?", "다음 장소까지 얼마나 걸어?")하는 것은 허용한다 —
			help 로 답한다. 단 그 목록에 없는 새 장소를 추가로 추천하지는 않는다 — 그건 여전히
			navigate 로 안내한다.

			🔴 이 앱에 대한 사실 — "이 앱 뭐야?" 같은 질문에는 아래 사실만 근거로 답한다. 여기
			없는 기능을 지어내서 답하지 않는다.
			- 이름은 '가볼래'다. 부산 여행에 특화된 여행 서비스다.
			- 가장 큰 특징: 장소·코스 추천이 AI 가 지어낸 것이 아니라, 실제 설문과 부산 현지인
			  추천 데이터를 바탕으로 한다.
			- 이 챗봇이 안내할 수 있는 기능은 여행 일정 만들기, 내 여행 관리, 현장 번역, 근처
			  버스 실시간 도착정보, 오늘의 환율 다섯 가지다.

			🔴 이 앱·여행과 무관한 요청(날씨·시사·다른 서비스·코딩·일반 상식·잡담 등)은 help 로
			답하되, 아는 척 답하지 않는다 — "저는 가볼래 여행 관련해서만 도와드릴 수 있어요" 처럼
			정중히 범위를 안내하고, 이 챗봇이 실제로 할 수 있는 것으로 돌아오게 유도한다.
			""";

	private static final String ENGLISH_DIRECTIVE = """


			🔴 답변 언어 — 이번 요청은 영어 사용자다. reply · label · korean(원문은 한국어 그대로
			두되 설명은 영어로) · pronunciation 등 사용자에게 보여줄 모든 텍스트를 영어로 써라.
			kind 값과 href 값 자체는 위에서 정한 그대로(navigate/phrase/help,
			'/plan' 등)를 그대로 쓴다 — 번역하지 않는다.
			""";

	private static final String JAPANESE_DIRECTIVE = """


			🔴 답변 언어 — 이번 요청은 일본어 사용자다. reply · label · korean(원문은 한국어 그대로
			두되 설명은 일본어로) · pronunciation 등 사용자에게 보여줄 모든 텍스트를 일본어로 써라.
			kind 값과 href 값 자체는 위에서 정한 그대로(navigate/phrase/help,
			'/plan' 등)를 그대로 쓴다 — 번역하지 않는다.
			""";

	private static final String CHINESE_SIMPLIFIED_DIRECTIVE = """


			🔴 답변 언어 — 이번 요청은 중국어(간체) 사용자다. reply · label · korean(원문은 한국어
			그대로 두되 설명은 간체자로) · pronunciation 등 사용자에게 보여줄 모든 텍스트를 간체자로
			써라. kind 값과 href 값 자체는 위에서 정한 그대로(navigate/phrase/help,
			'/plan' 등)를 그대로 쓴다 — 번역하지 않는다.
			""";

	private static final String CHINESE_TRADITIONAL_DIRECTIVE = """


			🔴 답변 언어 — 이번 요청은 중국어(번체) 사용자다. reply · label · korean(원문은 한국어
			그대로 두되 설명은 번체자로) · pronunciation 등 사용자에게 보여줄 모든 텍스트를 번체자로
			써라. kind 값과 href 값 자체는 위에서 정한 그대로(navigate/phrase/help,
			'/plan' 등)를 그대로 쓴다 — 번역하지 않는다.
			""";

	private final AssistantProperties properties;

	private final ObjectMapper objectMapper;

	public GeminiAssistantAdapter(AssistantProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	/**
	 * 주소와 시간 제한을 기본값에 맡기지 않는다. 구글을 직접 부르면 개인 키의 무료 한도가 좁아 같은
	 * 질문을 연달아 보낼 때 대부분 429 가 되고, 시간 제한이 없으면 30초 넘게 요청 스레드를 붙잡는다 —
	 * 앱은 12초에 이미 끊으므로 그 뒤는 아무도 안 기다리는 시간이다.
	 *
	 * <p>{@code baseUrl} 이 비어 있으면 안 건다. 시간 제한은 주소와 무관하게 언제나 건다.
	 */
	HttpOptions httpOptions() {
		HttpOptions.Builder builder = HttpOptions.builder()
				.timeout((int) this.properties.getTimeout().toMillis())
				// 🔴 재시도는 끈다(한 번만 부른다). SDK 는 retryOptions 가 비어 있으면 기본값으로 **5번까지**
				//    다시 부르고 사이사이 1·2·4·8초를 쉰다 — 시간 제한은 한 번에만 걸려서, 느린 날 요청 하나가
				//    30초를 붙잡았다. 앱은 12초에 이미 끊고 엉뚱한 기본 안내를 보였다(S15P21E201-1749).
				.retryOptions(HttpRetryOptions.builder().attempts(1).build());
		String baseUrl = this.properties.getBaseUrl();
		if (baseUrl != null && !baseUrl.isBlank()) {
			builder.baseUrl(baseUrl);
		}
		return builder.build();
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

		Client client = Client.builder().apiKey(apiKey).httpOptions(httpOptions()).build();

		String systemPrompt = SYSTEM_PROMPT + languageDirective(request.language());
		if (request.tripContext() != null && !request.tripContext().isBlank()) {
			systemPrompt = systemPrompt + "\n\n[실제 일정]\n" + request.tripContext();
		}
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
			// 원문을 찍지 않는다 — 실패했다는 사실만 남긴다.
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

	/** {@code AssistantChatService.normalizeLanguage} 가 만든 다섯 값 중 하나를 받는다. */
	private String languageDirective(String language) {
		if (language == null) {
			return "";
		}
		return switch (language) {
			case "en" -> ENGLISH_DIRECTIVE;
			case "ja" -> JAPANESE_DIRECTIVE;
			case "zh-Hans" -> CHINESE_SIMPLIFIED_DIRECTIVE;
			case "zh-Hant" -> CHINESE_TRADITIONAL_DIRECTIVE;
			default -> "";
		};
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
			// 모델이 허용 목록 밖의 경로를 지어내면 안내만 하는 HELP 로 낮춘다 — 화면이 모르는
			// 경로로 이동을 시도하게 두지 않는다.
			return new AssistantReply(AssistantActionKind.HELP, parsed.reply(), null, null, null, null);
		}

		return new AssistantReply(
				kind,
				parsed.reply(),
				kind == AssistantActionKind.PHRASE ? parsed.korean() : null,
				kind == AssistantActionKind.PHRASE ? parsed.pronunciation() : null,
				kind == AssistantActionKind.NAVIGATE ? label(parsed) : null,
				kind == AssistantActionKind.NAVIGATE ? withPrefill(parsed.href(), parsed) : null);
	}

	private static final Map<String, String> DEFAULT_LABELS = Map.of(
			"/plan", "여행 만들기",
			"/trips", "내 여행 보기",
			"/field/translate", "번역 열기",
			"/field/transit", "버스 도착정보 보기",
			"/field/exchange-rate", "환율 보기");

	/**
	 * label 은 스키마에서 required 가 아니라 모델이 이따금 비워서 준다. 버튼은 비워 둘 수 없는
	 * 자리라 href 별 기본 문구로 채운다.
	 */
	private String label(GeminiStructuredReply parsed) {
		if (parsed.label() != null && !parsed.label().isBlank()) {
			return parsed.label();
		}
		return DEFAULT_LABELS.get(parsed.href());
	}

	/**
	 * '/plan' 으로 갈 때, 대화에서 뽑아낸 days·people 을 쿼리 파라미터로 실어 보낸다 —
	 * 화면이 그 값으로 폼을 미리 채울 수 있게. 모델이 범위 밖 숫자를 지어내면(음수, 너무 큰 값
	 * 등) 그 파라미터만 조용히 뺀다 — 전체 응답을 실패시킬 이유는 아니다.
	 */
	private String withPrefill(String href, GeminiStructuredReply parsed) {
		if (!"/plan".equals(href)) {
			return href;
		}
		StringBuilder query = new StringBuilder();
		appendIfInRange(query, "days", parsed.days(), MIN_DAYS, MAX_DAYS);
		appendIfInRange(query, "people", parsed.people(), MIN_PEOPLE, MAX_PEOPLE);
		appendCodes(query, "areas", parsed.areas(), AREA_CODES);
		appendCodes(query, "categories", parsed.categories(), CATEGORY_CODES);
		if (parsed.startDate() != null && ISO_DATE.matcher(parsed.startDate().trim()).matches()) {
			append(query, "start", parsed.startDate().trim());
		}
		return query.isEmpty() ? href : href + "?" + query;
	}

	/** 허용 목록 안의 코드만, 중복 없이 쉼표로 잇는다. 하나도 안 남으면 파라미터를 뺀다. */
	private void appendCodes(StringBuilder query, String key, List<String> values, List<String> allowed) {
		if (values == null) {
			return;
		}
		List<String> kept = values.stream()
				.filter(java.util.Objects::nonNull)
				.map(value -> value.trim().toUpperCase(Locale.ROOT))
				.filter(allowed::contains)
				.distinct()
				.toList();
		if (!kept.isEmpty()) {
			append(query, key, String.join(",", kept));
		}
	}

	private void append(StringBuilder query, String key, String value) {
		if (!query.isEmpty()) {
			query.append("&");
		}
		query.append(key).append("=").append(value);
	}

	private void appendIfInRange(StringBuilder query, String key, Integer value, int min, int max) {
		if (value == null || value < min || value > max) {
			return;
		}
		if (!query.isEmpty()) {
			query.append("&");
		}
		query.append(key).append("=").append(value);
	}

	AssistantActionKind parseKind(String raw) {
		if (raw == null) {
			return AssistantActionKind.HELP;
		}
		try {
			return AssistantActionKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException exception) {
			// 모델이 모르는 kind 를 지어내면 HELP 로 낮춘다 — 화면이 모르는 kind 를 받고
			// 죽지 않게 한다.
			return AssistantActionKind.HELP;
		}
	}
}
