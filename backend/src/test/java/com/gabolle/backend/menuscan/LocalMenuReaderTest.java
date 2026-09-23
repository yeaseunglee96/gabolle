package com.gabolle.backend.menuscan;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.gabolle.backend.menuscan.adapter.GmsMenuReader;
import com.gabolle.backend.menuscan.adapter.GmsNameTranslator;
import com.gabolle.backend.menuscan.adapter.LocalMenuReader;
import com.gabolle.backend.menuscan.config.MenuScanProperties;
import com.gabolle.backend.menuscan.presentation.dto.MenuScanResponse;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 서버 안의 메뉴판 OCR(menu-ocr)이 준 답을 화면 칸으로 옮기는가 (S15P21E201-1538).
 *
 * <p>진짜 컨테이너 대신 같은 모양으로 답하는 가짜 서버를 띄운다. 컨테이너의 답 모양은
 * {@code backend/menu-ocr/app/ocr.py} 의 {@code Reader.read} 가 정한다.
 */
class LocalMenuReaderTest {

	private static final byte[] ANY_IMAGE = "가짜 사진 바이트".getBytes(StandardCharsets.UTF_8);

	/** 한 줄은 사전에 있어 이미 옮겨져 오고(돼지국밥), 한 줄은 사전에 없다(딴딴미엔), 한 줄은 안내문이다. */
	private static final String OCR_ANSWER = """
			{"lines":[
			  {"text":"돼지국밥 9,000","name":"돼지국밥","price":"9,000","translatedName":"Pork and Rice Soup","translationSource":"dictionary"},
			  {"text":"딴딴미엔 11.9","name":"딴딴미엔","price":"11.9","translatedName":"","translationSource":null},
			  {"text":"24시간 영업","name":"","price":"","translatedName":"","translationSource":null}
			 ],"unreadLineCount":2,"timingsMs":{"total":1900},"model":{"det":"PP-OCRv5 mobile det INT8(NNCF)"}}
			""";

	private final List<HttpServer> servers = new java.util.ArrayList<>();

	@AfterEach
	void stop() {
		this.servers.forEach(s -> s.stop(0));
	}

