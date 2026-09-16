package com.gabolle.backend.assistant.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.assistant.config.AssistantProperties;
import com.gabolle.backend.assistant.domain.AssistantActionKind;
import com.gabolle.backend.assistant.domain.AssistantReply;

/**
 * {@link GeminiAssistantAdapter#toDomain}·{@code parseKind} 검증 — S15P21E201-802.
 *
 * <p>실제 Gemini 호출 없이, 모델이 낼 수 있는 구조화 출력 모양을 직접 만들어 변환 로직만
 * 잰다 — 특히 모델이 허용 목록 밖의 href 나 모르는 kind 를 지어냈을 때 안전하게 HELP 로
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

	// ── /plan/basic 사전 채우기 (S15P21E201-985) ────────────────────────

	@Test
	@DisplayName("/plan/basic 이고 days·people 이 있으면 쿼리 파라미터로 실어 보낸다")
	void planBasicWithDaysAndPeopleGetsQueryParams() {
		GeminiStructuredReply parsed = new GeminiStructuredReply("navigate", "여행 만들기로 안내할게요.", null, null,
				"여행 만들기", "/plan/basic", 2, 3);

		AssistantReply reply = this.adapter.toDomain(parsed);

		assertThat(reply.href()).isEqualTo("/plan/basic?days=2&people=3");
	}

	@Test
	@DisplayName("days·people 이 없으면 물음표 없이 href 그대로다")
	void planBasicWithoutDaysOrPeopleHasNoQueryString() {
		GeminiStructuredReply parsed = new GeminiStructuredReply("navigate", "여행 만들기로 안내할게요.", null, null,
				"여행 만들기", "/plan/basic", null, null);

		AssistantReply reply = this.adapter.toDomain(parsed);

		assertThat(reply.href()).isEqualTo("/plan/basic");
	}

	@Test
	@DisplayName("🔴 범위 밖 숫자를 지어내면 그 파라미터만 조용히 뺀다")
	void outOfRangeValuesAreDroppedIndividually() {
		GeminiStructuredReply parsed = new GeminiStructuredReply("navigate", "여행 만들기로 안내할게요.", null, null,
				"여행 만들기", "/plan/basic", 999, 3);

		AssistantReply reply = this.adapter.toDomain(parsed);

		assertThat(reply.href()).isEqualTo("/plan/basic?people=3");
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
				"/plan/basic", 3, 4);

		AssistantReply reply = this.adapter.toDomain(parsed);

		assertThat(reply.label()).isEqualTo("여행 만들기");
	}

	// ── 대중교통 (S15P21E201-988) ────────────────────────────────────────

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

	@Test
	@DisplayName("🔴 모델이 모르는 kind 를 지어내면 HELP 로 낮춘다")
	void unknownKindFallsBackToHelp() {
		assertThat(this.adapter.parseKind("plan")).isEqualTo(AssistantActionKind.HELP);
		assertThat(this.adapter.parseKind("nonsense")).isEqualTo(AssistantActionKind.HELP);
		assertThat(this.adapter.parseKind(null)).isEqualTo(AssistantActionKind.HELP);
	}
}
