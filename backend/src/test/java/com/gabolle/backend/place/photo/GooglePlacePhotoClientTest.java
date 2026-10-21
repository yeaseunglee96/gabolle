package com.gabolle.backend.place.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.Place.PhotoLicense;
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * S15P21E201-2059 — 운영자가 고른 번호의 사진을 받고, 그 번호까지 사진이 없으면 첫 사진으로 물러선다.
 *
 * <p>진짜 Google 을 부르지 않고(과금·키 문제) 로컬에 가짜 Places API 를 띄운다. 이 클라이언트는 자기
 * 요청 공장을 직접 만들어 쓰므로 {@code MockRestServiceServer} 가 안 붙는다 — 실제 HTTP 로 확인한다.
 */
class GooglePlacePhotoClientTest {

	private static final String GOOGLE_ID = "ChIJfYLMelSTaDURFlCRfk5W1PA";

	private final GooglePlacePhotoProperties properties = new GooglePlacePhotoProperties();

	/** 가짜 서버가 받은 요청의 경로(쿼리 제외), 받은 순서대로. */
	private final List<String> paths = new CopyOnWriteArrayList<>();

	/** 장소 상세 요청에 실려 온 {@code X-Goog-FieldMask}. 비용 구간이 갈리므로 바뀌면 안 된다. */
	private final List<String> fieldMasks = new CopyOnWriteArrayList<>();

	private HttpServer server;

	private int detailsStatus = 200;

	private String detailsBody = photosJson(3);

