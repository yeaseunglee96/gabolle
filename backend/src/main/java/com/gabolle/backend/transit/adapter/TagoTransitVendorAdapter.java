package com.gabolle.backend.transit.adapter;

import java.net.URI;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.gabolle.backend.transit.application.TransitVendorException;
import com.gabolle.backend.transit.application.TransitVendorPort;
import com.gabolle.backend.transit.config.TransitProperties;

/**
 * 국토교통부 TAGO 버스 정류소·도착정보 API 호출.
 * 서비스 키가 비어 있으면 호출을 시도하지 않고 즉시 명확한 실패를 던진다.
 * 좌표는 로그에 남기지 않는다 — 실패 로그는 상태코드만 남긴다.
 */
@Component
@Profile({ "db", "dev" })
public class TagoTransitVendorAdapter implements TransitVendorPort {

	private static final Logger log = LoggerFactory.getLogger(TagoTransitVendorAdapter.class);

	static final String PROVIDER_NAME = "TAGO";

	private final RestClient restClient;

	private final TransitProperties properties;

	/** 「덤」 정류소 전용 — 읽기 제한이 짧다(S15P21E201-1755, {@link TransitProperties#getExtraReadTimeout}). */
	private final RestClient extraClient;

	@Autowired
	public TagoTransitVendorAdapter(RestClient.Builder restClientBuilder, TransitProperties properties) {
		this(restClientBuilder, properties, timeoutFactory(properties, properties.getReadTimeout()),
				timeoutFactory(properties, properties.getExtraReadTimeout()));
	}

	/** 시간 제한 자리를 갈아 끼울 수 있게 열어 둔 생성자 — 테스트 전용. 보통 호출과 덤이 같은 자리를 쓴다. */
	TagoTransitVendorAdapter(RestClient.Builder restClientBuilder, TransitProperties properties,
			ClientHttpRequestFactory requestFactory) {
		this(restClientBuilder, properties, requestFactory, requestFactory);
	}

	TagoTransitVendorAdapter(RestClient.Builder restClientBuilder, TransitProperties properties,
			ClientHttpRequestFactory requestFactory, ClientHttpRequestFactory extraRequestFactory) {
		this.properties = properties;
		// 복제를 먼저 뜬다 — 시험이 빌더에 묶어 둔 가짜 서버(MockRestServiceServer)는 복제에도 그대로 따라간다.
		RestClient.Builder extraBuilder = restClientBuilder.clone();
		if (requestFactory != null) {
			restClientBuilder.requestFactory(requestFactory);
		}
		if (extraRequestFactory != null) {
			extraBuilder.requestFactory(extraRequestFactory);
		}
		this.restClient = restClientBuilder.build();
		this.extraClient = extraBuilder.build();
	}

	private static ClientHttpRequestFactory timeoutFactory(TransitProperties properties, Duration readTimeout) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.getConnectTimeout());
		requestFactory.setReadTimeout(readTimeout);
		return requestFactory;
	}

	@Override
	public String providerName() {
		return PROVIDER_NAME;
	}

	@Override
	public String fetchNearbyStopsJson(double lat, double lng) {
		String serviceKey = requireServiceKey();

		// 공공데이터포털 서비스 키는 이미 URL 인코딩된 값으로 발급된다 — 키는 문자열로 그대로
		// 이어 붙이고, 나머지 파라미터만 빌더로 만든다.
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

		return get(uri, "근처 정류소", this.restClient);
	}

	@Override
	public String fetchArrivalsJson(String cityCode, String nodeId) {
		return get(arrivalsUri(cityCode, nodeId), "버스 도착정보", this.restClient);
	}

	/** 덤 정류소 — 같은 호출을 짧은 읽기 제한으로(S15P21E201-1755). 늦으면 예외로 끝나고 부르는 쪽이 버린다. */
	@Override
	public String fetchExtraArrivalsJson(String cityCode, String nodeId) {
		return get(arrivalsUri(cityCode, nodeId), "버스 도착정보(덤)", this.extraClient);
	}

	private URI arrivalsUri(String cityCode, String nodeId) {
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

		return URI.create(this.properties.getBaseUrl()
				+ "/ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList?serviceKey=" + serviceKey + query);
	}

	private String requireServiceKey() {
		String serviceKey = this.properties.getServiceKey();
		if (serviceKey == null || serviceKey.isBlank()) {
			throw new TransitVendorException("TRANSIT_VENDOR_NOT_CONFIGURED", "대중교통 정보 서비스가 설정되지 않았습니다.",
					HttpStatus.BAD_GATEWAY);
		}
		return serviceKey;
	}

	private String get(URI uri, String what, RestClient client) {
		try {
			return client.get().uri(uri).retrieve().body(String.class);
		}
		catch (HttpStatusCodeException exception) {
			if (isServiceKeyRejected(exception)) {
				log.warn("{} 거절 — 이 서비스에 등록된 키가 아니다 status={}", what, exception.getStatusCode().value());
				throw new TransitVendorException("TRANSIT_VENDOR_NOT_CONFIGURED", "대중교통 정보 서비스가 설정되지 않았습니다.",
						HttpStatus.BAD_GATEWAY, exception);
			}
			log.warn("{} 호출 실패 status={}", what, exception.getStatusCode().value());
			throw new TransitVendorException("TRANSIT_VENDOR_UNAVAILABLE", what + " 호출에 실패했습니다.",
					HttpStatus.BAD_GATEWAY, exception);
		}
		catch (RestClientException exception) {
			log.warn("{} 호출 실패", what);
			throw new TransitVendorException("TRANSIT_VENDOR_UNAVAILABLE", what + " 호출에 실패했습니다.",
					HttpStatus.BAD_GATEWAY, exception);
		}
	}

	/**
	 * 업체가 우리 키를 거절한 것인가. 키가 그 서비스에 등록돼 있지 않으면 몇 번을 다시 눌러도
	 * 같은 답이 오므로, 「잠시 응답하지 않는다」로 다루면 안 된다.
	 *
	 * 공공데이터포털은 키 문제에 401 과 403 을 섞어 쓰고 그 둘이 다른 이유로도 오므로,
	 * 상태코드만으로는 모자라 본문의 {@code SERVICE_KEY_IS_NOT_REGISTERED_ERROR}·
	 * {@code SERVICE_KEY_IS_NULL} 도 같이 본다.
	 */
	private static boolean isServiceKeyRejected(HttpStatusCodeException exception) {
		int status = exception.getStatusCode().value();
		if (status != 401 && status != 403) {
			return false;
		}
		String body = exception.getResponseBodyAsString();
		return body.contains("SERVICE_KEY_IS_NOT_REGISTERED")
				|| body.contains("SERVICE_KEY_IS_NULL")
				|| body.isBlank();
	}
}
