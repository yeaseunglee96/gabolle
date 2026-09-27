package com.gabolle.backend.assistant.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.assistant.config.AssistantProperties;
import com.gabolle.backend.assistant.domain.AssistantActionKind;
import com.gabolle.backend.assistant.domain.AssistantReply;

/**
 * {@link GeminiAssistantAdapter#toDomain}·{@code parseKind} 검증. 실제 Gemini 호출 없이 구조화 출력
 * 모양을 직접 만들어 변환만 잰다 — 모델이 허용 목록 밖의 href 나 모르는 kind 를 지어냈을 때 HELP 로
 * 낮추는지가 핵심이다.
 */
class GeminiAssistantAdapterTest {

	private final GeminiAssistantAdapter adapter = new GeminiAssistantAdapter(new AssistantProperties(), null);

	@Test
	@DisplayName("navigate + 허용된 href 는 그대로 통과한다")
	void navigateWithAllowedHrefPassesThrough() {
		GeminiStructuredReply parsed = new GeminiStructuredReply("navigate", "여행 만들기로 안내할게요.", null, null,
				"여행 만들기", "/trips", null, null);

		AssistantReply reply = this.adapter.toDomain(parsed);

		assertThat(reply.kind()).isEqualTo(AssistantActionKind.NAVIGATE);
		assertThat(reply.href()).isEqualTo("/trips");
		assertThat(reply.label()).isEqualTo("여행 만들기");
	}

	@Test
	@DisplayName("🔴 navigate 인데 href 가 허용 목록 밖이면 HELP 로 낮춘다")
	void navigateWithDisallowedHrefIsDowngradedToHelp() {
		GeminiStructuredReply parsed = new GeminiStructuredReply("navigate", "안내할게요.", null, null, "아무 데나",
				"/admin/secret", null, null);

		AssistantReply reply = this.adapter.toDomain(parsed);

		assertThat(reply.kind()).isEqualTo(AssistantActionKind.HELP);
		assertThat(reply.href()).isNull();
		assertThat(reply.label()).isNull();
	}

	@Test
	@DisplayName("phrase 는 korean·pronunciation 만 채워진다")
	void phraseFillsOnlyKoreanAndPronunciation() {
		GeminiStructuredReply parsed = new GeminiStructuredReply("phrase", "현장에서 쓰세요.", "화장실이 어디예요?",
				"hwajangsiri eodiyeyo?", null, null, null, null);

		AssistantReply reply = this.adapter.toDomain(parsed);

		assertThat(reply.kind()).isEqualTo(AssistantActionKind.PHRASE);
		assertThat(reply.korean()).isEqualTo("화장실이 어디예요?");
		assertThat(reply.href()).isNull();
	}

	// ── /plan 사전 채우기 ──────────────────────────────────────────

	@Test
	@DisplayName("/plan 이고 days·people 이 있으면 쿼리 파라미터로 실어 보낸다")
	void planBasicWithDaysAndPeopleGetsQueryParams() {
		GeminiStructuredReply parsed = new GeminiStructuredReply("navigate", "여행 만들기로 안내할게요.", null, null,
				"여행 만들기", "/plan", 2, 3);

		AssistantReply reply = this.adapter.toDomain(parsed);

		assertThat(reply.href()).isEqualTo("/plan?days=2&people=3");
	}

	@Test
	@DisplayName("days·people 이 없으면 물음표 없이 href 그대로다")
	void planBasicWithoutDaysOrPeopleHasNoQueryString() {
		GeminiStructuredReply parsed = new GeminiStructuredReply("navigate", "여행 만들기로 안내할게요.", null, null,
				"여행 만들기", "/plan", null, null);

		AssistantReply reply = this.adapter.toDomain(parsed);

		assertThat(reply.href()).isEqualTo("/plan");
	}

	@Test
	@DisplayName("🔴 범위 밖 숫자를 지어내면 그 파라미터만 조용히 뺀다")
	void outOfRangeValuesAreDroppedIndividually() {
		GeminiStructuredReply parsed = new GeminiStructuredReply("navigate", "여행 만들기로 안내할게요.", null, null,
				"여행 만들기", "/plan", 999, 3);

		AssistantReply reply = this.adapter.toDomain(parsed);

		assertThat(reply.href()).isEqualTo("/plan?people=3");
	}

