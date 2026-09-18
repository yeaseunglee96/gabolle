package com.gabolle.backend.dish.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 음식 하나에 설명과 그림을 붙이는 데 쓰는 설정 — S15P21E201-1272.
 *
 * <h2>왜 메뉴판 읽기 설정과 따로 두나</h2>
 *
 * 키와 중계 주소는 같은 것을 쓴다(설정 파일이 같은 환경변수로 채운다). 하지만
 * <b>시간 예산이 완전히 다르다.</b>
 *
 * <ul>
 *   <li>메뉴판 읽기는 <b>요청 안에서</b> 돈다. 앱이 12초에 끊으므로 서버는 그보다 먼저
 *       포기해야 한다 — {@code MenuScanLatencyBudgetTest} 가 그 규칙을 지킨다</li>
 *   <li>그림 만들기는 <b>요청 밖</b> 다른 스레드에서 돈다. 아무도 기다리지 않으므로
 *       60초를 줘도 된다. 실제로 10~46초가 걸린다(2026-09-18 실측)</li>
 * </ul>
 *
 * 한 설정에 두 예산을 섞으면 둘 중 하나가 반드시 틀린다. 그래서 갈랐다.
 */
@ConfigurationProperties(prefix = "gabolle.dish")
public class DishProperties {

	/** 🔴 비어 있으면 부르지 않는다 — 조용히 빈 설명을 주지 않으려고 호출부가 먼저 본다. */
	private String apiKey = "";

	private String baseUrl = "https://gms.ssafy.io/gmsapi/api.openai.com/v1";

	/**
	 * 설명을 쓰는 모델. 메뉴판 읽기와 같은 것을 쓴다 — 한국 음식 이름을 제대로 알아야
	 * 설명이 맞는데, 그 판단이 이미 S15P21E201-1268 에서 끝나 있다.
	 */
	private String describeModel = "gpt-4.1-mini";

	/**
	 * 그림을 그리는 모델.
	 *
	 * <p>🔴 {@code dall-e-3} 는 이 중계에 <b>없다</b> — {@code Model dall-e-3 is not
	 * available} 이 돌아온다(2026-09-18 실측). 이름만 바꿔 넣으면 그 자리에서 죽는다.
	 */
	private String imageModel = "gpt-image-1";

	/**
	 * 그림 품질.
	 *
	 * <p>🔴 <b>{@code low} 를 올리기 전에 재 보라.</b> 같은 요청으로 잰 값이다(2026-09-18).
	 *
	 * <pre>
	 *   low     10.90초
	 *   medium  15.55초
	 *   (기본)  45.77초
	 * </pre>
	 *
	 * 손바닥만 하게 뜨는 그림이라 {@code low} 로 충분하고, 사용자가 기다리는 시간이
	 * 곧 품질의 값이다.
	 */
	private String imageQuality = "low";

	private String imageSize = "1024x1024";

	/**
	 * 모델에게 받을 그림 형식.
	 *
	 * <p>🔴 <b>{@code webp} 로 바꾸지 마라.</b> 받은 그림을 우리가 줄여서 넣는데, 자바
	 * 기본 {@code ImageIO} 는 webp 를 <b>못 읽는다.</b> 용량은 png 가 조금 크지만
	 * (1,358KB 대 1,099KB) 어차피 줄여서 버리는 크기다.
	 */
	private String imageFormat = "png";

	/** 보관할 그림의 긴 변 길이. 이보다 크면 줄인다. */
	private int storedImageWidth = 512;

	/** 줄여 넣을 때 쓰는 JPEG 품질(0~1). */
	private float storedImageQuality = 0.82f;

	private Duration connectTimeout = Duration.ofSeconds(3);

	/**
	 * 설명을 기다리는 시간.
	 *
	 * <p>이것은 <b>요청 안에서</b> 도는 호출이라 메뉴판 읽기와 같은 규칙을 받는다 —
	 * 연결 + 읽기가 앱 대기 시간(12초)보다 짧아야 한다. 한 접시 설명은 1.25~1.89초로
	 * 끝난다(실측)라 6초면 넉넉하다.
	 */
	private Duration describeReadTimeout = Duration.ofSeconds(6);

