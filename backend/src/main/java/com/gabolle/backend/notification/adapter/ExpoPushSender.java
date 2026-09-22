package com.gabolle.backend.notification.adapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.gabolle.backend.notification.application.PushMessage;
import com.gabolle.backend.notification.application.PushSender;
import com.gabolle.backend.notification.config.PushProperties;

/**
 * Expo 푸시 API 로 실제 발송 — S15P21E201-1391.
 *
 * <pre>
 *   POST https://exp.host/--/api/v2/push/send
 *   [ { "to": "ExponentPushToken[..]", "title": "…", "body": "…",
 *       "data": { "href": "/trips/1/itinerary" }, "channelId": "trip" }, … ]
 * </pre>
 *
 * <p>🔴 <b>{@code channelId} 를 빼면 안드로이드에서 소리도 배너도 안 난다.</b> 앱이 만들어 둔
 * 통로 이름이 {@code trip} 이고({@code frontend/src/notifications/pushToken.ts} 의
 * {@code CHANNEL_ID}), 안드로이드 8 이후로는 통로가 정해져야 알림이 뜬다. 안 보내면 기본 통로로
 * 가는데, 우리 앱은 그 통로를 만든 적이 없다.
 *
 * <p>🔴 <b>답의 순서가 요청의 순서다.</b> Expo 는 토큰을 돌려주지 않고 {@code data} 배열만 준다.
 * 그래서 몇 번째가 실패했는지로 어느 기기인지를 되짚는다 — 순서가 어긋나면 <b>멀쩡한 토큰을
 * 지운다.</b> 그래서 한 묶음을 보낸 뒤 그 묶음의 답만 보고 짝을 맞춘다.
 *
 * <p>🟢 <b>한 번도 던지지 않는다.</b> 부르는 자리는 이미 커밋이 끝난 뒤다 — 알림이 안 갔다고
 * 여행 편집을 실패로 만들 수는 없다. 실패는 전부 로그로 남긴다.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "gabolle.push", name = "enabled", havingValue = "true")
public class ExpoPushSender implements PushSender {

	private static final Logger log = LoggerFactory.getLogger(ExpoPushSender.class);

	/**
	 * 「그 기기는 이제 없다」는 Expo 의 대답. 앱을 지웠거나 알림을 껐다.
	 *
	 * <p>이 값만 토큰을 지우는 근거로 삼는다. {@code MessageRateExceeded} 같은 다른 실패에 지우면
	 * 잠깐 밀렸을 뿐인 멀쩡한 기기가 알림에서 영영 빠진다.
	 */
	static final String DEVICE_NOT_REGISTERED = "DeviceNotRegistered";

	/** 앱이 만들어 둔 안드로이드 통로 이름. 앱의 {@code CHANNEL_ID} 와 같아야 한다. */
	static final String CHANNEL_ID = "trip";

	private final RestClient restClient;

	private final PushProperties properties;

	@Autowired
	public ExpoPushSender(RestClient.Builder restClientBuilder, PushProperties properties) {
		this(restClientBuilder, properties, timeoutFactory(properties));
	}

	/** 시간 제한 자리를 갈아 끼울 수 있게 열어 둔 생성자 — 테스트 전용. */
	ExpoPushSender(RestClient.Builder restClientBuilder, PushProperties properties,
			ClientHttpRequestFactory requestFactory) {
		this.properties = properties;
		if (requestFactory != null) {
			restClientBuilder.requestFactory(requestFactory);
		}
		this.restClient = restClientBuilder.build();
	}

	private static ClientHttpRequestFactory timeoutFactory(PushProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.getConnectTimeout());
		requestFactory.setReadTimeout(properties.getReadTimeout());
		return requestFactory;
	}

	@Override
	public List<String> send(List<String> tokens, PushMessage message) {
		if (tokens == null || tokens.isEmpty()) {
			return List.of();
		}
		int batchSize = Math.max(1, this.properties.getBatchSize());
		List<String> unregistered = new ArrayList<>();
		for (int from = 0; from < tokens.size(); from += batchSize) {
			List<String> batch = tokens.subList(from, Math.min(from + batchSize, tokens.size()));
			unregistered.addAll(sendBatch(batch, message));
		}
		return unregistered;
	}

	private List<String> sendBatch(List<String> tokens, PushMessage message) {
		List<Map<String, Object>> payload = tokens.stream().map((token) -> body(token, message)).toList();
		ExpoResponse response;
		try {
			response = this.restClient.post()
					.uri(this.properties.getBaseUrl())
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON)
					.body(payload)
					.retrieve()
					.body(ExpoResponse.class);
		}
		catch (RestClientException exception) {
			// 알림 한 통이 안 간 것이다. 다시 보내지 않는다 — 늦게 도착한 「순서를 바꿨어요」는
			// 안 온 것보다 헷갈린다. 다음 변경이 다음 알림을 만든다.
			log.warn("Expo 푸시 발송에 실패했습니다. 기기 {}대 · href={}", tokens.size(), message.href(), exception);
			return List.of();
		}
		return deadTokens(tokens, response);
	}

	/**
	 * 답을 요청과 짝지어 「이제 없는 기기」만 골라낸다.
	 *
	 * <p>답이 요청보다 짧으면 짝을 믿을 수 없으므로 <b>하나도 안 지운다.</b> 잘못 지우면 멀쩡한
	 * 사람이 알림을 영영 못 받고, 그 고장은 아무 데도 안 남는다.
	 */
	private static List<String> deadTokens(List<String> tokens, ExpoResponse response) {
		List<Ticket> tickets = (response == null || response.data() == null) ? List.of() : response.data();
		if (tickets.isEmpty()) {
			return List.of();
		}
		if (tickets.size() != tokens.size()) {
			log.warn("Expo 답의 개수가 보낸 개수와 다릅니다 — 짝을 맞출 수 없어 토큰을 지우지 않습니다. 보냄={} 받음={}",
					tokens.size(), tickets.size());
			return List.of();
		}
		List<String> dead = new ArrayList<>();
		for (int index = 0; index < tickets.size(); index++) {
			Ticket ticket = tickets.get(index);
			if (ticket == null || !"error".equals(ticket.status())) {
				continue;
			}
			String reason = (ticket.details() == null) ? null : ticket.details().error();
			if (DEVICE_NOT_REGISTERED.equals(reason)) {
				dead.add(tokens.get(index));
			}
			else {
				log.warn("Expo 가 알림 한 통을 거절했습니다. reason={} message={}", reason, ticket.message());
			}
		}
		return dead;
	}

	private static Map<String, Object> body(String token, PushMessage message) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("href", message.href());
		if (message.itineraryId() != null) {
			// 티켓이 적어 둔 칸. 앱이 읽는 것은 href 지만, 나중에 쓰는 화면이 생길 수 있어 함께 싣는다.
			data.put("itineraryId", message.itineraryId());
		}

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("to", token);
		body.put("title", message.title());
		if (message.body() != null && !message.body().isBlank()) {
			body.put("body", message.body());
		}
		body.put("data", data);
		body.put("sound", "default");
		body.put("channelId", CHANNEL_ID);
		return body;
	}

	/** Expo 의 답. 우리가 보는 칸만 적는다 — 모르는 칸은 무시한다. */
	record ExpoResponse(List<Ticket> data) {
	}

	record Ticket(String status, String message, Details details) {
	}

	record Details(String error) {
	}
}
