package com.gabolle.backend.tripnaming.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.gabolle.backend.tripnaming.config.TripNamingProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 일정에 있는 장소 이름만 주고 여행 이름 후보를 받아 온다.
 *
 * <p>지어낸 장소 이름을 막는 방법은 둘이고 둘 다 한다 — 프롬프트에 일정의 장소 이름만 넣고,
 * 받은 것은 {@code PlaceWordGuard} 가 다시 검사한다. 지시만으로는 지켜졌는지 알 수 없다.
 *
 * <p>실패하면 빈 목록을 준다. 부르는 쪽이 템플릿으로 물러서면 되기 때문이고, 실패를 실패로
 * 답하는 메뉴판 읽기({@code GmsMenuReader})와 다른 점이다.
 */
@Component
public class GmsTripNamer {

	private static final String SYSTEM_PROMPT = """
			너는 여행 일정에 어울리는 짧은 이름을 짓는 도구다.

			규칙:
			1. 아래에 주는 장소 이름 말고 **다른 장소 이름을 쓰지 않는다.** 지어내면 버려진다.
			2. 한 이름은 20자 이내, 한 줄이다. 줄바꿈·따옴표·괄호를 쓰지 않는다.
			3. 사람 이름·상호·날짜를 넣지 않는다.
			4. 서로 다른 느낌으로 짓는다 — 하나는 담백하게, 하나는 분위기를 담아.

			아래 JSON 으로만 답한다. 다른 칸을 만들지 않는다.
			{"names":["...","..."]}
			""";

	private final TripNamingProperties properties;

	private final ObjectMapper objectMapper;

	private final RestClient restClient;

	public GmsTripNamer(TripNamingProperties properties, ObjectMapper objectMapper,
			RestClient.Builder restClientBuilder) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.restClient = restClientBuilder.requestFactory(timeoutRequestFactory(properties)).build();
	}

	/**
	 * 시간 제한을 반드시 건다. 중계가 멈추면 제한 없는 호출이 요청 스레드를 무한정 붙잡는다.
	 */
	private static ClientHttpRequestFactory timeoutRequestFactory(TripNamingProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.getConnectTimeout());
		requestFactory.setReadTimeout(properties.getReadTimeout());
		return requestFactory;
	}

	public boolean isConfigured() {
		return !this.properties.getApiKey().isBlank() && !this.properties.getBaseUrl().isBlank();
	}

	/**
	 * @param placeNames 이 여행의 일정에 실제로 있는 장소 이름
	 * @return 후보 이름들. 검사를 통과한 것이 아니다 — 거르는 것은 부르는 쪽이 한다.
	 *     실패하면 빈 목록
	 */
	public List<String> suggest(List<String> placeNames, int dayCount, int partySize) {
		String user = "장소: " + String.join(", ", placeNames)
				+ "\n일수: " + dayCount + "일, 인원: " + partySize + "명"
				+ "\n이름 " + this.properties.getSuggestionCount() + "개를 지어라.";

		Map<String, Object> body = Map.of(
				"model", this.properties.getModel(),
				"response_format", Map.of("type", "json_object"),
				"messages", List.of(
						Map.of("role", "system", "content", SYSTEM_PROMPT),
						Map.of("role", "user", "content", user)));

		try {
			String raw = this.restClient.post()
					.uri(this.properties.getBaseUrl() + "/chat/completions")
					.header("Authorization", "Bearer " + this.properties.getApiKey())
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(String.class);
			return parse(raw);
		}
		catch (RuntimeException exception) {
			// 이름을 못 지은 것은 사람을 다치게 하지 않는다. 부르는 쪽이 템플릿으로 간다.
			return List.of();
		}
	}

	/** {@code names} 말고는 아무것도 읽지 않는다. 모델이 무엇을 더 보내든 여기서 사라진다. */
	private List<String> parse(String raw) {
		JsonNode root = this.objectMapper.readTree(raw);
		String content = root.path("choices").path(0).path("message").path("content").asString();
		JsonNode parsed = this.objectMapper.readTree(content);

		List<String> names = new ArrayList<>();
		for (JsonNode name : parsed.path("names")) {
			String value = name.asString("").trim();
			if (!value.isBlank() && names.size() < this.properties.getSuggestionCount()) {
				names.add(value);
			}
		}
		return List.copyOf(names);
	}
}
