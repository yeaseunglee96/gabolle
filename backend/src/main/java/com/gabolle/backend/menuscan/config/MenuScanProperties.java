package com.gabolle.backend.menuscan.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 메뉴판 읽기 설정 — S15P21E201-1025.
 *
 * <p>🔴 <b>기본값만으로 기동해야 한다.</b> 키가 비어 있으면 <b>호출이 명확한 실패</b>로
 * 끝날 뿐, 기동이 실패하면 안 된다 — {@code TranslateProperties} 가 같은 이유로 같은 규칙을
 * 지킨다.
 *
 * <h2>🔴 중계 주소의 모양을 깨지 않는다</h2>
 * {@code gms.ssafy.io/gmsapi/api.openai.com/v1} 은 <b>OpenAI 주소를 그대로 뒤에 붙이는
 * 통과형 중계</b>다. 그래서 코드 입장에서는 그냥 OpenAI 다 — <b>크레딧이 끝나면 이 한 줄만
 * 바꿔 진짜 OpenAI 로 간다.</b> 중계에만 있는 문법을 코드에 넣으면 그 성질이 사라진다.
 */
@ConfigurationProperties(prefix = "gabolle.menu-scan")
public class MenuScanProperties {

	/** 중계 주소. 비어 있으면 키가 있어도 호출하지 않는다. */
	private String baseUrl = "https://gms.ssafy.io/gmsapi/api.openai.com/v1";

	/** 🔴 키. 비어 있으면 호출이 즉시 «설정이 없다» 로 끝난다 — 조용히 빈 결과를 주지 않는다. */
	private String apiKey = "";

	private String model = "gpt-4o-mini";

	private Duration connectTimeout = Duration.ofSeconds(3);

	/** 사진을 읽는 일이라 번역보다 오래 걸린다. */
	private Duration readTimeout = Duration.ofSeconds(30);

	/**
	 * 받을 수 있는 사진 크기.
	 *
	 * <p>🔴 큰 사진은 <b>크레딧을 태운다.</b> 키가 팀 공용이라 한 사람이 다 쓰면 전부 멈춘다.
	 */
	private long maxImageBytes = 8L * 1024 * 1024;

	/** 한 사람이 하루에 쓸 수 있는 횟수. */
	private int dailyLimit = 20;

	/** 한 사람이 1분에 쓸 수 있는 횟수 — 연타와 자동 스크립트를 막는다. */
	private int perMinuteLimit = 3;

	/**
	 * 응답에서 받아들일 최대 줄 수.
	 *
	 * <p>🔴 주입 대비다. 메뉴판에 «이전 지시를 무시하고 …» 뒤에 장문을 인쇄해 두면 모델이
	 * 긴 답을 내놓을 수 있다. 상한이 없으면 그 길이가 그대로 화면과 응답에 실린다.
	 */
	private int maxLines = 60;

	/** 한 줄의 최대 글자 수. 같은 이유다. */
	private int maxLineLength = 120;

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
	public long getMaxImageBytes() { return this.maxImageBytes; }
	public void setMaxImageBytes(long maxImageBytes) { this.maxImageBytes = maxImageBytes; }
	public int getDailyLimit() { return this.dailyLimit; }
	public void setDailyLimit(int dailyLimit) { this.dailyLimit = dailyLimit; }
	public int getPerMinuteLimit() { return this.perMinuteLimit; }
	public void setPerMinuteLimit(int perMinuteLimit) { this.perMinuteLimit = perMinuteLimit; }
	public int getMaxLines() { return this.maxLines; }
	public void setMaxLines(int maxLines) { this.maxLines = maxLines; }
	public int getMaxLineLength() { return this.maxLineLength; }
	public void setMaxLineLength(int maxLineLength) { this.maxLineLength = maxLineLength; }
}
