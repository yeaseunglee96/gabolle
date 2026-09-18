package com.gabolle.backend.assistant.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 여행 도우미 설정 — S15P21E201-802, 중계 경로와 시간 제한이 붙음(S15P21E201-1253).
 *
 * <p>🔴 <b>필드 기본값만으로 기동해야 한다.</b> 키가 비어 있으면 호출 자체가 명확한 실패
 * ({@code AssistantVendorException})로 올라갈 뿐, 기동이 실패하면 안 된다 —
 * {@code TranslateProperties} 가 같은 이유로 같은 규칙을 지킨다.
 *
 * <h2>🔴 {@link #baseUrl} 과 {@link #model} 은 같이 움직인다</h2>
 * 아래 두 칸은 따로 고르면 안 된다. 기본 모델({@code gemini-2.5-flash})은 <b>GMS 중계를
 * 지날 때만</b> 열려 있고, 구글을 직접 부르면 신규 키에는 404 다(아래 {@link #model} 주석).
 * 하나만 바꾸면 그 자리에서 기능이 통째로 죽는다.
 */
@ConfigurationProperties(prefix = "gabolle.assistant")
public class AssistantProperties {

	/** AI 업체 API 키. 비어 있으면 호출 자체를 시도하지 않고 즉시 명확한 실패로 끝난다. */
	private String apiKey = "";

	/**
	 * 모델을 부를 주소 — S15P21E201-1253. 비우면 구글을 <b>직접</b> 부른다.
	 *
	 * <p>🔴 <b>기본값을 일부러 비워 둔다.</b> 여기에 중계 주소를 박아 두면 설정을 안 한
	 * 환경에서도 그 주소로 나가려 한다. 실제 값은 {@code application-dev.properties} 가 준다 —
	 * 거기서 SSAFY GMS(우리 팀이 쓰는 중계) 를 가리키고, 키도 메뉴판 읽기와 같은 것을 쓴다.
	 *
	 * <p><b>왜 중계로 옮겼나</b> — 개인 구글 키는 무료 한도가 좁아서, 같은 질문을 여섯 번
	 * 연속으로 보내면 <b>첫 한 번만 성공하고 나머지가 429</b> 로 막혔다(2026-09-18 실측).
	 * 사용자에게는 그것이 「제공처가 잠시 응답하지 않아요」로 보인다. 중계 쪽 키로는 여덟 번을
	 * 연속으로 보내도 여덟 번 다 통과했다.
	 */
	private String baseUrl = "";

	/**
	 * 모델 식별자.
	 *
	 * <p>🔴 <b>2026-09-11 의 실측</b> — {@code gemini-2.5-flash} 는 <b>구글을 직접 부를 때</b>
	 * 신규 사용자에게 더 이상 제공되지 않는다(API 가 404 와 함께 다른 모델로 바꾸라고 직접
	 * 알려줬다). 그래서 그때 {@code gemini-3.6-flash} 로 올렸다. 그 실측은 지금도 유효하다.
	 *
	 * <p>🔴 <b>2026-09-18 의 실측 — 중계를 지나면 반대가 된다.</b> GMS 중계에는
	 * {@code gemini-3.6-flash} 가 <b>열려 있지 않고</b>({@code Model gemini-3.6-flash is not
	 * available in Model}), {@code gemini-2.5-flash} 는 <b>통한다.</b> 그래서 중계로 옮기면서
	 * 이 값을 되돌렸다. 위 {@link #baseUrl} 을 비우면 이 모델은 다시 404 가 된다.
	 *
	 * <p>답이 나빠지지 않는지는 재 보고 정했다. 여덟 개 질문(일수·인원 추출, 버스, 환율,
	 * 표현 요청, 지하철, 지시 주입)을 두 모델에 나란히 넣어 <b>분류·{@code days}·{@code people}
	 * 이 모두 같았고</b>, 중계 쪽이 오히려 빨랐다(1.6~2.4초 대 2.9초).
	 *
	 * <p>Gemini 는 모델이 자주 세대교체된다. 나중에 이 기본값이 또 막히면, <b>어느 경로에서
	 * 막혔는지를 먼저 보고</b> 그 경로가 알려주는 모델로 바꾼다.
	 */
	private String model = "gemini-2.5-flash";

	private long maxTokens = 2048L;

	/**
	 * 바깥 모델을 기다리는 시간 — S15P21E201-1253.
	 *
	 * <p>🔴 <b>앱이 12초에 요청을 끊는다</b>({@code frontend/src/api/client.ts} 의
	 * {@code API_TIMEOUT_MS}). 서버가 그보다 오래 기다리면, 사용자는 이미 실패 화면을 보고
	 * 있는데 서버만 요청 스레드를 붙잡고 있게 된다. 그 사이 사용자가 다시 누르면 스레드가
	 * 하나 더 묶인다. 게다가 {@link #maxRequestsPerMinute} 한도는 <b>부르기 전에</b> 세므로,
	 * 그 사람은 결과를 못 받고 한도만 깎인다 — 메뉴판 읽기가 {@code S15P21E201-1083} 에서
	 * 정확히 이 일을 겪었고, 거기서 정한 예산이 <b>연결 + 읽기 &lt; 앱 대기 시간</b>이다.
	 *
	 * <p>제한이 없을 때 실제로 <b>35.96초</b>를 붙잡고 죽는 것을 봤다(2026-09-18 운영 실측).
	 * 정상 응답은 1.6~2.9초라 10초면 넉넉하고, 앱의 12초 안에 들어온다.
	 *
	 * <p>이 값은 {@code HttpOptions.timeout} 으로 넘어간다. 그쪽은 <b>밀리초</b>를 받아
	 * OkHttp 의 {@code callTimeout}(연결·전송·응답을 합친 호출 전체에 거는 제한)으로 쓴다 —
	 * 라이브러리 바이트코드를 열어 확인했다({@code google-genai 1.65.0}).
	 */
	private Duration timeout = Duration.ofSeconds(10);

	/**
	 * 한 사용자가 1분 안에 부를 수 있는 최대 횟수 — 무료 티어 한도를 한 사람이 다 써버리는
	 * 것을 막는다. 단일 서버 인스턴스 기준 메모리 카운터라, 인스턴스를 여러 대로 늘리면
	 * 사용자별 한도가 인스턴스 수만큼 늘어난다 — 지금 배포 규모에서는 문제되지 않는다.
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
