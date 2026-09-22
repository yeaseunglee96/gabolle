package com.gabolle.backend.notification.adapter;

// 어댑터와 같은 패키지에 둔다 — 시간 제한 공장을 갈아 끼우는 생성자가 패키지 안에서만 보인다.

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.gabolle.backend.notification.application.PushMessage;
import com.gabolle.backend.notification.config.PushProperties;

/**
 * Expo 푸시 API 로 나가는 모양 — S15P21E201-1391 (2/2).
 *
 * <p>누구에게 보낼지를 정하는 것은 {@code TripPushNotifierTest} 가 본다. 여기는
 * <b>나가는 요청의 모양</b>과 <b>돌아온 답을 어떻게 읽는가</b>다.
 */
class ExpoPushSenderTest {

	private static final String URL = "https://exp.host/--/api/v2/push/send";

	private static final PushMessage MESSAGE =
			new PushMessage("일정 순서가 바뀌었어요", "「부산 바다 2박 3일」 — 수민님이 순서를 바꿨어요.",
					"/trips/itn_1/itinerary", "itn_1");

	private ExpoPushSender newSender(RestClient.Builder builder, int batchSize) {
		PushProperties properties = new PushProperties();
		properties.setEnabled(true);
		properties.setBatchSize(batchSize);
		// 세 번째 인자가 null 이다 — builder 에 꽂힌 가짜 요청 공장을 덮어쓰지 않는다.
		return new ExpoPushSender(builder, properties, null);
	}

	@Test
	@DisplayName("기기 하나마다 한 덩이로 실어 보낸다 — to·title·body·data.href")
	void sendsOneEntryPerDevice() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ExpoPushSender sender = newSender(builder, 100);

		server.expect(ExpectedCount.once(), requestTo(URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$[0].to").value("ExponentPushToken[a]"))
				.andExpect(jsonPath("$[0].title").value("일정 순서가 바뀌었어요"))
				.andExpect(jsonPath("$[0].body").value("「부산 바다 2박 3일」 — 수민님이 순서를 바꿨어요."))
				.andExpect(jsonPath("$[0].data.href").value("/trips/itn_1/itinerary"))
				.andExpect(jsonPath("$[0].data.itineraryId").value("itn_1"))
				.andExpect(jsonPath("$[1].to").value("ExponentPushToken[b]"))
				.andRespond(withSuccess("{\"data\":[{\"status\":\"ok\"},{\"status\":\"ok\"}]}",
						MediaType.APPLICATION_JSON));

		List<String> gone = sender.send(List.of("ExponentPushToken[a]", "ExponentPushToken[b]"), MESSAGE);

		assertThat(gone).isEmpty();
		server.verify();
	}

	@Test
	@DisplayName("🔴 안드로이드 통로 이름을 반드시 싣는다 — 빼면 소리도 배너도 안 난다")
	void alwaysCarriesTheAndroidChannel() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ExpoPushSender sender = newSender(builder, 100);

		// 앱이 만들어 둔 통로 이름과 같아야 한다 — frontend/src/notifications/pushToken.ts 의 CHANNEL_ID.
		server.expect(ExpectedCount.once(), requestTo(URL))
				.andExpect(jsonPath("$[0].channelId").value("trip"))
				.andRespond(withSuccess("{\"data\":[{\"status\":\"ok\"}]}", MediaType.APPLICATION_JSON));

		sender.send(List.of("ExponentPushToken[a]"), MESSAGE);

		server.verify();
	}

	@Test
	@DisplayName("여행 단위 알림에는 itineraryId 칸이 아예 없다 — null 을 실어 보내지 않는다")
	void tripWideMessagesOmitTheItineraryField() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ExpoPushSender sender = newSender(builder, 100);

		server.expect(ExpectedCount.once(), requestTo(URL))
				.andExpect(jsonPath("$[0].data.href").value("/trp_1/collaborate"))
				.andExpect(jsonPath("$[0].data.itineraryId").doesNotExist())
				.andRespond(withSuccess("{\"data\":[{\"status\":\"ok\"}]}", MediaType.APPLICATION_JSON));

		sender.send(List.of("ExponentPushToken[a]"),
				new PushMessage("동행이 합류했어요", "「부산 바다 2박 3일」 — 지훈님이 함께하기로 했어요.",
						"/trp_1/collaborate", null));

