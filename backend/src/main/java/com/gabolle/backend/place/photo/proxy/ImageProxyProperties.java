package com.gabolle.backend.place.photo.proxy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공공 사진 대리 조회 설정(S15P21E201-1954). 필드 기본값만으로 기동해야 한다.
 *
 * <p>허락 호스트를 넓힐 때는 그 호스트가 실제로 앱에서 못 받는 이유(인증서 사슬 등)를 먼저 확인한다 —
 * 이 경로는 남의 서버를 우리 이름으로 부르는 문이라, 이유 없이 넓히면 그만큼 남용될 자리가 생긴다.
 */
@ConfigurationProperties(prefix = "gabolle.image-proxy")
public class ImageProxyProperties {

	/**
	 * 대신 받아 줄 호스트. 정확히 같은 이름만 허락한다(하위 도메인도 따로 적어야 한다).
	 * www.visitbusan.net 은 중간 인증서를 안 보내 안드로이드가 사진을 거절한다 — 그래서 넣었다.
	 */
	private List<String> allowedHosts = new ArrayList<>(List.of("www.visitbusan.net"));

	/** 사진 한 장의 상한. 넘으면 끝까지 읽지 않고 502. */
	private long maxBytes = 10L * 1024 * 1024;

	/** 따라갈 리디렉트 수. 매번 허락 호스트를 다시 검사한다. */
	private int maxRedirects = 3;

	private Duration connectTimeout = Duration.ofSeconds(3);

	/** 요청 하나 전체(머리 받기까지)의 시간 제한. */
	private Duration requestTimeout = Duration.ofSeconds(10);

	public List<String> getAllowedHosts() { return this.allowedHosts; }
	public void setAllowedHosts(List<String> allowedHosts) { this.allowedHosts = allowedHosts; }
	public long getMaxBytes() { return this.maxBytes; }
	public void setMaxBytes(long maxBytes) { this.maxBytes = maxBytes; }
	public int getMaxRedirects() { return this.maxRedirects; }
	public void setMaxRedirects(int maxRedirects) { this.maxRedirects = maxRedirects; }
	public Duration getConnectTimeout() { return this.connectTimeout; }
	public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
	public Duration getRequestTimeout() { return this.requestTimeout; }
	public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
}
