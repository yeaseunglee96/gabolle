package com.gabolle.backend.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.review.application.PlaceReviewService;
import com.gabolle.backend.review.domain.PlaceReview;
import com.gabolle.backend.review.presentation.PlaceReviewController;
import com.gabolle.backend.review.presentation.ReviewExceptionHandler;
import com.gabolle.backend.review.support.ReviewPostgresIntegrationTest;

class PlaceReviewIntegrationTest extends ReviewPostgresIntegrationTest {

	@Autowired
	private PlaceReviewController controller;

	@Autowired
	private ReviewExceptionHandler exceptionHandler;

	@Autowired
	private PlaceReviewService placeReviewService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private MockMvc mockMvc;

	private PlaceFixture placeFixture;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.controller)
				.setControllerAdvice(this.exceptionHandler)
				.build();
		this.placeFixture = new PlaceFixture(this.jdbcTemplate);
	}

	private UUID createUser() {
		UUID id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbcTemplate.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				id, now, now);
		return id;
	}

	private void markVisitVerified(UUID placeId, UUID userId) {
		this.jdbcTemplate.update(
				"INSERT INTO place_visit_verification (place_visit_verification_id, place_id, user_id, verified_at, distance_m) "
						+ "VALUES (?, ?, ?, now(), 0)",
				UUID.randomUUID(), placeId, userId);
	}

	private Authentication as(UUID id) {
		return new UsernamePasswordAuthenticationToken(id.toString(), null, List.of());
	}

	private String reviewBody(Integer food, Integer price, Integer accessibility, Integer onsite, String body,
			String region) {
		return "{"
				+ "\"foodScore\":" + food
				+ ",\"priceScore\":" + price
				+ ",\"accessibilityScore\":" + accessibility
				+ ",\"onsiteScore\":" + onsite
				+ ",\"body\":" + (body == null ? "null" : "\"" + body + "\"")
				+ ",\"region\":" + (region == null ? "null" : "\"" + region + "\"")
				+ "}";
	}

	private int reviewRowCount(UUID placeId, UUID userId) {
		Integer count = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM place_review WHERE place_id = ? AND user_id = ?", Integer.class, placeId,
				userId);
		return count == null ? 0 : count;
	}

	@Test
	@DisplayName("인증한 사람의 리뷰는 verified=true 로 저장된다")
	void verifiedUserReviewIsSavedAsVerified() throws Exception {
		UUID placeId = this.placeFixture.insertPlace("장소A", null, "CAFE", 35.1, 129.1);
		UUID userId = createUser();
		markVisitVerified(placeId, userId);

		this.mockMvc.perform(post("/api/v1/places/{placeId}/reviews", placeId)
						.principal(as(userId))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewBody(5, 4, null, 5, "좋아요", "해운대구")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.verified").value(true));

		assertThat(reviewRowCount(placeId, userId)).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 인증하지 않은 사람의 리뷰도 저장된다(거부되지 않는다) — verified=false")
	void unverifiedUserReviewIsStillSaved() throws Exception {
		UUID placeId = this.placeFixture.insertPlace("장소B", null, "CAFE", 35.1, 129.1);
		UUID userId = createUser();
		// markVisitVerified 를 부르지 않는다 — 위치 권한을 거부한 사람을 재현한다.

		this.mockMvc.perform(post("/api/v1/places/{placeId}/reviews", placeId)
						.principal(as(userId))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewBody(1, null, null, null, "별로예요", "해운대구")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.verified").value(false));

		assertThat(reviewRowCount(placeId, userId)).isEqualTo(1);
	}

	@Test
	@DisplayName("목록 응답에 인증 여부가 들어 있고, 평균은 인증된 평가만으로 계산된다")
	void listCarriesVerifiedFlagAndVerifiedOnlyAverage() throws Exception {
		UUID placeId = this.placeFixture.insertPlace("장소C", null, "CAFE", 35.1, 129.1);
		UUID verifiedUser = createUser();
		UUID unverifiedUser = createUser();
		markVisitVerified(placeId, verifiedUser);

		this.mockMvc.perform(post("/api/v1/places/{placeId}/reviews", placeId)
						.principal(as(verifiedUser))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewBody(5, null, null, null, null, "해운대구")))
				.andExpect(status().isOk());
		this.mockMvc.perform(post("/api/v1/places/{placeId}/reviews", placeId)
						.principal(as(unverifiedUser))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewBody(1, null, null, null, null, "해운대구")))
				.andExpect(status().isOk());

		this.mockMvc.perform(get("/api/v1/places/{placeId}/reviews", placeId).principal(as(verifiedUser)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.reviews.length()").value(2))
				// 인증 하나(5점)와 미인증 하나(1점) — 평균이 미인증까지 섞였다면 3 이 나와야 한다.
				.andExpect(jsonPath("$.data.averageScore").value(5.0));
	}

	@Test
	@DisplayName("인증된 평가가 없으면 평균은 null 이다 (0 이 아니다)")
	void averageIsNullWithoutAnyVerifiedReview() throws Exception {
		UUID placeId = this.placeFixture.insertPlace("장소D", null, "CAFE", 35.1, 129.1);
		UUID userId = createUser();

		this.mockMvc.perform(post("/api/v1/places/{placeId}/reviews", placeId)
						.principal(as(userId))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewBody(3, null, null, null, null, "해운대구")))
				.andExpect(status().isOk());

		this.mockMvc.perform(get("/api/v1/places/{placeId}/reviews", placeId).principal(as(userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.averageScore").doesNotExist());
	}

	@Test
	@DisplayName("같은 사람이 다시 쓰면 덮어써지고 행이 하나다")
	void rewriteOverwritesInsteadOfAddingRow() throws Exception {
		UUID placeId = this.placeFixture.insertPlace("장소E", null, "CAFE", 35.1, 129.1);
		UUID userId = createUser();

		this.mockMvc.perform(post("/api/v1/places/{placeId}/reviews", placeId)
						.principal(as(userId))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewBody(3, null, null, null, "처음", "해운대구")))
				.andExpect(status().isOk());
		this.mockMvc.perform(post("/api/v1/places/{placeId}/reviews", placeId)
						.principal(as(userId))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewBody(5, null, null, null, "다시씀", "해운대구")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.body").value("다시씀"));

		assertThat(reviewRowCount(placeId, userId)).isEqualTo(1);
	}

	@Test
	@DisplayName("점수 6 을 보내면 400 이고 어느 항목인지 응답에 남는다")
	void outOfRangeScoreReturns400WithField() throws Exception {
		UUID placeId = this.placeFixture.insertPlace("장소F", null, "CAFE", 35.1, 129.1);
		UUID userId = createUser();

		this.mockMvc.perform(post("/api/v1/places/{placeId}/reviews", placeId)
						.principal(as(userId))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewBody(6, null, null, null, null, "해운대구")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("SCORE_OUT_OF_RANGE"))
				.andExpect(jsonPath("$.error.fields[0]").value("food"));
	}

	@Test
	@DisplayName("점수도 글도 없으면 400 이다")
	void emptyReviewReturns400() throws Exception {
		UUID placeId = this.placeFixture.insertPlace("장소G", null, "CAFE", 35.1, 129.1);
		UUID userId = createUser();

		this.mockMvc.perform(post("/api/v1/places/{placeId}/reviews", placeId)
						.principal(as(userId))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewBody(null, null, null, null, null, "해운대구")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("EMPTY_REVIEW"));
	}

	@Test
	@DisplayName("없는 장소는 404 다 — 작성과 목록 조회 둘 다")
	void unknownPlaceReturns404ForWriteAndList() throws Exception {
		UUID missingPlaceId = UUID.randomUUID();
		UUID userId = createUser();

		this.mockMvc.perform(post("/api/v1/places/{placeId}/reviews", missingPlaceId)
						.principal(as(userId))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewBody(5, null, null, null, null, "해운대구")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("PLACE_NOT_FOUND"));

		this.mockMvc.perform(get("/api/v1/places/{placeId}/reviews", missingPlaceId).principal(as(userId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("PLACE_NOT_FOUND"));
	}

	@Test
	@DisplayName("place_review 표에 좌표 칸이 없다")
	void placeReviewTableHasNoCoordinateColumns() {
		List<String> columnNames = this.jdbcTemplate.queryForList(
				"SELECT column_name FROM information_schema.columns "
						+ "WHERE table_name = 'place_review' AND table_schema = current_schema()",
				String.class);
		assertThat(columnNames).isNotEmpty();
		for (String columnName : columnNames) {
			String lower = columnName.toLowerCase();
			assertThat(lower).doesNotContain("lat").doesNotContain("lng").doesNotContain("longitude")
					.doesNotContain("latitude");
		}
	}

	@Test
	@DisplayName("🔴 같은 목록 응답 안에서 내가 쓴 평가는 mine=true, 남이 쓴 평가는 mine=false 다 — S15P21E201-745")
	void mineFlagDiffersWithinTheSameListResponse() throws Exception {
		UUID placeId = this.placeFixture.insertPlace("장소H", null, "CAFE", 35.1, 129.1);
		UUID me = createUser();
		UUID someoneElse = createUser();

		this.mockMvc.perform(post("/api/v1/places/{placeId}/reviews", placeId)
						.principal(as(me))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewBody(5, null, null, null, "내 평가", "해운대구")))
				.andExpect(status().isOk());
		this.mockMvc.perform(post("/api/v1/places/{placeId}/reviews", placeId)
						.principal(as(someoneElse))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewBody(1, null, null, null, "남의 평가", "해운대구")))
				.andExpect(status().isOk());

		// 목록 순서에 기대지 않고 본문으로 찾아 짚는다 — 정렬 규칙이 바뀌어도 이 검사는 유효하다.
		String json = this.mockMvc
				.perform(get("/api/v1/places/{placeId}/reviews", placeId).principal(as(me)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		Map<String, JsonNode> rowByBody = new LinkedHashMap<>();
		new ObjectMapper().readTree(json).path("data").path("reviews")
				.forEach(row -> rowByBody.put(row.path("body").asText(), row));

		assertThat(rowByBody.keySet()).as("응답 전체: %s", json).contains("내 평가", "남의 평가");
		// 칸이 아예 없으면 asBoolean() 이 조용히 false 를 준다 — 먼저 참·거짓인지 본다.
		assertThat(rowByBody.get("내 평가").path("mine").isBoolean()).as("응답 전체: %s", json).isTrue();
		assertThat(rowByBody.get("내 평가").path("mine").asBoolean()).as("응답 전체: %s", json).isTrue();
		assertThat(rowByBody.get("남의 평가").path("mine").asBoolean()).as("응답 전체: %s", json).isFalse();
	}

	@Test
	@DisplayName("🔴 방금 쓴 응답도 mine=true 다 — write 와 list 가 같은 판정을 쓴다")
	void mineFlagIsTrueOnTheWriteResponseItself() throws Exception {
		UUID placeId = this.placeFixture.insertPlace("장소I", null, "CAFE", 35.1, 129.1);
		UUID userId = createUser();

		this.mockMvc.perform(post("/api/v1/places/{placeId}/reviews", placeId)
						.principal(as(userId))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewBody(5, null, null, null, "내 평가", "해운대구")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.mine").value(true));
	}

	@Test
	@DisplayName("리뷰 목록 응답에 작성자 번호 문자열이 새지 않는다")
	void listResponseDoesNotLeakAuthorId() throws Exception {
		UUID placeId = this.placeFixture.insertPlace("장소J", null, "CAFE", 35.1, 129.1);
		UUID me = createUser();
		UUID someoneElse = createUser();

		this.mockMvc.perform(post("/api/v1/places/{placeId}/reviews", placeId)
						.principal(as(me))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewBody(5, null, null, null, "내 평가", "해운대구")))
				.andExpect(status().isOk());
		this.mockMvc.perform(post("/api/v1/places/{placeId}/reviews", placeId)
						.principal(as(someoneElse))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewBody(1, null, null, null, "남의 평가", "해운대구")))
				.andExpect(status().isOk());

		String json = this.mockMvc
				.perform(get("/api/v1/places/{placeId}/reviews", placeId).principal(as(me)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(json).doesNotContain(me.toString()).doesNotContain(someoneElse.toString());
	}

	/** MockMvc 가 아니라 서비스를 직접 여러 스레드로 부른다 — 서블릿 계층을 거치면 신호가 흐려진다. */
	@Test
	@DisplayName("동시에 두 번 써도 행은 하나다 — UNIQUE 위반이 500 으로 새지 않는다")
	void concurrentWritesLeaveExactlyOneRow() throws Exception {
		UUID placeId = this.placeFixture.insertPlace("경쟁장소", null, "CAFE", 35.1, 129.1);
		UUID userId = createUser();

		int attempts = 8;
		ExecutorService pool = Executors.newFixedThreadPool(attempts);
		CountDownLatch ready = new CountDownLatch(attempts);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger failures = new AtomicInteger();
		try {
			for (int i = 0; i < attempts; i++) {
				int score = 1 + (i % 5);
				pool.submit(() -> {
					ready.countDown();
					try {
						start.await();
						PlaceReview.Scores scores = new PlaceReview.Scores((short) score, null, null, null);
						this.placeReviewService.write(placeId, userId, scores, "동시쓰기-" + score, "해운대구");
					}
					catch (Exception ex) {
						failures.incrementAndGet();
					}
				});
			}
			ready.await(5, TimeUnit.SECONDS);
			start.countDown();
			pool.shutdown();
			pool.awaitTermination(10, TimeUnit.SECONDS);
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(failures.get()).as("어느 시도도 예외로 죽으면 안 된다").isEqualTo(0);
		assertThat(reviewRowCount(placeId, userId)).isEqualTo(1);
	}
}
