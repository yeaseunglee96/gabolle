package com.gabolle.backend.dish.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 음식 하나에 설명과 그림을 붙이는 데 쓰는 설정.
 *
 * <p>키와 중계 주소는 메뉴판 읽기와 같은 것을 쓰지만 시간 예산이 다르다. 메뉴판 읽기는 요청 안에서
 * 돌아 앱이 끊는 12초보다 먼저 포기해야 하고, 그림 만들기는 요청 밖 다른 스레드에서 돌아 10~46초를
 * 줘도 된다. 한 설정에 두 예산을 섞으면 둘 중 하나가 반드시 틀린다.
 */
@ConfigurationProperties(prefix = "gabolle.dish")
public class DishProperties {

	/** 비어 있으면 부르지 않는다 — 조용히 빈 설명을 주지 않으려고 호출부가 먼저 본다. */
	private String apiKey = "";

	private String baseUrl = "https://gms.ssafy.io/gmsapi/api.openai.com/v1";

	/** 설명을 쓰는 모델. 한국 음식 이름을 제대로 알아야 해서 메뉴판 읽기와 같은 것을 쓴다. */
	private String describeModel = "gpt-4.1-mini";

	/** 그림을 그리는 모델. {@code dall-e-3} 는 이 중계에 없다 — 넣으면 그 자리에서 죽는다. */
	private String imageModel = "gpt-image-1";

	/**
	 * 그림 품질. 올리기 전에 시간을 재 보라 — {@code low} 10.9초, {@code medium} 15.6초, 기본
	 * 45.8초다. 손바닥만 하게 뜨는 그림이라 {@code low} 로 충분하다.
	 */
	private String imageQuality = "low";

	private String imageSize = "1024x1024";

	/**
	 * 모델에게 받을 그림 형식. {@code webp} 로 바꾸지 마라 — 받은 그림을 우리가 줄여서 넣는데 자바
	 * 기본 {@code ImageIO} 는 webp 를 못 읽는다.
	 */
	private String imageFormat = "png";

	/** 보관할 그림의 긴 변 길이. 이보다 크면 줄인다. */
	private int storedImageWidth = 512;

	/** 줄여 넣을 때 쓰는 JPEG 품질(0~1). */
	private float storedImageQuality = 0.82f;

	private Duration connectTimeout = Duration.ofSeconds(3);

	/**
	 * 설명을 기다리는 시간. 요청 안에서 도는 호출이라 연결 + 읽기가 앱 대기 시간(12초)보다 짧아야
	 * 한다. 한 접시 설명은 1.25~1.89초로 끝난다.
	 */
	private Duration describeReadTimeout = Duration.ofSeconds(6);

	/**
	 * 그림을 기다리는 시간. 12초를 넘는 것이 맞다 — 요청 밖 다른 스레드에서 돌아 아무도 기다리지
	 * 않는다. 짧게 잡으면 10~15초짜리 정상 호출을 우리가 먼저 끊고 값은 이미 치른 뒤가 된다.
	 */
	private Duration imageReadTimeout = Duration.ofSeconds(90);

	/** 이름이 이보다 길면 자른다 — 장문을 이름 자리에 밀어 넣는 것도 공격이다. */
	private int maxNameLength = 60;

	/** 설명이 이보다 길면 자른다. */
	private int maxDescriptionLength = 300;

	/** 한 사람이 하루에 그림을 새로 만들 수 있는 횟수. 이미 만든 것을 꺼내 쓰는 것은 안 센다. */
	private int imageDailyLimit = 25;

	/**
	 * 한 사람이 1분에 그림을 새로 만들 수 있는 횟수. 메뉴 한 장에서 궁금한 음식을 연달아 누르는 것이
	 * 이 기능의 본래 쓰임이라 셋 이상을 허용한다. 같은 음식은 이름으로 모아 한 번만 만든다.
	 */
	private int imagePerMinuteLimit = 4;

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