	@Test
	@DisplayName("/trips·/field/translate 는 days·people 이 있어도 무시한다")
	void nonPlanBasicHrefIgnoresPrefillParams() {
		GeminiStructuredReply parsed = new GeminiStructuredReply("navigate", "내 여행으로 안내할게요.", null, null,
				"내 여행", "/trips", 2, 3);

		AssistantReply reply = this.adapter.toDomain(parsed);

		assertThat(reply.href()).isEqualTo("/trips");
	}

	@Test
	@DisplayName("🔴 모델이 label 을 비우면 href 별 기본 문구로 채운다")
	void blankLabelFallsBackToDefaultPerHref() {
		GeminiStructuredReply parsed = new GeminiStructuredReply("navigate", "안내해 드릴게요.", null, null, null,
				"/plan", 3, 4);

		AssistantReply reply = this.adapter.toDomain(parsed);

		assertThat(reply.label()).isEqualTo("여행 만들기");
	}

	// ── 대중교통 ─────────────────────────────────────────────────────────

	@Test
	@DisplayName("navigate + '/field/transit' 는 허용된 href 로 통과한다")
	void navigateToTransitFieldIsAllowed() {
		GeminiStructuredReply parsed = new GeminiStructuredReply("navigate", "근처 버스 도착정보로 안내할게요.", null, null,
				"버스 도착정보 보기", "/field/transit", null, null);

		AssistantReply reply = this.adapter.toDomain(parsed);

		assertThat(reply.kind()).isEqualTo(AssistantActionKind.NAVIGATE);
		assertThat(reply.href()).isEqualTo("/field/transit");
	}

	@Test
	@DisplayName("🔴 모델이 label 을 비우면 '/field/transit' 도 기본 문구로 채운다")
	void blankLabelFallsBackToDefaultForTransit() {
		GeminiStructuredReply parsed = new GeminiStructuredReply("navigate", "안내해 드릴게요.", null, null, null,
				"/field/transit", null, null);

		AssistantReply reply = this.adapter.toDomain(parsed);

		assertThat(reply.label()).isEqualTo("버스 도착정보 보기");
	}

	// ── 환율 ─────────────────────────────────────────────────────────────

	@Test
	@DisplayName("navigate + '/field/exchange-rate' 는 허용된 href 로 통과한다")
	void navigateToExchangeRateFieldIsAllowed() {
		GeminiStructuredReply parsed = new GeminiStructuredReply("navigate", "오늘의 환율로 안내할게요.", null, null,
				"환율 보기", "/field/exchange-rate", null, null);

		AssistantReply reply = this.adapter.toDomain(parsed);

		assertThat(reply.kind()).isEqualTo(AssistantActionKind.NAVIGATE);
		assertThat(reply.href()).isEqualTo("/field/exchange-rate");
	}

	@Test
	@DisplayName("🔴 모델이 label 을 비우면 '/field/exchange-rate' 도 기본 문구로 채운다")
	void blankLabelFallsBackToDefaultForExchangeRate() {
		GeminiStructuredReply parsed = new GeminiStructuredReply("navigate", "안내해 드릴게요.", null, null, null,
				"/field/exchange-rate", null, null);

		AssistantReply reply = this.adapter.toDomain(parsed);

		assertThat(reply.label()).isEqualTo("환율 보기");
	}

	@Test
	@DisplayName("🔴 모델이 모르는 kind 를 지어내면 HELP 로 낮춘다")
	void unknownKindFallsBackToHelp() {
		assertThat(this.adapter.parseKind("plan")).isEqualTo(AssistantActionKind.HELP);
		assertThat(this.adapter.parseKind("nonsense")).isEqualTo(AssistantActionKind.HELP);
		assertThat(this.adapter.parseKind(null)).isEqualTo(AssistantActionKind.HELP);
	}

	// ── 어디로, 얼마나 기다려 부르는가 ────────────────────────────────────
	//
	// 시간 제한이 없으면 앱이 12초에 끊은 뒤에도 서버는 계속 기다리고, 그 사람의 하루
	// 한도는 이미 깎여 있다. 주소를 안 걸면 개인 키 한도(429)로 돌아간다.

