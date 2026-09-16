package com.gabolle.backend.place.adapter;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 카카오 로컬 키워드 검색({@code /v2/local/search/keyword.json}) 호출 (S15P21E201-434).
 *
 * <h2>🔴 REST 키와 JavaScript 키를 헷갈리지 마라 — 같은 팀이 이미 한 번 걸렸다</h2>
 *
 * <p>{@code ref/local-route/server/src/services/kakao.ts} 의 실측(2026-08-25)이 근거다.
 * 그쪽 {@code .env} 의 {@code KAKAO_REST_API_KEY} 는 실제로는 REST API 키가 아니라 JavaScript
 * 키였다 — {@code Authorization} 헤더만 실어 {@code dapi.kakao.com} 을 부르면 기본 API 조차 401
 * {@code "KA Header is required"} 로 거부됐고, {@code KA: sdk/1.0.0 os/javascript ...
 * origin/<등록도메인>} 헤더를 함께 보내야 통과했다.
 *
 * <p>이 어댑터는 그 우회를 <b>기본으로 켜지 않는다</b> — 진짜 REST API 키를 발급받는 것이 맞다.
 * {@link OriginSearchProperties#getKakaoKaOrigin()} 에 값이 있을 때만, 그 실측을 재현해야 하는
 * 예외적인 상황에서만 {@code KA} 헤더를 붙인다.
 *
 * <p>🔴 {@code RestClient.Builder} 는 {@code AbstractRestClientOAuthProvider} 와 같은 방식으로
 * 쓴다 — 주입받은 builder 에 타임아웃이 걸린 {@code SimpleClientHttpRequestFactory} 를 꽂는다.
 * 이 builder 빈이 없어서 배포가 깨진 적이 있고 {@code RestClientBuilderContextTest} 가 그 회귀
 * 테스트다.
 */
@Component
@Profile({"db", "dev"})
public class KakaoLocalOriginSearchAdapter implements OriginSearchPort {

	private static final Logger log = LoggerFactory.getLogger(KakaoLocalOriginSearchAdapter.class);

	private final RestClient restClient;

	private final ObjectMapper objectMapper;

	private final OriginSearchProperties properties;

	public KakaoLocalOriginSearchAdapter(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
			OriginSearchProperties properties) {
		this.properties = properties;
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.getConnectTimeout());
		requestFactory.setReadTimeout(properties.getReadTimeout());
		this.restClient = restClientBuilder.requestFactory(requestFactory).build();
		this.objectMapper = objectMapper;
	}

	@Override
	public List<OriginCandidate> search(String query, int limit) {
		URI uri = uriFor(query, limit);
		try {
			String body = this.restClient.get().uri(uri)
					.headers(this::applyHeaders)
					.retrieve()
					.body(String.class);
			return parse(this.objectMapper.readTree(body));
		} catch (RestClientException | JacksonException exception) {
			// 🔴 URL 을 통째로 찍지 않는다 — 상태 코드와 provider 이름만 남긴다.
			log.warn("출발지 검색 provider=KAKAO_LOCAL 호출 실패 status={}", statusOf(exception));
			throw new IllegalStateException("카카오 로컬 검색 호출에 실패했습니다.", exception);
		}
	}

	/**
	 * 호출할 주소를 만든다 — S15P21E201-979 에서 검사할 수 있게 따로 뺐다.
	 *
	 * <p>🔴 rect 로 부산만 본다. 이것이 없던 동안 카카오에 전국을 물어봐서 "서면" 을 치면
	 * 부산 서면이 아니라 전남 순천시 서면의 장소만 나왔다 — 부산 결과는 한 건도 없었다.
	 * 지명에 "부산" 이 들어가야만 제대로 나오는 검색이었다.
	 *
	 * <p>x·y·radius 가 아니라 rect 인 이유는 radius 의 상한이 20km 라 부산이 다 안 들어가기
	 * 때문이다(동래에서 기장까지 그보다 멀다). 값은 설정에 있고 비우면 제한 없이 부른다 —
	 * 부산 밖을 다루게 되는 날 코드를 안 고치게 하려는 것이다.
	 *
	 * <p>🔴 fromHttpUrl 이 아니라 fromUriString 이다. Spring Framework 7(Boot 4)에서 앞의 것이
	 * 없어졌다 — 옛 예제를 그대로 옮기면 컴파일이 안 된다.
	 */
	URI uriFor(String query, int limit) {
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(this.properties.getKakaoBaseUrl())
				.path("/v2/local/search/keyword.json")
				.queryParam("query", query)
				.queryParam("size", limit);
		String rect = this.properties.getSearchRect();
		if (rect != null && !rect.isBlank()) {
			builder.queryParam("rect", rect);
		}
		return builder.build().encode(StandardCharsets.UTF_8).toUri();
	}

	private void applyHeaders(HttpHeaders headers) {
		headers.set("Authorization", "KakaoAK " + this.properties.getKakaoRestApiKey());
		String kaOrigin = this.properties.getKakaoKaOrigin();
		if (kaOrigin != null && !kaOrigin.isBlank()) {
			headers.set("KA", "sdk/1.0.0 os/javascript lang/ko-KR origin/" + kaOrigin);
		}
	}

	private List<OriginCandidate> parse(JsonNode root) {
		List<OriginCandidate> candidates = new ArrayList<>();
		for (JsonNode document : root.path("documents")) {
			double lat = document.path("y").asDouble(Double.NaN);
			double lng = document.path("x").asDouble(Double.NaN);
			if (Double.isNaN(lat) || Double.isNaN(lng)) {
				continue;
			}
			String address = document.path("road_address_name").asText(null);
			if (address == null || address.isBlank()) {
				address = document.path("address_name").asText(null);
			}
			candidates.add(new OriginCandidate(document.path("place_name").asText(null), address, lat, lng,
					document.path("id").asText(null), OriginCandidate.Source.KAKAO_LOCAL));
		}
		return candidates;
	}

	private String statusOf(Exception exception) {
		if (exception instanceof RestClientResponseException responseException) {
			return String.valueOf(responseException.getStatusCode().value());
		}
		return "N/A";
	}
}
