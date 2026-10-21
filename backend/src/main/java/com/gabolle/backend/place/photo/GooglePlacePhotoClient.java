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
 *
 * <p>몇 번째 사진을 쓸지는 부르는 쪽이 정한다(S15P21E201-2059). 그 장소의 사진이 그 번호까지 없으면
 * 첫 번째로 물러선다.
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

	/**
	 * @param photoIndex 그 장소의 사진 중 몇 번째인가(0 부터). 그 번호까지 사진이 없으면 첫 번째를 쓴다
	 */
	@SuppressWarnings("unchecked")
	public Optional<String> photoUri(String googlePlaceId, int photoIndex) {
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
			Optional<String> name = photoNameAt(googlePlaceId, photos, photoIndex);
			if (name.isEmpty()) {
				return Optional.empty();
			}
			Map<String, Object> media = this.restClient.get()
					.uri("/" + name.get() + "/media?maxWidthPx={w}&skipHttpRedirect=true", this.properties.getMaxWidthPx())
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

	/**
	 * 받은 사진 목록에서 쓸 사진 <b>하나</b>를 고르고 그 이름을 돌려준다. {@code photoIndex} 번째가 있으면
	 * 그것을, 없으면(그 장소에 Google 이 줄 사진이 그만큼 없다) 첫 번째를 쓴다 — 번호를 크게 골랐다고 사진이
	 * 통째로 사라지면 안 된다.
	 *
	 * <p>고른 사진은 목록의 원소 하나다. 사진에 딸린 다른 값(저작자 표시 {@code authorAttributions} 등)을
	 * 나중에 쓰게 되더라도 반드시 <b>이 원소에서</b> 꺼내야 한다 — 이름은 N 번째에서, 저작자는 첫 번째에서
	 * 꺼내면 사진과 저작자가 어긋난다. 지금은 이름 말고는 아무것도 쓰지 않는다.
	 */
	static Optional<String> photoNameAt(String googlePlaceId, Object photos, int photoIndex) {
		if (!(photos instanceof List<?> list) || list.isEmpty()) {
			return Optional.empty();
		}
		if (photoIndex > 0 && photoIndex < list.size()) {
			Optional<String> chosen = nameOf(list.get(photoIndex));
			if (chosen.isPresent()) {
				return chosen;
			}
		}
		if (photoIndex > 0) {
			// 운영자가 고른 번호가 안 먹힌 까닭을 로그 한 줄로 알 수 있게 한다. 캐시 덕에 같은 장소는 30분에 한 번만 난다.
			log.info("Google 장소 사진 {}장 중 &photo={} 를 쓸 수 없어 첫 사진을 쓴다 place={}",
					list.size(), photoIndex, googlePlaceId);
		}
		return nameOf(list.get(0));
	}

	private static Optional<String> nameOf(Object photo) {
		return (photo instanceof Map<?, ?> map && map.get("name") instanceof String name && !name.isBlank())
				? Optional.of(name)
				: Optional.empty();
	}
}
