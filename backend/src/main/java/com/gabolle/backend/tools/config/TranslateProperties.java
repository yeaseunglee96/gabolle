package com.gabolle.backend.tools.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 번역 중계 설정 — S15P21E201-343.
 *
 * <p>🔴 <b>필드 기본값만으로 기동해야 한다.</b> 업체 키가 비어 있으면 호출 자체가 명확한
 * 실패({@code TranslationVendorException})로 올라갈 뿐, 기동이 실패하면 안 된다 —
 * {@code RouteProperties} 가 같은 이유로 같은 규칙을 지킨다.
 */
@ConfigurationProperties(prefix = "gabolle.tools.translate")
public class TranslateProperties {

	/** 번역 업체 API 키. 비어 있으면 호출이 즉시 명확한 실패로 끝난다. */
	private String vendorApiKey = "";

	/** 번역 업체 엔드포인트. 비어 있으면 키가 있어도 호출하지 않는다. */
	private String vendorBaseUrl = "";

	private Duration connectTimeout = Duration.ofSeconds(2);

	private Duration readTimeout = Duration.ofSeconds(5);

	/** 같은 문장을 다시 번역하지 않고 답을 재사용하는 시간 — 티켓이 정한 값(7일)이다. */
	private Duration cacheTtl = Duration.ofDays(7);

	public String getVendorApiKey() { return this.vendorApiKey; }
	public void setVendorApiKey(String vendorApiKey) { this.vendorApiKey = vendorApiKey; }
	public String getVendorBaseUrl() { return this.vendorBaseUrl; }
	public void setVendorBaseUrl(String vendorBaseUrl) { this.vendorBaseUrl = vendorBaseUrl; }
	public Duration getConnectTimeout() { return this.connectTimeout; }
	public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
	public Duration getReadTimeout() { return this.readTimeout; }
	public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
	public Duration getCacheTtl() { return this.cacheTtl; }
	public void setCacheTtl(Duration cacheTtl) { this.cacheTtl = cacheTtl; }
}
