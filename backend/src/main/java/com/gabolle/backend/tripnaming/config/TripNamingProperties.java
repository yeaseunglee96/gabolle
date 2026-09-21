package com.gabolle.backend.tripnaming.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 여행 이름 짓기 설정. 기본값만으로 기동해야 한다 — 키가 비어 있으면 모델을 안 부르고
 * 템플릿 이름으로 답할 뿐, 기동은 실패하지 않는다.
 *
 * <p>키가 없을 때 503 으로 거절하는 메뉴판 읽기와 다르다. 이름은 틀려도 사람이 다치지
 * 않으므로 조용히 템플릿으로 물러선다.
 */
@ConfigurationProperties(prefix = "gabolle.trip-naming")
public class TripNamingProperties {

	/** 메뉴판 읽기와 같은 GMS 중계를 쓴다. */
	private String baseUrl = "https://gms.ssafy.io/gmsapi/api.openai.com/v1";

	/** 비어 있으면 모델을 안 부르고 템플릿 이름만 준다. 기동은 정상이다. */
	private String apiKey = "";

	private String model = "gpt-4o-mini";

	private Duration connectTimeout = Duration.ofSeconds(3);

	/** 이름 하나 짓는 일이라 짧게 끊는다. 늦어지면 템플릿으로 답하면 된다. */
	private Duration readTimeout = Duration.ofSeconds(8);

	/** 한 사람이 1분에 부를 수 있는 횟수 — 이름을 계속 다시 뽑는 것으로 크레딧이 녹는다. */
	private int perMinuteLimit = 6;

	/** 몇 개를 줄 것인가. 화면이 고를 수 있을 만큼만. */
	private int suggestionCount = 3;

	public String getBaseUrl() { return this.baseUrl; }
	public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
	public String getApiKey() { return this.apiKey; }
	public void setApiKey(String apiKey) { this.apiKey = apiKey; }
	public String getModel() { return this.model; }
	public void setModel(String model) { this.model = model; }
	public Duration getConnectTimeout() { return this.connectTimeout; }
	public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
	public Duration getReadTimeout() { return this.readTimeout; }
	public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
	public int getPerMinuteLimit() { return this.perMinuteLimit; }
	public void setPerMinuteLimit(int perMinuteLimit) { this.perMinuteLimit = perMinuteLimit; }
	public int getSuggestionCount() { return this.suggestionCount; }
	public void setSuggestionCount(int suggestionCount) { this.suggestionCount = suggestionCount; }
}
