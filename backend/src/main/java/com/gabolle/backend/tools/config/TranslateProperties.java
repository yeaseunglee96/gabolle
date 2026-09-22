package com.gabolle.backend.tools.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 번역 중계 설정. 필드 기본값만으로 기동해야 한다 — 키가 비어 있으면 호출이 명확한 실패로 올라갈 뿐
 * 기동이 실패하면 안 된다.
 *
 * <p>키와 주소의 기본값은 일부러 비워 둔다. 여기에 GMS 주소를 박아 두면 설정을 안 한 환경에서도
 * 설정된 것으로 보여 어댑터가 호출을 시도한다.
 */
@ConfigurationProperties(prefix = "gabolle.tools.translate")
public class TranslateProperties {

	/** 번역 업체 API 키. 비어 있으면 호출이 즉시 명확한 실패로 끝난다. */
	private String vendorApiKey = "";

	/** 번역 업체 엔드포인트. 비어 있으면 키가 있어도 호출하지 않는다. */
	private String vendorBaseUrl = "";

	/** 메뉴판 읽기({@code MenuScanProperties})와 같은 값이다 — 번역이 새 모델을 처음 시험하는 자리가 되지 않게. */
	private String model = "gpt-4o-mini";

	/**
	 * 한 번 호출에서 모델이 쓸 수 있는 토큰 상한. 원문 길이는 {@code TranslationRequest} 가 이미 막으므로,
	 * 이 값은 모델이 답을 끝없이 뱉는 경우를 막는 쪽이다.
	 */
	private int maxTokens = 2048;

	private Duration connectTimeout = Duration.ofSeconds(2);

	private Duration readTimeout = Duration.ofSeconds(5);

	/** 같은 문장을 다시 번역하지 않고 답을 재사용하는 시간. */
	private Duration cacheTtl = Duration.ofDays(7);

	/**
	 * 일괄 번역 한 번에 받는 문장 수. 캐시에 없는 문장은 하나씩 업체를 부르므로(읽기 제한 시간 5초),
	 * 이 값이 곧 «최악의 경우 몇 번 기다리나» 다. 화면은 목록을 이 크기로 잘라 여러 번 부른다.
	 */
	private int batchMaxTexts = 20;

	/**
	 * 🔴 일괄 번역 한 번이 업체를 부르는 데 쓰는 시간 상한. 넘으면 남은 문장은 부르지 않고 SKIPPED 로
	 * 돌려준다 — 화면이 다음에 다시 부른다. 앞단 nginx 의 제한(60초)보다 넉넉히 짧아야 504 로 통째 잃지 않는다.
	 */
	private Duration batchTimeBudget = Duration.ofSeconds(20);

	public String getVendorApiKey() { return this.vendorApiKey; }
	public void setVendorApiKey(String vendorApiKey) { this.vendorApiKey = vendorApiKey; }
	public String getVendorBaseUrl() { return this.vendorBaseUrl; }
	public void setVendorBaseUrl(String vendorBaseUrl) { this.vendorBaseUrl = vendorBaseUrl; }
	public String getModel() { return this.model; }
	public void setModel(String model) { this.model = model; }
	public int getMaxTokens() { return this.maxTokens; }
	public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
	public Duration getConnectTimeout() { return this.connectTimeout; }
	public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
	public Duration getReadTimeout() { return this.readTimeout; }
	public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
	public Duration getCacheTtl() { return this.cacheTtl; }
	public void setCacheTtl(Duration cacheTtl) { this.cacheTtl = cacheTtl; }
	public int getBatchMaxTexts() { return this.batchMaxTexts; }
	public void setBatchMaxTexts(int batchMaxTexts) { this.batchMaxTexts = batchMaxTexts; }
	public Duration getBatchTimeBudget() { return this.batchTimeBudget; }
	public void setBatchTimeBudget(Duration batchTimeBudget) { this.batchTimeBudget = batchTimeBudget; }
}
