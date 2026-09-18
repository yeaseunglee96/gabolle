package com.gabolle.backend.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 여행 도우미 설정 — S15P21E201-802.
 *
 * <p>🔴 <b>필드 기본값만으로 기동해야 한다.</b> 키가 비어 있으면 호출 자체가 명확한 실패
 * ({@code AssistantVendorException})로 올라갈 뿐, 기동이 실패하면 안 된다 —
 * {@code TranslateProperties} 가 같은 이유로 같은 규칙을 지킨다.
 */
@ConfigurationProperties(prefix = "gabolle.assistant")
public class AssistantProperties {

	/** AI 업체 API 키. 비어 있으면 호출 자체를 시도하지 않고 즉시 명확한 실패로 끝난다. */
	private String apiKey = "";

	/**
	 * 모델 식별자 — 기본은 Gemini 3.6 Flash. 무료 티어(크레딧 결제 없이)로 쓸 수 있고
	 * 단순 분류·추출에 충분하다. {@code Claude Haiku 4.5} 에서 이쪽으로 바꿨다 —
	 * S15P21E201-802 코멘트 참고.
	 *
	 * <p>🔴 {@code gemini-2.5-flash} 는 신규 사용자에게 더 이상 제공되지 않는다(실측,
	 * 2026-09-11 — API 가 404 와 함께 이 모델로 바꾸라고 직접 알려줬다). Gemini 는 모델이
	 * 자주 세대교체되니, 나중에 이 기본값이 또 막히면 API 오류 메시지가 가리키는 모델로
	 * 바꾼다.
	 */
	private String model = "gemini-3.6-flash";

	private long maxTokens = 2048L;

	public String getApiKey() { return this.apiKey; }
	public void setApiKey(String apiKey) { this.apiKey = apiKey; }
	public String getModel() { return this.model; }
	public void setModel(String model) { this.model = model; }
	public long getMaxTokens() { return this.maxTokens; }
	public void setMaxTokens(long maxTokens) { this.maxTokens = maxTokens; }
}