	@Test
	@DisplayName("🔴 시간 제한을 밀리초로 건다 — 앱이 끊는 12초 안이어야 한다")
	void timeoutIsPassedInMillisecondsAndFitsInsideAppTimeout() {
		AssistantProperties properties = new AssistantProperties();
		properties.setTimeout(Duration.ofSeconds(10));

		var options = new GeminiAssistantAdapter(properties, null).httpOptions();

		// HttpOptions.timeout 은 밀리초를 받아 OkHttp callTimeout 으로 쓴다.
		assertThat(options.timeout()).contains(10_000);
		// 앱은 12초(frontend/src/api/client.ts 의 API_TIMEOUT_MS)에 끊는다.
		assertThat(properties.getTimeout()).isLessThan(Duration.ofSeconds(12));
	}

	@Test
	@DisplayName("🔴 기본 설정은 중계 주소와 그 중계에 열려 있는 모델을 같이 쓴다")
	void defaultsPointAtRelayWithAModelThatRelayServes() {
		AssistantProperties properties = new AssistantProperties();

		// 기본값은 비어 있다 — 주소를 박아 두면 설정 안 한 환경도 그리로 나간다.
		assertThat(properties.getBaseUrl()).isEmpty();
		// 모델은 중계가 여는 쪽이다. gemini-3.6-flash 는 중계에 없다(2026-09-18 실측).
		assertThat(properties.getModel()).isEqualTo("gemini-2.5-flash");
	}

	@Test
	@DisplayName("주소를 정하면 그 주소로 건다")
	void baseUrlIsAppliedWhenConfigured() {
		AssistantProperties properties = new AssistantProperties();
		properties.setBaseUrl("https://relay.example/gmsapi/generativelanguage.googleapis.com");

		var options = new GeminiAssistantAdapter(properties, null).httpOptions();

		assertThat(options.baseUrl()).contains("https://relay.example/gmsapi/generativelanguage.googleapis.com");
	}

	@Test
	@DisplayName("🔴 주소가 비면 안 건다 — 그때는 예전처럼 구글을 직접 부른다")
	void blankBaseUrlIsNotApplied() {
		AssistantProperties properties = new AssistantProperties();
		properties.setBaseUrl("   ");

		var options = new GeminiAssistantAdapter(properties, null).httpOptions();

		// 빈 문자열을 그대로 걸면 주소가 깨져 호출이 통째로 실패한다.
		assertThat(options.baseUrl()).isEmpty();
		// 시간 제한은 주소와 무관하게 언제나 건다.
		assertThat(options.timeout()).isPresent();
	}

	@Test
	@DisplayName("🔴 재시도를 끈다 — 한 번만 부른다 (S15P21E201-1749)")
	void retriesAreDisabled() {
		var options = new GeminiAssistantAdapter(new AssistantProperties(), null).httpOptions();

		// 비워 두면 SDK 가 기본값으로 5번까지 다시 부른다. 1 로 박아 둔다.
		assertThat(options.retryOptions()).isPresent();
		assertThat(options.retryOptions().get().attempts()).contains(1);
	}


	@Test
	@DisplayName("🔴 /plan 이면 지역·취향·출발일도 싣는다 — 광안리 맛집 2명 (S15P21E201-1825)")
	void planCarriesAreasCategoriesAndStart() {
		GeminiStructuredReply parsed = new GeminiStructuredReply("navigate", "안내할게요.", null, null, "여행 만들기",
				"/plan", null, 2, java.util.List.of("GWANGALLI", "haeundae", "GWANGALLI"), java.util.List.of("FOOD"),
				"2026-10-03");

		AssistantReply reply = this.adapter.toDomain(parsed);

		assertThat(reply.href()).isEqualTo("/plan?people=2&areas=GWANGALLI,HAEUNDAE&categories=FOOD&start=2026-10-03");
	}

	@Test
	@DisplayName("목록 밖 지역·취향 코드와 모양이 틀린 날짜는 뺀다")
	void planDropsUnknownCodesAndBadDate() {
		GeminiStructuredReply parsed = new GeminiStructuredReply("navigate", "안내할게요.", null, null, "여행 만들기",
				"/plan", null, null, java.util.List.of("SEOUL"), java.util.List.of("SHOPPING"), "내일");

		assertThat(this.adapter.toDomain(parsed).href()).isEqualTo("/plan");
	}

	@Test
	@DisplayName("/trips 에는 지역을 싣지 않는다")
	void tripsIgnoresAreas() {
		GeminiStructuredReply parsed = new GeminiStructuredReply("navigate", "안내할게요.", null, null, "내 여행",
				"/trips", null, null, java.util.List.of("HAEUNDAE"), null, null);

		assertThat(this.adapter.toDomain(parsed).href()).isEqualTo("/trips");
	}

}
