package com.gabolle.backend.weather.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 기상청 단기예보 조회 설정. 필드 기본값만으로 기동해야 한다 — 키가 비면 호출이
 * WeatherVendorException 으로 실패할 뿐 기동이 실패하면 안 된다.
 */
@ConfigurationProperties(prefix = "gabolle.weather")
public class WeatherProperties {

	/** 기상청 API 허브가 발급하는 인증키. 비어 있으면 호출이 즉시 명확한 실패로 끝난다. */
	private String kmaServiceKey = "";

	/**
	 * 공공데이터포털이 아니라 기상청 API 허브다. 같은 API 라 인자도 응답도 같고, 다른 것은
	 * 주소와 인증 인자 이름뿐이다(serviceKey → authKey — KmaWeatherVendorAdapter 참고).
	 */
	private String kmaBaseUrl = "https://apihub.kma.go.kr/api/typ02/openApi/VilageFcstInfoService_2.0";

	private Duration connectTimeout = Duration.ofSeconds(2);

	private Duration readTimeout = Duration.ofSeconds(5);

	/**
	 * 같은 격자·같은 발표 회차의 응답을 다시 받지 않고 재사용하는 시간. 발표 회차가 열쇠에
	 * 이미 들어 있어 그 열쇠의 값은 절대 안 바뀐다 — 이 값은 그저 DB 에 얼마나 오래 들고
	 * 있을지를 정할 뿐이다.
	 */
	private Duration cacheTtl = Duration.ofDays(1);

	/**
	 * 미리 받아 둘 부산 범위와 주기. 이 범위를 정기적으로 훑어 캐시에 채워 두므로
	 * 기상청 호출량이 사용자 수와 무관하게 고정된다.
	 */
	private Prefetch prefetch = new Prefetch();

	public Prefetch getPrefetch() { return this.prefetch; }
	public void setPrefetch(Prefetch prefetch) { this.prefetch = prefetch; }

	/**
	 * 범위는 부산 장소 53,716곳의 실제 좌표를 격자로 변환해 센 값(위도 35.0103~35.3829 ·
	 * 경도 128.8017~129.2904, 서로 다른 격자 51칸)을 가장자리까지 넉넉히 잡은 것이다.
	 * 51칸 × 하루 8회 발표 = 하루 408회를 부른다.
	 *
	 * 기상청 키의 일일 한도는 아직 모른다. 넘으면 enabled 를 끄거나 범위를 줄인다.
	 */
	public static class Prefetch {

		/** 꺼 두면 지금까지와 똑같이 요청이 올 때만 기상청을 부른다. */
		private boolean enabled = true;

		private double latMin = 35.00;

		private double latMax = 35.40;

		private double lonMin = 128.78;

		private double lonMax = 129.31;

		/**
		 * 매시 정각 5분. 발표는 02·05·08·11·14·17·20·23시 10분경이지만 매시 도는 쪽이
		 * 놓치는 회차가 없고, 이미 받은 회차는 캐시에 있어 호출 없이 끝난다.
		 */
		private String cron = "0 5 * * * *";

		public boolean isEnabled() { return this.enabled; }
		public void setEnabled(boolean enabled) { this.enabled = enabled; }
		public double getLatMin() { return this.latMin; }
		public void setLatMin(double latMin) { this.latMin = latMin; }
		public double getLatMax() { return this.latMax; }
		public void setLatMax(double latMax) { this.latMax = latMax; }
		public double getLonMin() { return this.lonMin; }
		public void setLonMin(double lonMin) { this.lonMin = lonMin; }
		public double getLonMax() { return this.lonMax; }
		public void setLonMax(double lonMax) { this.lonMax = lonMax; }
		public String getCron() { return this.cron; }
		public void setCron(String cron) { this.cron = cron; }

	}

	public String getKmaServiceKey() { return this.kmaServiceKey; }
	public void setKmaServiceKey(String kmaServiceKey) { this.kmaServiceKey = kmaServiceKey; }
	public String getKmaBaseUrl() { return this.kmaBaseUrl; }
	public void setKmaBaseUrl(String kmaBaseUrl) { this.kmaBaseUrl = kmaBaseUrl; }
	public Duration getConnectTimeout() { return this.connectTimeout; }
	public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
	public Duration getReadTimeout() { return this.readTimeout; }
	public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
	public Duration getCacheTtl() { return this.cacheTtl; }
	public void setCacheTtl(Duration cacheTtl) { this.cacheTtl = cacheTtl; }
}
