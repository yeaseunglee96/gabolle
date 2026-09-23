package com.gabolle.backend.menuscan.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 메뉴판 읽기 설정.
 *
 * <p>기본값만으로 기동해야 한다. 키가 비어 있으면 호출이 명확한 실패로 끝날 뿐 기동이 실패하면
 * 안 된다.
 *
 * <p>중계 주소는 OpenAI 주소를 그대로 뒤에 붙이는 통과형이라 코드 입장에서는 그냥 OpenAI 다 —
 * 크레딧이 끝나면 이 한 줄만 바꿔 진짜 OpenAI 로 간다. 중계에만 있는 문법을 코드에 넣으면 그 성질이
 * 사라진다.
 */
@ConfigurationProperties(prefix = "gabolle.menu-scan")
public class MenuScanProperties {

	/** 중계 주소. 비어 있으면 키가 있어도 호출하지 않는다. */
	private String baseUrl = "https://gms.ssafy.io/gmsapi/api.openai.com/v1";

	/** 키. 비어 있으면 호출이 즉시 «설정이 없다»로 끝난다 — 조용히 빈 결과를 주지 않는다. */
	private String apiKey = "";

	/**
	 * 사진 속 글자를 읽는 모델.
	 *
	 * <p>{@code gpt-4o-mini} 로 돌아가지 마라 — 한글을 틀리게 옮겨 적고 더 느리다. 이 기능이
	 * 알레르기 낱말을 찾는 데 쓰이므로 한 글자만 흘려도 화면에는 «해당 없음»으로 나오고, 사용자는
	 * 그것을 «안 들어 있구나»로 읽는다.
	 *
	 * <p>{@code gpt-4o} 는 정확하지만 군말을 붙여 그대로 쓸 수 없었다.
	 */
	private String model = "gpt-4.1-mini";

	/**
	 * 중계에 연결이 붙기까지 기다리는 시간. 느린 것은 연결이 아니라 모델이 생각하는 시간이므로,
	 * 전체 예산이 모자라면 이쪽이 아니라 읽기 쪽을 본다.
	 */
	private Duration connectTimeout = Duration.ofSeconds(3);

	/**
	 * 바깥 모델을 기다리는 시간.
	 *
	 * <p>불변식은 연결 + 읽기 &lt; 앱 대기 시간이다. 서버가 더 오래 기다리면 읽기가 성공해도
	 * 사용자는 결과를 못 받는다 — 앱이 이미 끊었는데 호출값은 치렀고 하루 한도도 깎였다.
	 *
	 * <p>이 경로만 예산이 크다. 음식 30개짜리 실제 메뉴판은 써 내는 양 때문에 10초를 넘고, 프롬프트를
	 * 줄여도 음식 수는 그대로다. 앱 쪽 짝은 {@code frontend/src/field/menuScan.ts} 의 요청 시간
	 * 제한이고 한쪽만 바꾸면 안 된다 — {@code MenuScanLatencyBudgetTest} 가 그 짝을 지킨다.
	 *
	 * <p>🔴 25초에서 20초로 줄였다 (S15P21E201-1538). 이 경로가 이제 «우리 모델이 실패했을 때의
	 * 대체»라, 우리 모델의 예산(연결 1 + 읽기 5)과 더해도 앱 대기 30초 안에 들어와야 한다. 고친
	 * 프롬프트의 실측이 10.91~13.51초라 20초면 6초 넘게 남는다.
	 */
	private Duration readTimeout = Duration.ofSeconds(20);

	/**
	 * 서버 안의 메뉴판 OCR({@code backend/menu-ocr}, 컨테이너 이름 {@code menu-ocr}) 주소.
	 *
	 * <p>비어 있으면 부르지 않고 곧장 위의 GMS 비전으로 읽는다. 기본값을 비워 두는 이유는 그 컨테이너가
	 * 없는 곳(시험, 개발 PC)에서 매번 연결 실패를 겪고 물러서지 않게 하려는 것이다. 운영 값은
	 * application-dev.properties 가 준다 — 그 값을 비우는 것이 «우리 모델을 끄는» 손잡이다.
	 */
	private String localBaseUrl = "";

	/** 같은 망 안의 컨테이너라 연결이 1초를 넘으면 떠 있지 않은 것이다. 오래 기다릴 이유가 없다. */
	private Duration localConnectTimeout = Duration.ofSeconds(1);

	/**
	 * 우리 모델을 기다리는 시간. 운영 서버 실측은 실사진 한 장 1.9초(2026-09-23, 조용할 때)다. 같은
	 * 기계에서 CI 잡이 돌면 1.5~3배 느려진다. 이 시간을 넘기면 GMS 비전으로 대신 읽는다.
	 */
	private Duration localReadTimeout = Duration.ofSeconds(5);

	/**
	 * 우리 모델이 읽은 음식 이름 가운데 한식진흥원 800선 사전에 없는 것만 글자로 번역하는 모델.
	 * 사진을 보내지 않으므로 싼 모델로 충분하다 — 현장 번역과 같은 모델이다.
	 */
	private String translateModel = "gpt-4o-mini";

	/** 이름 몇십 개를 옮기는 짧은 요청이다. 넘기면 번역 없이 원문 이름만 보여 준다. */
	private Duration translateReadTimeout = Duration.ofSeconds(6);

	/** 받을 수 있는 사진 크기. 큰 사진은 크레딧을 태우고, 키가 팀 공용이라 한 사람이 다 쓰면 전부 멈춘다. */
	private long maxImageBytes = 8L * 1024 * 1024;

	/** 한 사람이 하루에 쓸 수 있는 횟수. */
	private int dailyLimit = 20;

	/** 한 사람이 1분에 쓸 수 있는 횟수 — 연타와 자동 스크립트를 막는다. */
	private int perMinuteLimit = 3;

	/**
	 * 응답에서 받아들일 최대 줄 수. 주입 대비다 — 메뉴판에 장문을 인쇄해 두면 모델이 긴 답을
	 * 내놓고, 상한이 없으면 그 길이가 그대로 화면과 응답에 실린다.
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
	public String getLocalBaseUrl() { return this.localBaseUrl; }
	public void setLocalBaseUrl(String localBaseUrl) { this.localBaseUrl = localBaseUrl; }
	public Duration getLocalConnectTimeout() { return this.localConnectTimeout; }
	public void setLocalConnectTimeout(Duration localConnectTimeout) { this.localConnectTimeout = localConnectTimeout; }
	public Duration getLocalReadTimeout() { return this.localReadTimeout; }
	public void setLocalReadTimeout(Duration localReadTimeout) { this.localReadTimeout = localReadTimeout; }
	public String getTranslateModel() { return this.translateModel; }
	public void setTranslateModel(String translateModel) { this.translateModel = translateModel; }
	public Duration getTranslateReadTimeout() { return this.translateReadTimeout; }
	public void setTranslateReadTimeout(Duration translateReadTimeout) { this.translateReadTimeout = translateReadTimeout; }
}