	private String serve(String path, int status, String body, AtomicReference<String> seenQuery,
			AtomicInteger calls) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext(path, (HttpExchange exchange) -> {
			if (seenQuery != null) {
				seenQuery.set(exchange.getRequestURI().getQuery());
			}
			if (calls != null) {
				calls.incrementAndGet();
			}
			byte[] payload = body.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, payload.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(payload);
			}
		});
		server.start();
		this.servers.add(server);
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	private static String gmsAnswer(String namesJson) {
		String content = "{\\\"names\\\":" + namesJson.replace("\"", "\\\"") + "}";
		return "{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}]}";
	}

	private LocalMenuReader reader(String localUrl, String gmsUrl) {
		MenuScanProperties properties = new MenuScanProperties();
		properties.setLocalBaseUrl(localUrl);
		properties.setBaseUrl(gmsUrl);
		properties.setApiKey("시험용 키");
		ObjectMapper mapper = new ObjectMapper();
		GmsNameTranslator translator = new GmsNameTranslator(properties, mapper, RestClient.builder());
		return new LocalMenuReader(properties, mapper, RestClient.builder(), translator);
	}

	@Test
	@DisplayName("사전에 없는 이름만 GMS 로 옮기고, 옮긴 이름에 가격을 붙여 번역 줄을 만든다")
	void fillsOnlyTheMissingTranslations() throws Exception {
		AtomicReference<String> query = new AtomicReference<>();
		AtomicInteger gmsCalls = new AtomicInteger();
		String local = serve("/v1/read", 200, OCR_ANSWER, query, null);
		String gms = serve("/chat/completions", 200, gmsAnswer("[\"Dan dan noodles\"]"), null, gmsCalls);

		GmsMenuReader.Result result = reader(local, gms).read(ANY_IMAGE, "en");

		assertThat(query.get()).isEqualTo("lang=en");
		assertThat(gmsCalls.get()).as("사전에 없는 이름이 한 번에 모여 한 번만 불린다").isEqualTo(1);
		assertThat(result.unreadLineCount()).isEqualTo(2);
		List<MenuScanResponse.Line> lines = result.lines();
		assertThat(lines).hasSize(3);
		assertThat(lines.get(0).translatedName()).isEqualTo("Pork and Rice Soup");
		assertThat(lines.get(0).translatedText()).isEqualTo("Pork and Rice Soup 9,000");
		assertThat(lines.get(1).translatedName()).isEqualTo("Dan dan noodles");
		assertThat(lines.get(1).name()).as("이름 칸은 원문 그대로 — 직원에게 가리킬 값이다").isEqualTo("딴딴미엔");
		assertThat(lines.get(2).name()).as("안내문은 음식으로 그리지 않는다").isEmpty();
		assertThat(lines.get(2).translatedText()).isEqualTo("24시간 영업");
		assertThat(lines).allSatisfy(l -> assertThat(l.allergenWords()).isEmpty());
	}

	@Test
	@DisplayName("번역이 실패해도 읽기는 성공이다 — 원문 이름을 그대로 보여 준다")
	void aFailedTranslationKeepsTheOriginalName() throws Exception {
		String local = serve("/v1/read", 200, OCR_ANSWER, null, null);
		String gms = serve("/chat/completions", 500, "{\"error\":\"아프다\"}", null, null);

		GmsMenuReader.Result result = reader(local, gms).read(ANY_IMAGE, "ja");

		MenuScanResponse.Line dandan = result.lines().get(1);
		assertThat(dandan.translatedName()).isEmpty();
		assertThat(dandan.translatedText()).isEqualTo("딴딴미엔 11.9");
	}

	@Test
	@DisplayName("모르는 언어 값은 주소에 싣지 않고 한국어로 떨어뜨린다 — 이때는 번역을 부르지 않는다")
	void anUnknownLanguageFallsBackToKorean() throws Exception {
		AtomicReference<String> query = new AtomicReference<>();
		AtomicInteger gmsCalls = new AtomicInteger();
		String local = serve("/v1/read", 200, OCR_ANSWER, query, null);
		String gms = serve("/chat/completions", 200, gmsAnswer("[\"x\"]"), null, gmsCalls);

		reader(local, gms).read(ANY_IMAGE, "fr&x=1");

		assertThat(query.get()).isEqualTo("lang=ko");
		assertThat(gmsCalls.get()).isZero();
	}

	@Test
	@DisplayName("컨테이너가 떠 있지 않으면 대체하라는 실패로 올린다")
	void anAbsentContainerAsksForTheFallback() throws Exception {
		int deadPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			deadPort = socket.getLocalPort();
		}
		LocalMenuReader reader = reader("http://127.0.0.1:" + deadPort, "http://127.0.0.1:" + deadPort);

		assertThatThrownBy(() -> reader.read(ANY_IMAGE, "en"))
				.isInstanceOf(LocalMenuReader.LocalReadFailedException.class)
				.hasMessageContaining("닿지 못했거나");
	}

	@Test
	@DisplayName("음식 줄을 하나도 못 짝지었으면 실패로 본다 — 빈 목록은 «음식이 없다»로 읽힌다")
	void noDishesIsAFailureNotAnEmptyMenu() throws Exception {
		String local = serve("/v1/read", 200,
				"{\"lines\":[{\"text\":\"영업시간 11시\",\"name\":\"\",\"price\":\"\",\"translatedName\":\"\"}],\"unreadLineCount\":0}",
				null, null);

		assertThatThrownBy(() -> reader(local, local).read(ANY_IMAGE, "en"))
				.isInstanceOf(LocalMenuReader.LocalReadFailedException.class)
				.hasMessageContaining("짝짓지 못했다");
	}

	@Test
	@DisplayName("주소가 비어 있으면 설정이 없는 것이다")
	void aBlankAddressMeansNotConfigured() {
		assertThat(reader("", "http://127.0.0.1:1").isConfigured()).isFalse();
		assertThat(reader("http://menu-ocr:8000", "http://127.0.0.1:1").isConfigured()).isTrue();
	}
}
