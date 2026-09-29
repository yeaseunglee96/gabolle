package com.gabolle.backend.place.photo;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Google 장소 번호로 지금 쓸 수 있는 사진 주소 하나를 받아 온다(S15P21E201-1832).
 *
 * <p>두 번 부른다 — 장소 상세에서 사진 이름을 받고, 그 이름으로 사진 주소를 받는다. 사진 이름도
 * 사진 주소도 시간이 지나면 만료되므로 둘 다 저장하지 않는다(Google 약관이 장소 번호 말고는
 * 저장을 금한다). 실패는 전부 비어 있음으로 돌려준다 — 사진 한 장 때문에 화면이 오류가 되면 안 된다.
 */
public class GooglePlacePhotoClient {

	private static final Logger log = LoggerFactory.getLogger(GooglePlacePhotoClient.class);

	private final RestClient restClient;

	private final GooglePlacePhotoProperties properties;

	public GooglePlacePhotoClient(RestClient.Builder builder, GooglePlacePhotoProperties properties) {
		this.properties = properties;
		HttpClient http = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
		factory.setReadTimeout(properties.getReadTimeout());
		this.restClient = builder.clone().baseUrl(properties.getBaseUrl()).requestFactory(factory).build();
	}

	public boolean configured() {
		return !this.properties.getApiKey().isBlank();
	}

	@SuppressWarnings("unchecked")
	public Optional<String> photoUri(String googlePlaceId) {
		if (!configured()) {
			return Optional.empty();
		}
		try {
			Map<String, Object> place = this.restClient.get()
					.uri("/places/{id}", googlePlaceId)
					.header("X-Goog-Api-Key", this.properties.getApiKey())
					.header("X-Goog-FieldMask", "photos")
					.retrieve()
					.body(Map.class);
			Object photos = (place == null) ? null : place.get("photos");
			if (!(photos instanceof List<?> list) || list.isEmpty() || !(list.get(0) instanceof Map<?, ?> first)
					|| !(first.get("name") instanceof String name)) {
				return Optional.empty();
			}
			Map<String, Object> media = this.restClient.get()
					.uri("/" + name + "/media?maxWidthPx={w}&skipHttpRedirect=true", this.properties.getMaxWidthPx())
					.header("X-Goog-Api-Key", this.properties.getApiKey())
					.retrieve()
					.body(Map.class);
			Object uri = (media == null) ? null : media.get("photoUri");
			return (uri instanceof String s && s.startsWith("https://")) ? Optional.of(s) : Optional.empty();
		}
		catch (RestClientException ex) {
			// 키 값이 로그에 남지 않게 메시지만 적는다.
			log.warn("Google 장소 사진을 못 받았다 place={} cause={}", googlePlaceId, ex.getClass().getSimpleName());
			return Optional.empty();
		}
	}
}