	@BeforeEach
	void startFakeGoogle() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext("/v1/places/", (exchange) -> {
			String path = exchange.getRequestURI().getRawPath();
			this.paths.add(path);
			if (path.endsWith("/media")) {
				// 사진 이름의 끝(P2 등)을 그대로 사진 주소에 실어, 어느 사진을 물었는지 응답만 봐도 알게 한다.
				String photo = path.substring(path.indexOf("/photos/") + "/photos/".length(), path.length() - "/media".length());
				respond(exchange, 200, "{\"name\":\"x/media\",\"photoUri\":\"https://lh3.googleusercontent.com/p/" + photo + "\"}");
			}
			else {
				this.fieldMasks.add(String.valueOf(exchange.getRequestHeaders().getFirst("X-Goog-FieldMask")));
				respond(exchange, this.detailsStatus, this.detailsBody);
			}
		});
		this.server.start();
		// 키는 요청 헤더에 실린다 — 헤더 값에는 ASCII 만 쓴다.
		this.properties.setApiKey("test-key");
		this.properties.setBaseUrl("http://127.0.0.1:" + this.server.getAddress().getPort() + "/v1");
	}

	@AfterEach
	void stopFakeGoogle() {
		this.server.stop(0);
	}

	// ---- 번호대로 고른다 ----

	@Test
	void 번호가_0이면_첫_사진을_받는다() {
		assertThat(client().photoUri(GOOGLE_ID, 0)).contains("https://lh3.googleusercontent.com/p/P0");
		assertThat(mediaPaths()).containsExactly("/v1/places/" + GOOGLE_ID + "/photos/P0/media");
	}

	@Test
	void 번호가_2면_세_번째_사진의_이름으로_사진_주소를_받는다() {
		this.detailsBody = photosJson(4);

		assertThat(client().photoUri(GOOGLE_ID, 2)).contains("https://lh3.googleusercontent.com/p/P2");
		assertThat(mediaPaths()).containsExactly("/v1/places/" + GOOGLE_ID + "/photos/P2/media");
	}

	@Test
	void 마지막_사진_번호도_고를_수_있다() {
		this.detailsBody = photosJson(10);

		assertThat(client().photoUri(GOOGLE_ID, 9)).contains("https://lh3.googleusercontent.com/p/P9");
	}

	// ---- 번호까지 사진이 없으면 첫 사진 — 실패로 만들지 않는다 ----

	@Test
	void 사진이_번호보다_적으면_실패하지_않고_첫_사진을_받는다() {
		this.detailsBody = photosJson(2);

		assertThat(client().photoUri(GOOGLE_ID, 5)).contains("https://lh3.googleusercontent.com/p/P0");
		assertThat(mediaPaths()).containsExactly("/v1/places/" + GOOGLE_ID + "/photos/P0/media");
	}

	@Test
	void 사진이_세_장이면_번호_3은_없으므로_첫_사진을_받는다() {
		this.detailsBody = photosJson(3);

		// 사진 셋의 번호는 0, 1, 2 다 — 경계 바로 밖인 3 도 실패가 아니다.
		assertThat(client().photoUri(GOOGLE_ID, 3)).contains("https://lh3.googleusercontent.com/p/P0");
	}

	@Test
	void 고른_번호의_사진에_이름이_없으면_첫_사진으로_물러선다() {
		this.detailsBody = "{\"photos\":[{\"name\":\"places/" + GOOGLE_ID + "/photos/P0\"},{\"widthPx\":10},"
				+ "{\"name\":\"places/" + GOOGLE_ID + "/photos/P2\"}]}";
		GooglePlacePhotoClient client = client();

		// 1 번은 이름이 없어 쓸 수 없다. 바로 다음 사진(P2)으로 건너뛰지 않고 첫 사진으로 간다.
		assertThat(client.photoUri(GOOGLE_ID, 1)).contains("https://lh3.googleusercontent.com/p/P0");
		// 같은 목록에서 이름이 멀쩡한 2 번은 그대로 받는다.
		assertThat(client.photoUri(GOOGLE_ID, 2)).contains("https://lh3.googleusercontent.com/p/P2");
	}

	@Test
	void 사진이_한_장뿐인_장소는_어떤_번호여도_그_사진이다() {
		this.detailsBody = photosJson(1);
		GooglePlacePhotoClient client = client();

		for (int index = 0; index <= GooglePlacePhotoLink.MAX_PHOTO_INDEX; index++) {
			assertThat(client.photoUri(GOOGLE_ID, index)).as("번호 %d", index)
					.contains("https://lh3.googleusercontent.com/p/P0");
		}
	}

	// ---- 예전 동작 그대로 ----

	@Test
	void 사진이_하나도_없으면_사진_주소를_묻지_않고_비어_있다() {
		this.detailsBody = "{\"photos\":[]}";

		assertThat(client().photoUri(GOOGLE_ID, 0)).isEmpty();
		assertThat(client().photoUri(GOOGLE_ID, 4)).isEmpty();
		assertThat(mediaPaths()).isEmpty();
	}

	@Test
	void 사진_칸이_아예_없어도_비어_있다() {
		this.detailsBody = "{}";

		assertThat(client().photoUri(GOOGLE_ID, 2)).isEmpty();
	}

	@Test
	void Google_이_오류를_주면_번호와_상관없이_비어_있다() {
		this.detailsStatus = 500;

		assertThat(client().photoUri(GOOGLE_ID, 0)).isEmpty();
		assertThat(client().photoUri(GOOGLE_ID, 3)).isEmpty();
	}

	@Test
	void 키가_없으면_Google_을_부르지_않는다() {
		this.properties.setApiKey("");

		assertThat(client().photoUri(GOOGLE_ID, 2)).isEmpty();
		assertThat(this.paths).isEmpty();
	}

	@Test
	void 장소_상세는_번호와_상관없이_같은_필드만_요청한다() {
		client().photoUri(GOOGLE_ID, 0);
		client().photoUri(GOOGLE_ID, 7);

		// 필드 마스크가 곧 과금 구간이다 — 번호를 지원한다고 더 달라고 하면 안 된다.
		assertThat(this.fieldMasks).containsExactly("photos", "photos");
	}

	// ---- 컨트롤러까지 이어서 ----

	@Test
	void 컨트롤러까지_이어도_번호가_범위를_넘으면_첫_사진으로_302() {
		this.detailsBody = photosJson(2);
		PlacePhotoController controller = controllerFor(GooglePlacePhotoLink.filePageOf(GOOGLE_ID, 7));

		ResponseEntity<Void> res = controller.photo(this.placeId);

		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FOUND);
		assertThat(res.getHeaders().getLocation()).hasToString("https://lh3.googleusercontent.com/p/P0");
	}

	@Test
	void 컨트롤러까지_이으면_주소에_적힌_번호의_사진으로_302() {
		this.detailsBody = photosJson(5);
		PlacePhotoController controller = controllerFor(GooglePlacePhotoLink.filePageOf(GOOGLE_ID, 3));

		ResponseEntity<Void> res = controller.photo(this.placeId);

		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FOUND);
		assertThat(res.getHeaders().getLocation()).hasToString("https://lh3.googleusercontent.com/p/P3");
	}

	// ---- 도우미 ----

	private final UUID placeId = UUID.randomUUID();

	private GooglePlacePhotoClient client() {
		return new GooglePlacePhotoClient(RestClient.builder(), this.properties);
	}

	private PlacePhotoController controllerFor(String filePage) {
		PlaceRepository repository = mock(PlaceRepository.class);
		Place place = mock(Place.class);
		when(place.getPhotoLicense()).thenReturn(new PhotoLicense(GooglePlacePhotoLink.LICENSE_NAME, null, filePage));
		when(repository.findById(this.placeId)).thenReturn(Optional.of(place));
		return new PlacePhotoController(repository, client(), this.properties, Clock.systemUTC());
	}

	private List<String> mediaPaths() {
		return this.paths.stream().filter((p) -> p.endsWith("/media")).collect(Collectors.toList());
	}

	/** 사진 {@code count} 장짜리 장소 상세. 이름은 P0, P1, ... 이고 실제 응답처럼 저작자 표시도 실었다. */
	private static String photosJson(int count) {
		return "{\"photos\":[" + IntStream.range(0, count)
				.mapToObj((i) -> "{\"name\":\"places/" + GOOGLE_ID + "/photos/P" + i + "\",\"widthPx\":800,\"heightPx\":600,"
						+ "\"authorAttributions\":[{\"displayName\":\"작성자 " + i + "\"}]}")
				.collect(Collectors.joining(",")) + "]}";
	}

	private static void respond(HttpExchange exchange, int status, String body) throws IOException {
		byte[] payload = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
		exchange.sendResponseHeaders(status, payload.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(payload);
		}
	}
}