		server.verify();
	}

	@Test
	@DisplayName("🔴 Expo 의 한 번 상한(100)을 넘으면 나눠 보낸다 — 넘겨 보내면 통째로 거절당한다")
	void splitsIntoBatches() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ExpoPushSender sender = newSender(builder, 2);

		server.expect(ExpectedCount.times(2), requestTo(URL))
				.andRespond(withSuccess("{\"data\":[{\"status\":\"ok\"},{\"status\":\"ok\"}]}",
						MediaType.APPLICATION_JSON));

		sender.send(List.of("ExponentPushToken[a]", "ExponentPushToken[b]",
				"ExponentPushToken[c]", "ExponentPushToken[d]"), MESSAGE);

		server.verify();
	}

	@Test
	@DisplayName("🔴 DeviceNotRegistered 로 답한 기기만 「없는 기기」로 돌려준다")
	void reportsOnlyTheDeviceExpoSaysIsGone() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ExpoPushSender sender = newSender(builder, 100);

		server.expect(ExpectedCount.once(), requestTo(URL))
				.andRespond(withSuccess("""
						{"data":[
						  {"status":"ok"},
						  {"status":"error","message":"not registered",
						   "details":{"error":"DeviceNotRegistered"}},
						  {"status":"error","message":"too many",
						   "details":{"error":"MessageRateExceeded"}}
						]}""", MediaType.APPLICATION_JSON));

		List<String> gone = sender.send(
				List.of("ExponentPushToken[a]", "ExponentPushToken[b]", "ExponentPushToken[c]"), MESSAGE);

		// 잠깐 몰린 것(MessageRateExceeded)으로 지우면 멀쩡한 사람이 알림을 영영 못 받는다.
		assertThat(gone).containsExactly("ExponentPushToken[b]");
	}

	@Test
	@DisplayName("🔴 답의 개수가 보낸 개수와 다르면 하나도 안 지운다 — 짝이 틀리면 멀쩡한 기기를 지운다")
	void aMismatchedResponseDeletesNothing() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ExpoPushSender sender = newSender(builder, 100);

		// 셋을 보냈는데 둘만 왔다. 두 번째 오류가 b 인지 c 인지 알 수 없다.
		server.expect(ExpectedCount.once(), requestTo(URL))
				.andRespond(withSuccess("""
						{"data":[
						  {"status":"ok"},
						  {"status":"error","details":{"error":"DeviceNotRegistered"}}
						]}""", MediaType.APPLICATION_JSON));

		List<String> gone = sender.send(
				List.of("ExponentPushToken[a]", "ExponentPushToken[b]", "ExponentPushToken[c]"), MESSAGE);

		assertThat(gone).isEmpty();
	}

	@Test
	@DisplayName("🔴 Expo 가 죽어도 던지지 않는다 — 부르는 자리는 이미 커밋이 끝난 뒤다")
	void aDeadVendorNeverThrows() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ExpoPushSender sender = newSender(builder, 100);

		server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withServerError());

		List<String> gone = sender.send(List.of("ExponentPushToken[a]"), MESSAGE);

		assertThat(gone).isEmpty();
		server.verify();
	}

	@Test
	@DisplayName("보낼 기기가 없으면 부르지도 않는다")
	void noDevicesMeansNoCall() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ExpoPushSender sender = newSender(builder, 100);

		// 기대를 하나도 걸지 않는다 — 그래도 요청이 나가지 않아야 통과한다.
		assertThat(sender.send(List.of(), MESSAGE)).isEmpty();

		server.verify();
	}

	@Test
	@DisplayName("한 묶음에 상한만큼 담는다 — 마지막 묶음만 짧다")
	void fillsEachBatchToTheLimit() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ExpoPushSender sender = newSender(builder, 100);

		List<String> tokens = IntStream.range(0, 101).mapToObj((i) -> "ExponentPushToken[" + i + "]").toList();
		server.expect(ExpectedCount.once(), requestTo(URL))
				.andExpect(jsonPath("$[99].to").value("ExponentPushToken[99]"))
				.andExpect(jsonPath("$[100]").doesNotExist())
				.andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
		server.expect(ExpectedCount.once(), requestTo(URL))
				.andExpect(jsonPath("$[0].to").value("ExponentPushToken[100]"))
				.andExpect(jsonPath("$[1]").doesNotExist())
				.andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

		sender.send(tokens, MESSAGE);

		server.verify();
	}
}
