package com.gabolle.backend.tools.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 번역 중계 설정 — S15P21E201-343, 업체를 GMS 로 옮기면서 칸이 둘 늘었다(S15P21E201-1235).
 *
 * <p>🔴 <b>필드 기본값만으로 기동해야 한다.</b> 업체 키가 비어 있으면 호출 자체가 명확한
 * 실패({@code TranslationVendorException})로 올라갈 뿐, 기동이 실패하면 안 된다 —
 * {@code RouteProperties} 가 같은 이유로 같은 규칙을 지킨다.
 *
 * <p>🔴 <b>키와 주소의 기본값은 일부러 비워 둔다.</b> 여기에 GMS 주소를 박아 두면, 설정을
 * 안 한 환경에서도 「설정된 것」으로 보여 어댑터가 호출을 시도한다. 기본값은
 * {@code application-dev.properties} 가 환경변수와 함께 준다 — 거기서는 메뉴판 읽기가
 * 쓰는 키로 물러서게 되어 있다.
 */
@ConfigurationProperties(prefix = "gabolle.tools.translate")
public class TranslateProperties {

	/** 번역 업체 API 키. 비어 있으면 호출이 즉시 명확한 실패로 끝난다. */
	private String vendorApiKey = "";

	/** 번역 업체 엔드포인트. 비어 있으면 키가 있어도 호출하지 않는다. */
	private String vendorBaseUrl = "";

	/**
	 * 부를 모델 이름 — S15P21E201-1235.
	 *
	 * <p>메뉴판 읽기({@code MenuScanProperties})와 <b>같은 값</b>이다. 그쪽이 운영에서 이
	 * 모델로 실제로 돌고 있으므로, 번역이 새 모델을 처음 시험하는 자리가 되지 않는다.
	 */
	private String model = "gpt-4o-mini";

	/**
	 * 한 번 호출에서 모델이 쓸 수 있는 토큰 상한 — S15P21E201-1235.
	 *
	 * <p>원문은 {@code TranslationRequest} 가 2000자로 이미 막는다. 이 값은 <b>모델이
	 * 답을 끝없이 뱉는 경우</b>를 막는 쪽이다 — 번역문 하나에 필요한 양보다 넉넉하다.
	 */
	private int maxTokens = 2048;

	private Duration connectTimeout = Duration.ofSeconds(2);

	private Duration readTimeout = Duration.ofSeconds(5);

	/** 같은 문장을 다시 번역하지 않고 답을 재사용하는 시간 — 티켓이 정한 값(7일)이다. */
	private Duration cacheTtl = Duration.ofDays(7);

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
}
