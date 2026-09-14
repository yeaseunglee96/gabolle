package com.gabolle.backend.weather.adapter;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.gabolle.backend.weather.application.WeatherVendorException;
import com.gabolle.backend.weather.application.WeatherVendorPort;
import com.gabolle.backend.weather.config.WeatherProperties;
import com.gabolle.backend.weather.domain.KmaBaseTime;

/**
 * 기상청 단기예보 조회서비스({@code getVilageFcst}) 호출 — S15P21E201-366.
 *
 * <p>{@code RestClient.Builder} 는 {@code TranslationVendorAdapter}·{@code
 * KakaoMobilityRouteAdapter} 와 같은 방식으로 쓴다 — 주입받은 builder 에 시간 제한이 걸린
 * {@code SimpleClientHttpRequestFactory} 를 꽂는다. 생성자가 둘이라 {@code @Autowired} 로
 * 어느 것을 쓸지 명시한다(둘의 실측 참고 — Spring 7 에서 생성자가 둘이면 그것 없이는
 * 기동이 죽는다).
 *
 * <p>🔴 <b>서비스 키가 비어 있으면 호출을 시도하지 않고 즉시 명확한 실패</b>를 던진다.
 * {@code TranslationVendorAdapter} 와 같은 이유 — 미리 정해 둔 값을 돌려주며 성공한 척하는
 * 것은 이 티켓이 명시적으로 금지한 바로 그 버그다.
 *
 * <p>🔴 <b>좌표를 로그에 남기지 않는다.</b> {@code KakaoMobilityRouteAdapter} 와 같은 이유로
 * 실패 로그는 상태코드만 남긴다.
 */
@Component
@Profile({ "db", "dev" })
public class KmaWeatherVendorAdapter implements WeatherVendorPort {

	private static final Logger log = LoggerFactory.getLogger(KmaWeatherVendorAdapter.class);

	static final String PROVIDER_NAME = "KMA_VILAGE_FCST";

	/** 기상청이 한 번에 최대 넉넉히 줄 수 있는 행 수 — 하루 8개 항목 × 여러 날을 다 받기 위함. */
	private static final int NUM_OF_ROWS = 1000;

	private final RestClient restClient;

	private final WeatherProperties properties;

	@Autowired
	public KmaWeatherVendorAdapter(RestClient.Builder restClientBuilder, WeatherProperties properties) {
		this(restClientBuilder, properties, timeoutFactory(properties));
	}

	/** 시간 제한 자리를 갈아 끼울 수 있게 열어 둔 생성자 — 테스트 전용. */
	KmaWeatherVendorAdapter(RestClient.Builder restClientBuilder, WeatherProperties properties,
			ClientHttpRequestFactory requestFactory) {
		this.properties = properties;
		if (requestFactory != null) {
			restClientBuilder.requestFactory(requestFactory);
		}
		this.restClient = restClientBuilder.build();
	}

	private static ClientHttpRequestFactory timeoutFactory(WeatherProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.getConnectTimeout());
		requestFactory.setReadTimeout(properties.getReadTimeout());
		return requestFactory;
	}

	@Override
	public String providerName() {
		return PROVIDER_NAME;
	}

	@Override
	public String fetchForecastJson(int nx, int ny, KmaBaseTime baseTime) {
		String serviceKey = this.properties.getKmaServiceKey();
		if (serviceKey == null || serviceKey.isBlank()) {
			throw new WeatherVendorException("WEATHER_VENDOR_NOT_CONFIGURED", "기상청 서비스 키가 설정되지 않았습니다.",
					HttpStatus.BAD_GATEWAY);
		}

		// 🔴 공공데이터포털 서비스 키는 이미 URL 인코딩된 값으로 발급된다. UriComponentsBuilder
		//    의 queryParam 으로 넣으면 다시 인코딩되어(이중 인코딩) 키가 깨진다 — 그래서 base
		//    URL 과 나머지 파라미터만 빌더로 만들고, 서비스 키는 문자열로 그대로 이어 붙인다.
		// toUriString() 이 만드는 "?a=1&b=2" 앞의 물음표를 & 로 바꿔, serviceKey 뒤에 그대로
		// 이어 붙일 수 있게 한다 — 물음표가 두 번 나오면 안 되기 때문이다.
		String query = UriComponentsBuilder.newInstance()
				.queryParam("numOfRows", NUM_OF_ROWS)
				.queryParam("pageNo", 1)
				.queryParam("dataType", "JSON")
				.queryParam("base_date", baseTime.baseDateParam())
				.queryParam("base_time", baseTime.baseTimeParam())
				.queryParam("nx", nx)
				.queryParam("ny", ny)
				.build()
				.toUriString()
				.replaceFirst("^\\?", "&");

		URI uri = URI.create(this.properties.getKmaBaseUrl() + "/getVilageFcst?serviceKey=" + serviceKey + query);

		try {
			return this.restClient.get().uri(uri).retrieve().body(String.class);
		}
		catch (RestClientException exception) {
			// 🔴 좌표를 로그에 남기지 않는다 — 사용자가 어디에 있었는지가 로그에 쌓인다.
			log.warn("기상청 단기예보 호출 실패");
			throw new WeatherVendorException("WEATHER_VENDOR_UNAVAILABLE", "기상청 호출에 실패했습니다.",
					HttpStatus.BAD_GATEWAY, exception);
		}
	}
}
