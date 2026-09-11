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
				"여행 만들기", "/plan/basic");

		AssistantReply reply = this.adapter.toDomain(parsed);

		assertThat(reply.kind()).isEqualTo(AssistantActionKind.NAVIGATE);
		assertThat(reply.href()).isEqualTo("/plan/basic");
		assertThat(reply.label()).isEqualTo("여행 만들기");
	}

	@Test
	@DisplayName("🔴 navigate 인데 href 가 허용 목록 밖이면 HELP 로 낮춘다")
	void navigateWithDisallowedHrefIsDowngradedToHelp() {
		GeminiStructuredReply parsed = new GeminiStructuredReply("navigate", "안내할게요.", null, null, "아무 데나",
				"/admin/secret");

		AssistantReply reply = this.adapter.toDomain(parsed);

		assertThat(reply.kind()).isEqualTo(AssistantActionKind.HELP);
		assertThat(reply.href()).isNull();
		assertThat(reply.label()).isNull();
	}

	@Test
	@DisplayName("phrase 는 korean·pronunciation 만 채워진다")
	void phraseFillsOnlyKoreanAndPronunciation() {
		GeminiStructuredReply parsed = new GeminiStructuredReply("phrase", "현장에서 쓰세요.", "화장실이 어디예요?",
				"hwajangsiri eodiyeyo?", null, null);

		AssistantReply reply = this.adapter.toDomain(parsed);

		assertThat(reply.kind()).isEqualTo(AssistantActionKind.PHRASE);
		assertThat(reply.korean()).isEqualTo("화장실이 어디예요?");
		assertThat(reply.href()).isNull();
	}

	@Test
	@DisplayName("🔴 모델이 모르는 kind 를 지어내면 HELP 로 낮춘다")
	void unknownKindFallsBackToHelp() {
		assertThat(this.adapter.parseKind("plan")).isEqualTo(AssistantActionKind.HELP);
		assertThat(this.adapter.parseKind("nonsense")).isEqualTo(AssistantActionKind.HELP);
		assertThat(this.adapter.parseKind(null)).isEqualTo(AssistantActionKind.HELP);
	}
}
