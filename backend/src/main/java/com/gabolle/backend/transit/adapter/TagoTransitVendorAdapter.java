package com.gabolle.backend.transit.adapter;

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

import com.gabolle.backend.transit.application.TransitVendorException;
import com.gabolle.backend.transit.application.TransitVendorPort;
import com.gabolle.backend.transit.config.TransitProperties;

/**
 * 국토교통부 TAGO 버스 정류소·도착정보 API 호출 — S15P21E201-988.
 *
 * <p>{@code RestClient.Builder}는 {@code KmaWeatherVendorAdapter}와 같은 방식으로 쓴다 —
 * 주입받은 builder에 시간 제한이 걸린 {@code SimpleClientHttpRequestFactory}를 꽂는다.
 *
 * <p>🔴 <b>서비스 키가 비어 있으면 호출을 시도하지 않고 즉시 명확한 실패</b>를 던진다 —
 * {@code TranslationVendorAdapter}·{@code KmaWeatherVendorAdapter}와 같은 이유.
 *
 * <p>🔴 <b>좌표를 로그에 남기지 않는다.</b> 실패 로그는 상태코드만 남긴다.
 */
@Component
@Profile({ "db", "dev" })
public class TagoTransitVendorAdapter implements TransitVendorPort {

	private static final Logger log = LoggerFactory.getLogger(TagoTransitVendorAdapter.class);

	static final String PROVIDER_NAME = "TAGO";

	private final RestClient restClient;

	private final TransitProperties properties;

	@Autowired
	public TagoTransitVendorAdapter(RestClient.Builder restClientBuilder, TransitProperties properties) {
		this(restClientBuilder, properties, timeoutFactory(properties));
	}

	/** 시간 제한 자리를 갈아 끼울 수 있게 열어 둔 생성자 — 테스트 전용. */
	TagoTransitVendorAdapter(RestClient.Builder restClientBuilder, TransitProperties properties,
			ClientHttpRequestFactory requestFactory) {
		this.properties = properties;
		if (requestFactory != null) {
			restClientBuilder.requestFactory(requestFactory);
		}
		this.restClient = restClientBuilder.build();
	}

	private static ClientHttpRequestFactory timeoutFactory(TransitProperties properties) {
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
	public String fetchNearbyStopsJson(double lat, double lng) {
		String serviceKey = requireServiceKey();

		// 🔴 공공데이터포털 서비스 키는 이미 URL 인코딩된 값으로 발급된다 — KmaWeatherVendorAdapter
		//    와 같은 이유로 키는 문자열로 그대로 이어 붙이고, 나머지 파라미터만 빌더로 만든다.
		String query = UriComponentsBuilder.newInstance()
				.queryParam("gpsLati", lat)
				.queryParam("gpsLong", lng)
				.queryParam("numOfRows", 10)
				.queryParam("pageNo", 1)
				.queryParam("_type", "json")
				.build()
				.toUriString()
				.replaceFirst("^\\?", "&");

		URI uri = URI.create(this.properties.getBaseUrl()
				+ "/BusSttnInfoInqireService/getCrdntPrxmtSttnList?serviceKey=" + serviceKey + query);

		return get(uri, "근처 정류소");
	}

	@Override
	public String fetchArrivalsJson(String cityCode, String nodeId) {
		String serviceKey = requireServiceKey();

		String query = UriComponentsBuilder.newInstance()
				.queryParam("cityCode", cityCode)
				.queryParam("nodeId", nodeId)
				.queryParam("numOfRows", 20)
				.queryParam("pageNo", 1)
				.queryParam("_type", "json")
				.build()
				.toUriString()
				.replaceFirst("^\\?", "&");

		URI uri = URI.create(this.properties.getBaseUrl()
				+ "/ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList?serviceKey=" + serviceKey + query);

		return get(uri, "버스 도착정보");
	}

	private String requireServiceKey() {
		String serviceKey = this.properties.getServiceKey();
		if (serviceKey == null || serviceKey.isBlank()) {
			throw new TransitVendorException("TRANSIT_VENDOR_NOT_CONFIGURED", "대중교통 정보 서비스가 설정되지 않았습니다.",
					HttpStatus.BAD_GATEWAY);
		}
		return serviceKey;
	}

	private String get(URI uri, String what) {
		try {
			return this.restClient.get().uri(uri).retrieve().body(String.class);
		}
		catch (RestClientException exception) {
			log.warn("{} 호출 실패", what);
			throw new TransitVendorException("TRANSIT_VENDOR_UNAVAILABLE", what + " 호출에 실패했습니다.",
					HttpStatus.BAD_GATEWAY, exception);
		}
	}
}
