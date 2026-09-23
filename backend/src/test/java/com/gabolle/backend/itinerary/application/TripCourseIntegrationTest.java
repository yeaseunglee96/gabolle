package com.gabolle.backend.itinerary.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.itinerary.presentation.TripActivityExceptionHandler;
import com.gabolle.backend.itinerary.presentation.TripCourseController;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryDetailResponse;
import com.gabolle.backend.recommendation.application.port.ItineraryDraft;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.testslice.CollaborationSliceApplication;

/**
 * 추천 코스 3안의 두 주소 (S15P21E201-1454) — DB 에 닿아야만 재지는 것들.
 *
 * <ul>
 *   <li>비회원에게 404 로 존재를 감추는가, 추천이 없는 여행이 오류가 아니라 빈 목록인가</li>
 *   <li>🔴 2안을 저장해도 <b>1안이 차지한 추천 번호의 유일 색인</b>과 부딪히지 않는가 — 이 설계가 기대는
 *       가정이다. 틀리면 2안을 고르는 순간마다 500 이 난다</li>
 *   <li>미리보기가 저장된 일정과 같은 모양이고, 고칠 수 없게 나가는가</li>
 * </ul>
 *
 * <p>2안·3안이 1안과 다른 곳으로 채워지는지는 {@code TripCourseServiceTest} 가 잰다.
 */
