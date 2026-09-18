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

	/**
	 * 중계에 연결이 붙기까지 기다리는 시간.
	 *
	 * <p>🔴 <b>3초에서 2초로 줄였다 — S15P21E201-1271.</b> 전체 예산(연결 + 읽기)은 11초
	 * 그대로이고, <b>몫을 옮긴 것</b>이다. 이름·가격·이름번역을 함께 받게 되면서 읽는 쪽이
	 * 길어졌는데(4.56초 → 최대 6.89초, 음식 8줄 메뉴판 실측), 연결은 이름이 이미 풀린
	 * 같은 중계라 2초면 넉넉하다. 느린 것은 연결이 아니라 <b>모델이 생각하는 시간</b>이다.
	 */
	private Duration connectTimeout = Duration.ofSeconds(3);

	/**
	 * 바깥 모델을 기다리는 시간 — S15P21E201-1083.
	 *
	 * <p>이 값을 올리기 전에 읽을 것: <b>서버가 앱보다 먼저 포기해야 한다.</b> 서버가 더
	 * 오래 기다리면 <b>읽기가 성공해도 사용자는 그 결과를 못 받는다</b> — 앱이 이미 끊었기
	 * 때문이다. 그러면 바깥 호출값은 치르고, 그 사람의 하루 한도도 한 번 깎이고(한도는
	 * 부르기 <i>전에</i> 센다), 화면에는 실패로 보여 다시 누른다.
	 *
	 * <p>그래서 예산은 <b>연결 + 읽기 &lt; 앱 대기 시간</b> 이다. 규칙은
	 * {@code context/decisions.md} 의 {@code DEC-LATENCY-001} 이 소유한다.
	 *
	 * <h2>🔴 2026-09-19 — 8초로는 진짜 메뉴판을 못 읽는다 (S15P21E201-1315)</h2>
	 *
	 * 그전까지 잰 것은 <b>직접 만든 깨끗한 메뉴판</b>(한 칸, 음식 8개)이었고 4.5~5.9초였다.
	 * 사용자가 준 <b>실제 메뉴판</b>(3단 배치, 음식 30개)으로 재니 이렇다.
	 *
	 * <pre>
	 *   칸을 나눠 읽게 한 프롬프트        11.95 · 13.35초   (출력 토큰 1,577)
	 *   + 음식 줄의 번역문 생략            9.65 · 10.50초   (1,269)
	 *   + 원문 줄도 생략                  7.41 ·  9.61초   (1,245)
	 * </pre>
	 *
	 * <b>어느 것도 9초 안에 안 들어온다.</b> 그리고 원인이 구조적이다 — 시간을 먹는 것은
	 * 사진이 아니라 <b>써 내는 양</b>이다. 음식이 30개면 토큰 1,200개를 쓰는 데 10초가 걸리고,
	 * 프롬프트를 더 줄여도 <b>음식 수는 그대로다.</b>
	 *
	 * <p>그래서 <b>이 경로에 한해</b> 예산을 늘렸다 — 연결 3 + 읽기 25 = 28초, 앱은 30초.
	 * DEC-LATENCY-001 이 지키라는 것은 「둘 다 짧아야 한다」가 아니라 <b>「서버가 먼저
	 * 포기한다」</b>이고, 둘을 같이 늘리면 그 순서는 그대로다. 다른 경로의 앱 기본값(12초)은
	 * 안 건드렸다.
	 *
	 * <p>🔴 앱 쪽 짝은 {@code frontend/src/field/menuScan.ts} 의 요청 시간 제한이다.
	 * <b>한쪽만 바꾸면 안 된다</b> — 서버만 늘리면 앱이 먼저 끊어 위의 손해가 그대로 나고,
	 * 앱만 늘리면 서버가 먼저 끊는다. {@code MenuScanLatencyBudgetTest} 가 그 짝을 지킨다.
	 */
	private Duration readTimeout = Duration.ofSeconds(25);

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
