package com.gabolle.backend.route.adapter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.gabolle.backend.route.application.RouteProviderPort;
import com.gabolle.backend.route.config.RouteProperties;
import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.domain.RouteQuery;
import com.gabolle.backend.route.domain.TravelMode;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 카카오모빌리티 길찾기(/v1/directions) 호출. 카카오모빌리티는 자동차 경로만 공개해서
 * supports 가 CAR 에만 참을 준다.
 */
@Component
@Profile({"db", "dev"})
public class KakaoMobilityRouteAdapter implements RouteProviderPort {

	private static final Logger log = LoggerFactory.getLogger(KakaoMobilityRouteAdapter.class);

	private final RestClient restClient;

	private final ObjectMapper objectMapper;

	private final RouteProperties properties;

	/**
	 * @Autowired 를 지우면 기동이 죽는다. 아래 package-private 생성자가 private 이 아니라서
	 * 스프링의 "생성자가 하나뿐" 판정에서 빠지지 않고, 둘 중 어느 것도 자동으로 못 고른다.
	 */
	@Autowired
	public KakaoMobilityRouteAdapter(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
			RouteProperties properties) {
		this(restClientBuilder, objectMapper, properties, timeoutFactory(properties));
	}

	/**
	 * 테스트 전용 생성자. requestFactory 에 null 을 주면 builder 의 요청 공장을 그대로 둔다.
	 * 공개 생성자처럼 시간 제한이 걸린 진짜 공장으로 덮으면 MockRestServiceServer 가 꽂아 둔
	 * 가짜 공장이 밀려나 진짜 카카오를 부르러 나간다.
	 */
	KakaoMobilityRouteAdapter(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
			RouteProperties properties, ClientHttpRequestFactory requestFactory) {
		this.properties = properties;
		if (requestFactory != null) {
			restClientBuilder.requestFactory(requestFactory);
		}
		this.restClient = restClientBuilder.build();
		this.objectMapper = objectMapper;
	}

	private static ClientHttpRequestFactory timeoutFactory(RouteProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.getConnectTimeout());
		requestFactory.setReadTimeout(properties.getReadTimeout());
		return requestFactory;
	}

	@Override
	public boolean supports(TravelMode mode) {
		return mode == TravelMode.CAR;
	}

	@Override
	public String providerName() {
		return RouteLeg.PROVIDER_KAKAO_MOBILITY;
	}

	@Override
	public Optional<RouteLeg> find(RouteQuery query) {
		if (!supports(query.mode())) {
			return Optional.empty();
		}
		String apiKey = this.properties.getKakaoRestApiKey();
		if (apiKey == null || apiKey.isBlank()) {
			return Optional.empty();
		}
		// 좌표 순서가 경도(x) 먼저다 — 위도를 먼저 넣으면 엉뚱한 곳의 경로가 온다.
		URI uri = UriComponentsBuilder.fromUriString(this.properties.getKakaoMobilityBaseUrl())
				.path("/v1/directions")
				.queryParam("origin", query.originLng() + "," + query.originLat())
				.queryParam("destination", query.destLng() + "," + query.destLat())
				.queryParam("priority", "RECOMMEND")
				.build()
				.toUri();
		String body;
		try {
			body = this.restClient.get().uri(uri)
					.header("Authorization", "KakaoAK " + apiKey)
					.retrieve()
					.body(String.class);
		} catch (RestClientException exception) {
			// URL 을 통째로 찍지 않는다 — 좌표가 로그에 남으면 사용자 위치가 남는다.
			log.warn("경로 조회 provider=KAKAO_MOBILITY 호출 실패 status={}", statusOf(exception));
			return Optional.empty();
		}
		try {
			return parse(this.objectMapper.readTree(body), query.mode());
		} catch (JacksonException exception) {
			log.warn("경로 조회 provider=KAKAO_MOBILITY 응답 파싱 실패", exception);
			return Optional.empty();
		}
	}

	private Optional<RouteLeg> parse(JsonNode root, TravelMode mode) {
		JsonNode routes = root.path("routes");
		if (!routes.isArray() || routes.isEmpty()) {
			return Optional.empty();
		}
		JsonNode route = routes.get(0);
		// 카카오는 길을 못 찾아도 HTTP 200 을 준다 — result_code 를 반드시 봐야 한다.
		if (route.path("result_code").asInt(-1) != 0) {
			return Optional.empty();
		}
		JsonNode summary = route.path("summary");
		int distanceM = summary.path("distance").asInt(0);
		int durationSec = summary.path("duration").asInt(0);
		// 초 → 분은 올림이 아니라 반올림, 최소 1분이다 — 0분이면 화면이 이동이 없다고 읽는다.
		int durationMin = Math.max(1, Math.round(durationSec / 60.0f));

		JsonNode fare = summary.path("fare");
		Integer taxiFareKrw = fare.has("taxi") ? fare.path("taxi").asInt() : null;
		Integer tollFareKrw = fare.has("toll") ? fare.path("toll").asInt() : null;

		List<double[]> path = new ArrayList<>();
		List<RouteLeg.Step> steps = new ArrayList<>();
		for (JsonNode section : route.path("sections")) {
			for (JsonNode road : section.path("roads")) {
				JsonNode vertexes = road.path("vertexes");
				// vertexes 는 [경도, 위도, 경도, 위도, ...] 로 납작하다 — 둘씩 끊어 짝짓는다.
				int pairCount = vertexes.size() / 2;
				for (int i = 0; i < pairCount; i++) {
					double lng = vertexes.get(i * 2).asDouble();
					double lat = vertexes.get(i * 2 + 1).asDouble();
					path.add(new double[] { lng, lat });
				}
			}
			for (JsonNode guide : section.path("guides")) {
				String guidance = guide.path("guidance").asText(null);
				if (guidance == null || guidance.isBlank()) {
					continue;
				}
				String name = guide.path("name").asText(null);
				int stepDistanceM = guide.path("distance").asInt(0);
				// 여기는 최소 1분 규칙을 적용하지 않는다 — 안내 한 줄은 실제로 0분일 수 있다.
				int stepDurationMin = Math.round(guide.path("duration").asInt(0) / 60.0f);
				steps.add(new RouteLeg.Step(name, guidance, stepDistanceM, stepDurationMin));
			}
		}

		return Optional.of(new RouteLeg(mode, distanceM, durationMin, taxiFareKrw, tollFareKrw, null, false,
				null, RouteLeg.PROVIDER_KAKAO_MOBILITY, path, steps));
	}

	private String statusOf(Exception exception) {
		if (exception instanceof RestClientResponseException responseException) {
			return String.valueOf(responseException.getStatusCode().value());
		}
		return "N/A";
	}
}