@SpringBootTest(classes = CollaborationSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class TripCourseIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private TripCourseController controller;

	@Autowired
	private TripActivityExceptionHandler exceptionHandler;

	@Autowired
	private ItineraryDraftService draftService;

	@Autowired
	private ItineraryQueryService queryService;

	@Autowired
	private TripQueryService tripQueryService;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mockMvc;

	private UUID owner;
	private UUID outsider;
	private UUID tripId;

	@BeforeEach
	void seed() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.controller)
				.setControllerAdvice(this.exceptionHandler)
				.build();

		this.owner = insertUser("소유자");
		this.outsider = insertUser("외부인");
		this.tripId = insertTrip();
		insertMember(this.tripId, this.owner, "OWNER");
	}

	@Test
	@DisplayName("🔴 비회원에게는 두 주소 다 404 다 — 코스 목록도, 고르기도")
	void outsiderCannotTellWhetherTheTripExists() throws Exception {
		this.mockMvc.perform(get("/api/v1/trips/{tripId}/recommendations", this.tripId).principal(as(this.outsider)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));

		this.mockMvc.perform(post("/api/v1/trips/{tripId}/course", this.tripId).principal(as(this.outsider))
						.contentType(MediaType.APPLICATION_JSON).content("{\"courseId\":\"" + UUID.randomUUID() + ":1\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));
	}

	@Test
	@DisplayName("추천이 아직 없는 여행은 오류가 아니라 빈 목록이다 — 화면은 그때 「보여 드릴 코스가 없어요」를 그린다")
	void aTripWithoutRecommendationIsAnEmptyList() throws Exception {
		this.mockMvc.perform(get("/api/v1/trips/{tripId}/recommendations", this.tripId).principal(as(this.owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.courses.length()").value(0));
	}

	@Test
	@DisplayName("이 여행의 것이 아닌 코스 번호는 404 COURSE_NOT_FOUND 다")
	void anUnknownCourseIsNotFound() throws Exception {
		this.mockMvc.perform(post("/api/v1/trips/{tripId}/course", this.tripId).principal(as(this.owner))
						.contentType(MediaType.APPLICATION_JSON).content("{\"courseId\":\"" + UUID.randomUUID() + ":1\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("COURSE_NOT_FOUND"));
	}

	@Test
	@DisplayName("🔴 2안 저장은 1안이 차지한 추천 번호와 부딪히지 않는다 — 판에는 꼬리표를, 항목에는 추천 번호를 남긴다")
	void theSecondCourseDoesNotCollideWithTheFirst() {
		UUID requestId = insertJob();
		UUID placeId = insertPlace();
		// 1안 — 추천 작업이 만든 일정이 그 추천 번호(source_request_id)를 차지하고 있다.
		insertFirstCourse(requestId);

		String itineraryId = this.draftService.persistAlternative(draftFor(requestId, placeId), "course:" + requestId + ":1")
				.itineraryId();

		Map<String, Object> version = this.jdbc.queryForMap(
				"SELECT request_id, source_request_id FROM itinerary_versions WHERE itinerary_id = ? AND version = 1",
				UUID.fromString(itineraryId));
		assertThat(version.get("request_id")).isEqualTo("course:" + requestId + ":1");
		assertThat(version.get("source_request_id")).isNull();

		Object itemSource = this.jdbc.queryForObject(
				"SELECT i.source_request_id FROM itinerary_item i JOIN itinerary_versions v "
						+ "ON v.itinerary_version_id = i.itinerary_version_id WHERE v.itinerary_id = ?",
				Object.class, UUID.fromString(itineraryId));
		assertThat(itemSource).as("어느 추천에서 나왔는지는 항목에 남는다").isEqualTo(requestId);
	}

	@Test
	@DisplayName("미리보기는 저장된 일정과 같은 모양이고 고칠 수 없다 — 판 번호 0, 장소 이름·좌표가 실린다")
	void aPreviewLooksLikeAnItineraryButIsNotEditable() {
		UUID requestId = insertJob();
		UUID placeId = insertPlace();
		ItineraryDraft draft = draftFor(requestId, placeId);
		ItineraryDraftService.DraftContent content = this.draftService.contentOf(draft, "preview", java.time.Instant.now());
		TripQueryService.View view = this.tripQueryService.get(this.tripId.toString(), this.owner.toString());

		ItineraryDetailResponse preview = this.queryService.preview(requestId + ":1", view.trip(), view.role(),
				content.items(), content.legs(), List.of());

		assertThat(preview.id()).isEqualTo(requestId + ":1");
		assertThat(preview.version()).isZero();
		assertThat(preview.canEdit()).isFalse();
		assertThat(preview.days().get(0).items()).singleElement().satisfies(item -> {
			assertThat(item.title()).isEqualTo("테스트 장소");
			assertThat(item.placeId()).isEqualTo(placeId.toString());
			assertThat(item.startsAt()).startsWith("2026-09-10T09:30");
		});
	}

	// ---- 시드 도우미 ----

	private ItineraryDraft draftFor(UUID requestId, UUID placeId) {
		ItineraryDraft.DraftItem item = new ItineraryDraft.DraftItem(0, LocalDate.of(2026, 9, 10), 1, placeId,
				UUID.randomUUID(), LocalTime.of(9, 30), LocalTime.of(10, 30), 60, "ESTIMATED", List.of(), List.of());
		return new ItineraryDraft(this.tripId.toString(), this.owner.toString(), requestId, null, null, null, null, null,
				List.of(item), List.of(), List.of());
	}

	/** 외래키 대상만 있으면 된다 — 다른 CHECK 를 건드리지 않게 FAILED 로 넣는다(ItineraryItemConstraintTest 와 같다). */
	private UUID insertJob() {
		UUID requestId = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO recommendation_job (
				    job_id, request_id, user_id, job_type, job_status, error_code, created_at)
				VALUES (?, ?, ?, 'ITINERARY_GENERATION', 'FAILED', 'TEST_FIXTURE', now())
				""", UUID.randomUUID(), requestId, this.owner);
		return requestId;
	}

	private UUID insertPlace() {
		UUID placeId = UUID.randomUUID();
		this.jdbc.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, '테스트 장소', now())", placeId);
		return placeId;
	}

	private void insertFirstCourse(UUID requestId) {
		UUID itineraryId = UUID.randomUUID();
		this.jdbc.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, now())",
				itineraryId, this.tripId);
		this.jdbc.update("""
				INSERT INTO itinerary_versions
				    (itinerary_version_id, itinerary_id, version, operation, created_by, request_id,
				     source_request_id, created_at)
				VALUES (?, ?, 1, 'CREATE', ?, ?, ?, now())
				""", UUID.randomUUID(), itineraryId, this.owner, requestId.toString(), requestId);
	}

	private UUID insertUser(String displayName) {
		UUID id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, ?, 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				id, displayName, now, now);
		return id;
	}

	private UUID insertTrip() {
		UUID id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update(
				"INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, created_at, updated_at) "
						+ "VALUES (?, ?, '2026-09-10', '2026-09-11', 2, ?, ?)",
				id, this.owner, now, now);
		return id;
	}

	private void insertMember(UUID trip, UUID userId, String role) {
		this.jdbc.update(
				"INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, ?, now())",
				UUID.randomUUID(), trip, userId, role);
	}

	private static Authentication as(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
	}
}
