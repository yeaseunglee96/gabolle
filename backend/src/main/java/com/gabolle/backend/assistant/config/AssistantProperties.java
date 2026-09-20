package com.gabolle.backend.assistant.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 여행 도우미 설정. 필드 기본값만으로 기동해야 한다 — 키가 비면 호출이
 * AssistantVendorException 으로 실패할 뿐 기동이 실패하면 안 된다.
 *
 * baseUrl 과 model 은 같이 움직인다. 기본 모델은 중계를 지날 때만 열려 있고 구글을 직접
 * 부르면 404 라, 하나만 바꾸면 기능이 통째로 죽는다.
 */
@ConfigurationProperties(prefix = "gabolle.assistant")
public class AssistantProperties {

	/** AI 업체 API 키. 비어 있으면 호출 자체를 시도하지 않고 즉시 명확한 실패로 끝난다. */
	private String apiKey = "";

	/**
	 * 모델을 부를 주소. 비우면 구글을 직접 부른다.
	 *
	 * 기본값을 일부러 비워 둔다 — 중계 주소를 박으면 설정을 안 한 환경에서도 그리로 나간다.
	 * 실제 값은 application-dev.properties 가 준다. 개인 구글 키는 무료 한도가 좁아 연속
	 * 요청이 429 로 막히므로 중계를 쓴다.
	 */
	private String baseUrl = "";

	/**
	 * 모델 식별자. baseUrl 과 짝이다 — 이 모델은 중계에서만 열려 있고, baseUrl 을 비워
	 * 구글을 직접 부르면 404 가 된다. 반대로 구글 직접 호출에서 되는 모델은 중계에 없다.
	 *
	 * Gemini 는 모델이 자주 세대교체된다. 막히면 어느 경로에서 막혔는지를 먼저 보고 그
	 * 경로가 알려주는 모델로 바꾼다.
	 */
	private String model = "gemini-2.5-flash";

	private long maxTokens = 2048L;

	/**
	 * 바깥 모델을 기다리는 시간. 앱이 12초에 요청을 끊으므로 그보다 짧아야 한다 — 길면
	 * 사용자는 이미 실패 화면을 보는데 서버만 스레드를 붙잡고, 한도는 부르기 전에 세므로
	 * 그 사람은 결과 없이 한도만 깎인다. 정상 응답은 1.6~2.9초다.
	 *
	 * HttpOptions.timeout 으로 넘어가 OkHttp 의 callTimeout(연결·전송·응답 전체)이 된다.
	 */
	private Duration timeout = Duration.ofSeconds(10);

	/**
	 * 한 사용자가 1분 안에 부를 수 있는 최대 횟수. 메모리 카운터라 인스턴스를 여러 대로
	 * 늘리면 사용자별 한도도 인스턴스 수만큼 늘어난다.
	 */
	private int maxRequestsPerMinute = 10;

	private int maxHistoryTurns = 6;

	public String getApiKey() { return this.apiKey; }
	public void setApiKey(String apiKey) { this.apiKey = apiKey; }
	public String getBaseUrl() { return this.baseUrl; }
	public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
	public String getModel() { return this.model; }
	public void setModel(String model) { this.model = model; }
	public long getMaxTokens() { return this.maxTokens; }
	public void setMaxTokens(long maxTokens) { this.maxTokens = maxTokens; }
	public Duration getTimeout() { return this.timeout; }
	public void setTimeout(Duration timeout) { this.timeout = timeout; }
	public int getMaxRequestsPerMinute() { return this.maxRequestsPerMinute; }
	public void setMaxRequestsPerMinute(int maxRequestsPerMinute) { this.maxRequestsPerMinute = maxRequestsPerMinute; }
	public int getMaxHistoryTurns() { return this.maxHistoryTurns; }
	public void setMaxHistoryTurns(int maxHistoryTurns) { this.maxHistoryTurns = maxHistoryTurns; }
}
