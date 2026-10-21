package com.gabolle.backend.facility.adapter;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.gabolle.backend.facility.service.ExchangeSearch;
import com.gabolle.backend.place.adapter.OriginSearchProperties;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 카카오 로컬 키워드 검색으로 근처 환전소를 찾는다 (S15P21E201-2066).
 *
 * <p>「환전소」로 찾고 카카오 분류가 {@code 외환,환율} 인 곳만 남긴다 — 「환전」 하나로 찾으면 금은방이 섞여 나온다(2026-10-20 남포동 실측).
 * 키·주소·타임아웃은 출발지 검색({@link OriginSearchProperties})과 같은 카카오 키를 쓴다.
 *
 * <p>카카오는 한 쪽에 15곳, 세 쪽(45곳)까지만 준다. 그래서 필요한 만큼만 쪽을 넘긴다.
 */
@Component
// 키·주소·타임아웃 묶음을 여기서도 켠다 — 편의시설만 띄우는 시험 슬라이스(FacilitySliceApplication)에는 출발지 검색 설정이 없다.
@EnableConfigurationProperties(OriginSearchProperties.class)
@Profile({ "db", "dev" })
public class KakaoExchangeSearchAdapter implements ExchangeSearch {

	private static final Logger log = LoggerFactory.getLogger(KakaoExchangeSearchAdapter.class);

	static final String QUERY = "환전소";

	static final String CATEGORY = "외환,환율";

	private static final int PAGE_SIZE = 15;

	private static final int MAX_PAGES = 3;

	private final RestClient restClient;

	private final ObjectMapper objectMapper;

	private final OriginSearchProperties properties;

	public KakaoExchangeSearchAdapter(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
			OriginSearchProperties properties) {
		this.properties = properties;
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.getConnectTimeout());
		requestFactory.setReadTimeout(properties.getReadTimeout());
		this.restClient = restClientBuilder.requestFactory(requestFactory).build();
		this.objectMapper = objectMapper;
	}

	@Override
	public List<Spot> search(double lat, double lng, int radiusM, int limit) {
		Map<String, Spot> found = new LinkedHashMap<>();
		for (int page = 1; page <= MAX_PAGES && found.size() < limit; page++) {
			JsonNode root = call(uriFor(lat, lng, radiusM, page));
			found.putAll(parse(root));
			if (root.path("meta").path("is_end").asBoolean(true)) {
				break;
			}
		}
		return found.values().stream().limit(limit).toList();
	}

	URI uriFor(double lat, double lng, int radiusM, int page) {
		return UriComponentsBuilder.fromUriString(this.properties.getKakaoBaseUrl())
				.path("/v2/local/search/keyword.json")
				.queryParam("query", QUERY)
				.queryParam("x", lng)
				.queryParam("y", lat)
				.queryParam("radius", radiusM)
				.queryParam("sort", "distance")
				.queryParam("size", PAGE_SIZE)
				.queryParam("page", page)
				.build().encode(StandardCharsets.UTF_8).toUri();
	}

	private JsonNode call(URI uri) {
		try {
			String body = this.restClient.get().uri(uri).headers(this::applyHeaders).retrieve().body(String.class);
			return this.objectMapper.readTree(body);
		} catch (RestClientException | JacksonException exception) {
			// URL 을 통째로 찍지 않는다 — 상태 코드와 provider 이름만 남긴다.
			log.warn("환전소 검색 provider=KAKAO_LOCAL 호출 실패 status={}", statusOf(exception));
			throw new IllegalStateException("카카오 로컬 검색 호출에 실패했습니다.", exception);
		}
	}

	private void applyHeaders(HttpHeaders headers) {
		headers.set("Authorization", "KakaoAK " + this.properties.getKakaoRestApiKey());
		String kaOrigin = this.properties.getKakaoKaOrigin();
		if (kaOrigin != null && !kaOrigin.isBlank()) {
			headers.set("KA", "sdk/1.0.0 os/javascript lang/ko-KR origin/" + kaOrigin);
		}
	}

	static Map<String, Spot> parse(JsonNode root) {
		Map<String, Spot> spots = new LinkedHashMap<>();
		for (JsonNode document : root.path("documents")) {
			String category = document.path("category_name").asText("");
			if (!category.contains(CATEGORY)) {
				continue;
			}
			double lat = document.path("y").asDouble(Double.NaN);
			double lng = document.path("x").asDouble(Double.NaN);
			String id = document.path("id").asText(null);
			if (Double.isNaN(lat) || Double.isNaN(lng) || id == null) {
				continue;
			}
			String address = blankToNull(document.path("road_address_name").asText(null));
			if (address == null) {
				address = blankToNull(document.path("address_name").asText(null));
			}
			spots.put(id, new Spot(id, document.path("place_name").asText(""), address, lat, lng,
					blankToNull(document.path("phone").asText(null)), blankToNull(document.path("place_url").asText(null)),
					document.path("distance").asInt(0)));
		}
		return spots;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static String statusOf(Exception exception) {
		if (exception instanceof RestClientResponseException responseException) {
			return String.valueOf(responseException.getStatusCode().value());
		}
		return "N/A";
	}

	/** 시험용 — 같은 꾸러미에서만 쓴다. */
	static List<Spot> spotsOf(JsonNode root) {
		return new ArrayList<>(parse(root).values());
	}
}
