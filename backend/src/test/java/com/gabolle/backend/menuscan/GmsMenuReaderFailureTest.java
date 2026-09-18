package com.gabolle.backend.menuscan;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.gabolle.backend.menuscan.adapter.GmsMenuReader;
import com.gabolle.backend.menuscan.config.MenuScanProperties;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 모델 호출이 실패하면 무엇이 나가는가 — S15P21E201-1102.
 *
 * <h2>왜 시험으로 두는가</h2>
 *
 * 이 경로는 <b>운영에 키가 들어가기 전까지 한 번도 안 돌았다.</b> 키가 없으면 그 앞의
 * 설정 검사에서 끝나기 때문이다. 그래서 키를 넣은 날(2026-09-16) 처음 실행됐고, 그때
 * {@code retrieve()} 가 던지는 것을 아무도 안 받아서 <b>봉투 없는 500</b> 이 나갔다.
 * 화면에는 「사진을 읽지 못했어요」 만 보이고 무엇이 막힌 것인지는 알 수 없었다.
 *
 * 감싸는 코드는 눈에 잘 안 띄어서 다음 사람이 정리하다 지우기 쉽다. 지우면 같은 날이
 * 다시 온다 — 그때도 증상은 「그냥 500」 이라 원인을 찾는 데 또 반나절이 든다.
 *
 * <h2>무엇을 가르나</h2>
 *
 * <b>닿지 못한 것</b>과 <b>거절당한 것</b>은 사람이 할 일이 다르다. 앞은 서버가 바깥으로
 * 나가는 길을 보는 일이고, 뒤는 키와 모델 이름을 보는 일이다. 둘 다 502 로 나가지만
 * 메시지가 달라야 로그 한 줄로 갈린다.
 */
class GmsMenuReaderFailureTest {

	private static final byte[] ANY_IMAGE = "가짜 사진 바이트".getBytes(StandardCharsets.UTF_8);

	private GmsMenuReader readerPointedAt(String baseUrl) {
		MenuScanProperties properties = new MenuScanProperties();
		properties.setBaseUrl(baseUrl);
		properties.setApiKey("시험용 키");
		return new GmsMenuReader(properties, new ObjectMapper(), RestClient.builder());
	}

