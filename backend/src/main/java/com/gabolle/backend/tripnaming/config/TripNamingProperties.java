package com.gabolle.backend.tripnaming.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 여행 이름 짓기 설정 — S15P21E201-1025.
 *
 * <p>🔴 <b>기본값만으로 기동해야 한다.</b> 키가 비어 있으면 모델을 안 부르고
 * <b>템플릿 이름</b>으로 답할 뿐, 기동이 실패하면 안 된다 — {@code MenuScanProperties} 와
 * 같은 규칙이다.
 *
 * <p>🔴 다만 메뉴판 읽기와 <b>다른 점</b>이 하나 있다. 그쪽은 키가 없으면 503 으로 거절한다
 * — 「못 읽었다」를 「없다」로 보이게 하면 사람이 다치기 때문이다. 이름 짓기는 <b>틀려도
 * 사람이 안 다친다.</b> 그래서 여기서는 조용히 템플릿으로 물러서는 것이 맞다.
 * 같은 규칙을 기계적으로 복사하지 않는다.
 */
@ConfigurationProperties(prefix = "gabolle.trip-naming")
public class TripNamingProperties {

	/** 메뉴판 읽기와 같은 GMS 중계를 쓴다. */
	private String baseUrl = "https://gms.ssafy.io/gmsapi/api.openai.com/v1";

	/** 🔴 비어 있으면 모델을 안 부르고 템플릿 이름만 준다. 기동은 정상이다. */
	private String apiKey = "";

	private String model = "gpt-4o-mini";

	private Duration connectTimeout = Duration.ofSeconds(3);

	/**
	 * 이름 하나 짓는 일이라 짧게 끊는다.
	 *
	 * <p>🔴 <b>기다릴 이유가 없다.</b> 늦어지면 템플릿으로 답하면 되고, 사용자는 이름 짓기
	 * 버튼을 눌러 놓고 화면을 보고 있다.
	 */
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
