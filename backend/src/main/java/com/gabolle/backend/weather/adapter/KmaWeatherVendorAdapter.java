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
 * 기상청 단기예보 조회서비스(getVilageFcst) 호출.
 *
 * 생성자가 둘이라 @Autowired 로 어느 것을 쓸지 명시한다 — 없으면 기동이 죽는다.
 * 서비스 키가 비면 호출하지 않고 즉시 실패를 던진다. 미리 정해 둔 값으로 성공한 척하지 않는다.
 * 실패 로그에 좌표를 남기지 않는다.
 */
@Component
@Profile({ "db", "dev" })
public class KmaWeatherVendorAdapter implements WeatherVendorPort {

	private static final Logger log = LoggerFactory.getLogger(KmaWeatherVendorAdapter.class);

	static final String PROVIDER_NAME = "KMA_VILAGE_FCST";

	/** 한 번에 받는 행 수. 하루 8개 항목 × 여러 날을 한 번에 받으려면 이만큼 필요하다. */
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

		// 인증키는 이미 URL 인코딩된 값으로 발급된다. queryParam 으로 넣으면 이중 인코딩되어
		// 키가 깨지므로 나머지 인자만 빌더로 만들고 인증키는 문자열로 이어 붙인다.
		// 그래서 toUriString() 이 붙인 앞의 물음표를 & 로 바꾼다.
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

		// 기상청 API 허브의 인증 인자 이름은 authKey 다 (공공데이터포털은 serviceKey 였다).
		URI uri = URI.create(this.properties.getKmaBaseUrl() + "/getVilageFcst?authKey=" + serviceKey + query);

		try {
			return this.restClient.get().uri(uri).retrieve().body(String.class);
		}
		catch (RestClientException exception) {
			// 좌표를 로그에 남기지 않는다 — 사용자가 어디에 있었는지가 로그에 쌓인다.
			log.warn("기상청 단기예보 호출 실패");
			throw new WeatherVendorException("WEATHER_VENDOR_UNAVAILABLE", "기상청 호출에 실패했습니다.",
					HttpStatus.BAD_GATEWAY, exception);
		}
	}
}