	/**
	 * 그림을 기다리는 시간.
	 *
	 * <p>🔴 이 값이 12초를 넘는 것은 <b>틀린 것이 아니다.</b> 이 호출은 요청 밖 다른
	 * 스레드에서 돌고 아무도 붙잡고 기다리지 않는다. 오히려 짧게 잡으면 10~15초짜리
	 * 정상 호출을 우리가 먼저 끊어 버리고, 값은 이미 치른 뒤가 된다.
	 */
	private Duration imageReadTimeout = Duration.ofSeconds(90);

	/** 이름이 이보다 길면 자른다 — 장문을 이름 자리에 밀어 넣는 것도 공격이다. */
	private int maxNameLength = 60;

	/** 설명이 이보다 길면 자른다. */
	private int maxDescriptionLength = 300;

	/** 한 사람이 하루에 그림을 새로 만들 수 있는 횟수. 이미 만든 것을 꺼내 쓰는 것은 안 센다. */
	private int imageDailyLimit = 15;

	/** 한 사람이 1분에 그림을 새로 만들 수 있는 횟수. */
	private int imagePerMinuteLimit = 2;

	public String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getBaseUrl() {
		return this.baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getDescribeModel() {
		return this.describeModel;
	}

	public void setDescribeModel(String describeModel) {
		this.describeModel = describeModel;
	}

	public String getImageModel() {
		return this.imageModel;
	}

	public void setImageModel(String imageModel) {
		this.imageModel = imageModel;
	}

	public String getImageQuality() {
		return this.imageQuality;
	}

	public void setImageQuality(String imageQuality) {
		this.imageQuality = imageQuality;
	}

	public String getImageSize() {
		return this.imageSize;
	}

	public void setImageSize(String imageSize) {
		this.imageSize = imageSize;
	}

	public String getImageFormat() {
		return this.imageFormat;
	}

	public void setImageFormat(String imageFormat) {
		this.imageFormat = imageFormat;
	}

	public int getStoredImageWidth() {
		return this.storedImageWidth;
	}

	public void setStoredImageWidth(int storedImageWidth) {
		this.storedImageWidth = storedImageWidth;
	}

	public float getStoredImageQuality() {
		return this.storedImageQuality;
	}

	public void setStoredImageQuality(float storedImageQuality) {
		this.storedImageQuality = storedImageQuality;
	}

	public Duration getConnectTimeout() {
		return this.connectTimeout;
	}

	public void setConnectTimeout(Duration connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public Duration getDescribeReadTimeout() {
		return this.describeReadTimeout;
	}

	public void setDescribeReadTimeout(Duration describeReadTimeout) {
		this.describeReadTimeout = describeReadTimeout;
	}

	public Duration getImageReadTimeout() {
		return this.imageReadTimeout;
	}

	public void setImageReadTimeout(Duration imageReadTimeout) {
		this.imageReadTimeout = imageReadTimeout;
	}

	public int getMaxNameLength() {
		return this.maxNameLength;
	}

	public void setMaxNameLength(int maxNameLength) {
		this.maxNameLength = maxNameLength;
	}

	public int getMaxDescriptionLength() {
		return this.maxDescriptionLength;
	}

	public void setMaxDescriptionLength(int maxDescriptionLength) {
		this.maxDescriptionLength = maxDescriptionLength;
	}

	public int getImageDailyLimit() {
		return this.imageDailyLimit;
	}

	public void setImageDailyLimit(int imageDailyLimit) {
		this.imageDailyLimit = imageDailyLimit;
	}

	public int getImagePerMinuteLimit() {
		return this.imagePerMinuteLimit;
	}

	public void setImagePerMinuteLimit(int imagePerMinuteLimit) {
		this.imagePerMinuteLimit = imagePerMinuteLimit;
	}
}
