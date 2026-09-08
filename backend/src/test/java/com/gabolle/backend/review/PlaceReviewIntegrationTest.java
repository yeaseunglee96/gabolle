package com.gabolle.backend.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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

import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.review.application.PlaceReviewService;
import com.gabolle.backend.review.domain.PlaceReview;
import com.gabolle.backend.review.presentation.PlaceReviewController;
import com.gabolle.backend.review.presentation.ReviewExceptionHandler;
import com.gabolle.backend.review.support.ReviewPostgresIntegrationTest;

/**
 * 장소 리뷰 — S15P21E201-287 · -408.
 *
 * <p>완료 기준 대부분은 MockMvc 로 확인하고, 동시 쓰기 경쟁({@link
 * #concurrentWritesLeaveExactlyOneRow()})은 실제 스레드 둘로 붙여 UNIQUE 위반이 500 으로 새지
 * 않는지 확인한다.
 */
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

	/** 위치로 방문을 확인한 것으로 만든다 — {@code place_visit_verification} 에 행을 하나 둔다. */
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

	/**
	 * 🔴 표에 좌표 칸이 없다 — 리뷰는 좌표 대신 {@code region} 문자열만 받는다. DTO 자체가
	 * 좌표를 받지 않아 저장할 수도 없지만, 표에도 그 칸이 없다는 것을 직접 확인한다.
	 */
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

	/**
	 * 🔴 선조회만으로는 막지 못하는 경쟁 — 같은 사람이 같은 장소에 거의 동시에 두 번 써도
	 * 행이 하나여야 한다({@code UNIQUE (place_id, user_id)}). {@code PlaceReviewService.write}
	 * 가 {@code REQUIRES_NEW} 로 삽입 시도를 독립 트랜잭션에 두어, 지는 쪽이 UNIQUE 위반을
	 * 겪어도 그 트랜잭션만 깔끔히 롤백되고 이어서 다시쓰기로 넘어간다는 것을 확인한다.
	 *
	 * <p>서비스 계층을 직접 두 스레드로 부른다 — MockMvc/서블릿 계층을 거치면 스레드마다
	 * 컨테이너를 새로 만들어야 해서 신호가 흐려진다.
	 */
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