	@Test
	@DisplayName("모델이 거절하면 상태 코드를 메시지에 실어 올린다")
	void aRejectedCallCarriesTheStatusCode() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/chat/completions", exchange -> {
			byte[] payload = "{\"error\":\"열려 있지 않은 키\"}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(401, payload.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(payload);
			}
		});
		server.start();
		try {
			GmsMenuReader reader = readerPointedAt("http://127.0.0.1:" + server.getAddress().getPort());

			assertThatThrownBy(() -> reader.read(ANY_IMAGE, null))
					.isInstanceOf(GmsMenuReader.MenuReadFailedException.class)
					.hasMessageContaining("401");
		}
		finally {
			server.stop(0);
		}
	}

	@Test
	@DisplayName("모델에 닿지 못하면 거절과 다른 메시지로 올린다")
	void anUnreachableVendorSaysSoDifferently() throws Exception {
		int deadPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			deadPort = socket.getLocalPort();
		}
		GmsMenuReader reader = readerPointedAt("http://127.0.0.1:" + deadPort);

		assertThatThrownBy(() -> reader.read(ANY_IMAGE, null))
				.isInstanceOf(GmsMenuReader.MenuReadFailedException.class)
				.hasMessageContaining("닿지 못했다");
	}

	@Test
	@DisplayName("갈래가 예외에 실린다 — 응답 코드가 여기서 갈린다")
	void theReasonRidesOnTheException() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/chat/completions", exchange -> {
			byte[] payload = "{\"error\":\"열려 있지 않은 키\"}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(401, payload.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(payload);
			}
		});
		server.start();
		int deadPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			deadPort = socket.getLocalPort();
		}
		try {
			GmsMenuReader rejected = readerPointedAt("http://127.0.0.1:" + server.getAddress().getPort());
			GmsMenuReader unreachable = readerPointedAt("http://127.0.0.1:" + deadPort);

			assertThat(reasonOf(rejected)).isEqualTo(GmsMenuReader.MenuReadFailedException.Reason.REJECTED);
			assertThat(reasonOf(unreachable)).isEqualTo(GmsMenuReader.MenuReadFailedException.Reason.UNREACHABLE);
		}
		finally {
			server.stop(0);
		}
	}

	@Test
	@DisplayName("모델이 알아볼 수 없는 답을 주면 못 읽은 것으로 가른다 — 모델 탓과 우리 탓을 안 섞는다")
	void anUnparseableAnswerIsItsOwnReason() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/chat/completions", exchange -> {
			byte[] payload = "이건 JSON 이 아니다".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, payload.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(payload);
			}
		});
		server.start();
		try {
			GmsMenuReader reader = readerPointedAt("http://127.0.0.1:" + server.getAddress().getPort());

			assertThat(reasonOf(reader)).isEqualTo(GmsMenuReader.MenuReadFailedException.Reason.UNPARSEABLE);
		}
		finally {
			server.stop(0);
		}
	}

	private GmsMenuReader.MenuReadFailedException.Reason reasonOf(GmsMenuReader reader) {
		try {
			reader.read(ANY_IMAGE, null);
			throw new AssertionError("실패하지 않았다");
		}
		catch (GmsMenuReader.MenuReadFailedException exception) {
			return exception.reason();
		}
	}

	@Test
	@DisplayName("두 실패의 메시지가 서로 다르다 — 로그 한 줄로 갈려야 한다")
	void theTwoFailuresAreDistinguishable() throws Exception {
		int deadPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			deadPort = socket.getLocalPort();
		}
		GmsMenuReader unreachable = readerPointedAt("http://127.0.0.1:" + deadPort);

		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/chat/completions", exchange -> {
			byte[] payload = "{\"error\":\"너무 잦다\"}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(429, payload.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(payload);
			}
		});
		server.start();
		try {
			GmsMenuReader rejected = readerPointedAt("http://127.0.0.1:" + server.getAddress().getPort());

			String unreachableMessage = messageOf(unreachable);
			String rejectedMessage = messageOf(rejected);

			assertThat(unreachableMessage).isNotEqualTo(rejectedMessage);
			assertThat(rejectedMessage).contains("429");
		}
		finally {
			server.stop(0);
		}
	}

	private String messageOf(GmsMenuReader reader) {
		try {
			reader.read(ANY_IMAGE, null);
			throw new AssertionError("실패하지 않았다");
		}
		catch (GmsMenuReader.MenuReadFailedException exception) {
			return exception.getMessage();
		}
	}
	/** 모델이 이 내용을 돌려주는 가짜 중계를 띄우고, 읽은 결과를 낸다. */
	private GmsMenuReader.Result readWithModelAnswering(String modelContent) throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/chat/completions", exchange -> {
			String envelope = new ObjectMapper().writeValueAsString(java.util.Map.of(
					"choices", java.util.List.of(java.util.Map.of(
							"message", java.util.Map.of("content", modelContent)))));
			byte[] payload = envelope.getBytes(StandardCharsets.UTF_8);
			// 🔴 charset 을 안 적으면 클라이언트가 ISO-8859-1 로 읽어 한글이 깨진다.
			//    진짜 중계는 이 헤더를 준다 — 가짜도 같게 둬야 시험이 거짓말을 안 한다.
			exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
			exchange.sendResponseHeaders(200, payload.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(payload);
			}
		});
		server.start();
		try {
			return readerPointedAt("http://127.0.0.1:" + server.getAddress().getPort())
					.read(ANY_IMAGE, "en");
		}
		finally {
			server.stop(0);
		}
	}

	// ── 줄을 이름과 가격으로 나눈다 (S15P21E201-1271) ────────────────────────

	@Test
	@DisplayName("모델이 나눠 준 이름과 가격을 그대로 싣는다")
	void nameAndPriceRideThrough() throws Exception {
		GmsMenuReader.Result result = readWithModelAnswering("""
				{"lines":[{"text":"돼지국밥 9,000원","name":"돼지국밥","price":"9,000원",
				"translatedText":"Pork and rice soup 9,000 won","allergenWords":["돼지고기"]}],
				"unreadLineCount":0}
				""");

		assertThat(result.lines()).singleElement().satisfies((line) -> {
			assertThat(line.name()).isEqualTo("돼지국밥");
			assertThat(line.price()).isEqualTo("9,000원");
			assertThat(line.text()).isEqualTo("돼지국밥 9,000원");
		});
	}

	/**
	 * 🔴 <b>{@code text} 로 물러서지 않는다.</b> {@code translatedText} 는 「못 옮겼으면
	 * 원문이라도」가 성립하지만 이름은 아니다 — 못 나눈 줄의 {@code name} 에 줄 전체를 넣으면
	 * 화면이 <b>안내문을 음식으로 그리고</b>, S15P21E201-1272 가 붙으면 그 「음식」의
	 * <b>그림까지 만든다.</b> 못 나눈 것은 빈 칸으로 두는 편이 언제나 낫다.
	 */
	@Test
	@DisplayName("🔴 모델이 이름·가격을 빠뜨리면 빈 문자열이다 — 원문으로 물러서지 않는다")
	void aMissingNameDoesNotFallBackToTheWholeLine() throws Exception {
		GmsMenuReader.Result result = readWithModelAnswering("""
				{"lines":[{"text":"※ 모든 메뉴에 공깃밥이 포함됩니다",
				"translatedText":"※ All menus include a bowl of rice","allergenWords":[]}],
				"unreadLineCount":0}
				""");

		assertThat(result.lines()).singleElement().satisfies((line) -> {
			assertThat(line.name()).isEmpty();
			assertThat(line.price()).isEmpty();
			// 번역은 반대로 원문으로 물러선다 — 그쪽은 빈 칸보다 원문이 낫다
			assertThat(line.translatedText()).isEqualTo("※ All menus include a bowl of rice");
		});
	}

	@Test
	@DisplayName("이름과 가격도 길이 상한에 걸린다 — 장문을 인쇄해 토큰을 태우는 것도 공격이다")
	void nameAndPriceAreClamped() throws Exception {
		String long1 = "가".repeat(5000);
		GmsMenuReader.Result result = readWithModelAnswering(
				"{\"lines\":[{\"text\":\"메뉴\",\"name\":\"" + long1 + "\",\"price\":\"" + long1
						+ "\",\"translatedText\":\"menu\",\"allergenWords\":[]}],\"unreadLineCount\":0}");

		int max = new MenuScanProperties().getMaxLineLength();
		assertThat(result.lines()).singleElement().satisfies((line) -> {
			assertThat(line.name()).hasSize(max);
			assertThat(line.price()).hasSize(max);
		});
	}
}
