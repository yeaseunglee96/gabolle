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

	/**
	 * 사진 속 글자를 읽는 모델 — S15P21E201-1268.
	 *
	 * <p>🔴 <b>{@code gpt-4o-mini} 로 돌아가지 마라. 한글을 틀리게 옮겨 적는다.</b>
	 * 같은 사진으로 나란히 재 봤다(2026-09-18): 「얼음 빼주세요」를 {@code gpt-4o-mini} 는
	 * 「염음 빼주세요」·「얼음 뺏 주세요」로, {@code gpt-4.1-mini} 는 제대로 읽었다.
	 *
	 * <p>이것이 조용한 결함인 이유는 이 기능이 <b>알레르기 낱말을 찾는 데</b> 쓰이기
	 * 때문이다. 낱말을 한 글자 흘리면 화면에는 「해당 없음」으로 나오고, 사용자는 그것을
	 * 「안 들어 있구나」로 읽는다. 못 읽은 것과 없는 것이 같아 보인다.
	 *
	 * <p>느려서 못 쓰는 것도 아니다 — 왕복 1.87·3.07초로 {@code gpt-4o-mini}(2.66·3.57초)
	 * 보다 오히려 빨랐다. 그래서 아래 시간 예산은 그대로 둔다.
	 *
	 * <p>{@code gpt-4o} 는 정확하지만 「나는 이미지를 보여줄 수 없지만」 같은 군말을 붙여
	 * 그대로 쓸 수 없었다.
	 */
	private String model = "gpt-4.1-mini";

	private Duration connectTimeout = Duration.ofSeconds(3);

	/**
	 * 바깥 모델을 기다리는 시간 — S15P21E201-1083.
	 *
	 * <p>이 값을 올리기 전에 읽을 것: <b>앱은 12초에 요청을 끊는다</b>
	 * ({@code frontend/src/api/client.ts} 의 {@code API_TIMEOUT_MS}, 이 경로만 따로 늘리지
	 * 않는다). 서버가 그보다 오래 기다리면 <b>읽기가 성공해도 사용자는 그 결과를 못 받는다</b> —
	 * 앱이 이미 끊었기 때문이다. 그러면 바깥 호출값은 치르고, 그 사람의 하루 한도도 한 번
	 * 깎이고(한도는 부르기 <i>전에</i> 센다), 화면에는 실패로 보여 다시 누른다.
	 *
	 * <p>그래서 예산은 <b>연결 + 읽기 &lt; 앱 대기 시간</b> 이다. 3 + 8 = 11 초로 1초를 남겼다.
	 * 늘려야 한다면 이 숫자만 올리지 말고 앱 쪽 대기 시간을 이 경로에 한해 함께 늘린다.
	 * 규칙은 {@code context/decisions.md} 의 {@code DEC-LATENCY-001} 이 소유한다.
	 *
	 * <p>8초가 실제 메뉴판 사진에 충분한지는 <b>아직 안 재 봤다.</b> 운영에 열쇠가 들어간 뒤
	 * 실제 응답 시간을 재서 조정한다.
	 */
	private Duration readTimeout = Duration.ofSeconds(8);

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
