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

	/** Claude API 키. 비어 있으면 호출 자체를 시도하지 않고 즉시 명확한 실패로 끝난다. */
	private String apiKey = "";

	/** 모델 식별자 — 기본은 Claude Haiku 4.5(단순 분류·추출에 충분하고 저렴하다). */
	private String model = "claude-haiku-4-5";

	private long maxTokens = 2048L;

	public String getApiKey() { return this.apiKey; }
	public void setApiKey(String apiKey) { this.apiKey = apiKey; }
	public String getModel() { return this.model; }
	public void setModel(String model) { this.model = model; }
	public long getMaxTokens() { return this.maxTokens; }
	public void setMaxTokens(long maxTokens) { this.maxTokens = maxTokens; }
}
