package com.gabolle.backend.weather.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 기상청 단기예보 조회 설정 — S15P21E201-366.
 *
 * <p>🔴 <b>필드 기본값만으로 기동해야 한다.</b> 서비스 키가 비어 있으면 호출 자체가 명확한
 * 실패({@code WeatherVendorException})로 올라갈 뿐, 기동이 실패하면 안 된다 —
 * {@code TranslateProperties}·{@code RouteProperties} 가 같은 이유로 같은 규칙을 지킨다.
 */
@ConfigurationProperties(prefix = "gabolle.weather")
public class WeatherProperties {

	/** 기상청 API 허브가 발급하는 인증키. 비어 있으면 호출이 즉시 명확한 실패로 끝난다. */
	private String kmaServiceKey = "";

	/**
	 * 🔴 <b>2026-09-16 에 공공데이터포털에서 기상청 API 허브로 옮겼다 — S15P21E201-1065.</b>
	 *
	 * <p>같은 이름의 같은 API 다. 인자({@code numOfRows}·{@code pageNo}·{@code dataType}·
	 * {@code base_date}·{@code base_time}·{@code nx}·{@code ny})도 응답 모양도 같아서
	 * 파서·캐시·미리받기는 한 줄도 안 고쳤다. <b>다른 것은 주소와 인증 인자 이름 둘뿐이다</b>
	 * ({@code serviceKey} → {@code authKey}, {@code KmaWeatherVendorAdapter} 참고).
	 *
	 * <p><b>왜 옮겼나.</b> 공공데이터포털은 서비스마다 활용신청을 따로 눌러야 하는데 단기예보
	 * 조회서비스가 신청돼 있지 않아 모든 호출이 {@code SERVICE_KEY_IS_NOT_REGISTERED_ERROR}
	 * 로 거절됐다. 그래서 부산 격자 99칸이 <b>매시간 전부 실패</b>했고, 미리 받아 둔 것만 읽는
	 * 비로그인 사용자에게는 홈 날씨가 통째로 비어 있었다.
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
	 * 미리 받아 둘 부산 범위와 주기 — S15P21E201-993.
	 *
	 * <p>🔴 <b>이 값들이 이 기능의 전부다.</b> 요청이 올 때 기상청을 부르는 대신, 이 범위를
	 * 정기적으로 훑어 캐시 표에 채워 둔다. 그러면 기상청 호출량이 <b>사용자 수와 무관하게
	 * 고정</b>되고, 로그인 문턱의 이유(우리 키로 남이 호출을 돌리는 것을 막는다 —
	 * {@code WeatherController} 주석)가 사라진다.
	 */
	private Prefetch prefetch = new Prefetch();

	public Prefetch getPrefetch() { return this.prefetch; }
	public void setPrefetch(Prefetch prefetch) { this.prefetch = prefetch; }

	/**
	 * 🔴 <b>범위 숫자는 지어낸 것이 아니라 잰 것이다 (2026-09-15).</b>
	 *
	 * <p>부산 장소 <b>53,716곳</b>의 실제 좌표({@code survey-recommend/places.json})를
	 * {@code KmaGridConverter} 로 변환해 직접 셌다.
	 *
	 * <pre>
	 * 좌표 범위 : 위도 35.0103~35.3829 · 경도 128.8017~129.2904
	 * 격자      : 서로 다른 칸 51개 (5km 간격)
	 * 호출량    : 51칸 × 하루 8회 발표 = 하루 408회
	 * </pre>
	 *
	 * <p>여기 적힌 값은 그 범위를 조금 넉넉히 잡은 것이다 — 장소가 없는 가장자리 격자까지
	 * 덮어야 사용자가 부산 어디를 찍어도 빈칸이 안 나온다.
	 *
	 * <p>🔴 <b>기상청 키의 일일 한도는 아직 모른다.</b> 하루 408회가 그 안인지 확인되지
	 * 않았다. 넘으면 {@code enabled} 를 끄거나 범위를 줄인다 — 코드를 고칠 필요는 없다.
	 */
	public static class Prefetch {

		/** 꺼 두면 지금까지와 똑같이 요청이 올 때만 기상청을 부른다. */
		private boolean enabled = true;

		private double latMin = 35.00;

		private double latMax = 35.40;

		private double lonMin = 128.78;

		private double lonMax = 129.31;

		/**
		 * 기본값은 매시 정각 5분이다. 단기예보 발표는 02·05·08·11·14·17·20·23시 10분경이라,
		 * 매시 도는 쪽이 발표 시각표를 코드에 박는 것보다 단순하고 놓치는 회차가 없다 —
		 * 이미 받은 회차는 캐시에 있으므로 그 시간의 훑기는 호출 없이 끝난다.
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
