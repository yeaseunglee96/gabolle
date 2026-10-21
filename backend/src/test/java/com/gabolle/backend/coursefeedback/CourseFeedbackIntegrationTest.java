package com.gabolle.backend.coursefeedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.CourseFeedbackSliceApplication;

import tools.jackson.databind.json.JsonMapper;

/** 코스별 품질 피드백 — 저장·고치기·다시 열기·직접 짜기 거부·남의 일정 거부·운영자 집계(S15P21E201-2020). */
@SpringBootTest(classes = CourseFeedbackSliceApplication.class, properties = { "spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=true" })
@AutoConfigureMockMvc
@Import(CourseFeedbackIntegrationTest.TestSecurity.class)
@ExtendWith(PostgresAvailableCondition.class)
class CourseFeedbackIntegrationTest {

	@TestConfiguration(proxyBeanMethods = false)
	@EnableMethodSecurity
	static class TestSecurity {

		@Bean
		SecurityFilterChain courseFeedbackTestChain(HttpSecurity http) throws Exception {
			http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(authorize -> authorize
					.requestMatchers("/api/v1/admin/**").hasRole("ADMIN").anyRequest().authenticated());
			return http.build();
		}
	}

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	private final JsonMapper json = JsonMapper.builder().build();

	private UUID owner;

	private UUID stranger;

	private UUID itinerary;

	private String area;

	@BeforeEach
	void setUp() {
		this.owner = insertUser("여행자");
		this.stranger = insertUser("남");
		this.area = "T" + UUID.randomUUID().toString().substring(0, 8);
		this.itinerary = insertItinerary(this.owner, "req_" + UUID.randomUUID());
	}

	@Test
	@DisplayName("평가를 남기면 다시 열 때 내 평가가 보이고, 다시 보내면 같은 판의 한 줄을 고친다")
	void submitReopenAndEdit() throws Exception {
		send(put(url(this.itinerary)).with(as(this.owner)),
				body("DOWN", List.of("LONG_TRANSIT", "TOO_TIGHT"), "이동이 길었어요", "b"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.rating").value("DOWN"))
			.andExpect(jsonPath("$.data.courseLabel").value("B"));

		this.mockMvc.perform(get(url(this.itinerary)).with(as(this.owner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.feedback.rating").value("DOWN"))
			.andExpect(jsonPath("$.data.feedback.reasons[1]").value("TOO_TIGHT"))
			.andExpect(jsonPath("$.data.feedback.comment").value("이동이 길었어요"));

		send(put(url(this.itinerary)).with(as(this.owner)), body("UP", List.of("GREAT_PLACES"), null, null))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.rating").value("UP"))
			.andExpect(jsonPath("$.data.courseLabel").value("B"));
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM course_feedback WHERE itinerary_id = ?",
				Integer.class, this.itinerary)).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 여행 회원이 아니면 평가도 조회도 못 한다 — 일정이 있는지조차 알리지 않는다")
	void strangerIsRejected() throws Exception {
		send(put(url(this.itinerary)).with(as(this.stranger)), body("UP", List.of(), null, "A"))
			.andExpect(status().isNotFound());
		this.mockMvc.perform(get(url(this.itinerary)).with(as(this.stranger))).andExpect(status().isNotFound());
		this.mockMvc.perform(delete(url(this.itinerary)).with(as(this.stranger))).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("🔴 직접 짠 일정은 평가를 받지 않는다(409), 값이 틀리면 400")
	void selfPlanAndValidation() throws Exception {
		UUID self = insertItinerary(this.owner, "self_plan");
		send(put(url(self)).with(as(this.owner)), body("UP", List.of(), null, null))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COURSE_FEEDBACK_SELF_PLAN"));
		send(put(url(this.itinerary)).with(as(this.owner)), body("MEH", List.of(), null, null))
			.andExpect(status().isBadRequest());
		send(put(url(this.itinerary)).with(as(this.owner)), body("UP", List.of("BAD_REASON"), null, null))
			.andExpect(status().isBadRequest());
		send(put(url(this.itinerary)).with(as(this.owner)), body("UP", List.of(), "가".repeat(201), null))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("지우면 다시 열 때 비어 있다")
	void deleteClears() throws Exception {
		send(put(url(this.itinerary)).with(as(this.owner)), body("UP", List.of(), null, "A"))
			.andExpect(status().isOk());
		this.mockMvc.perform(delete(url(this.itinerary)).with(as(this.owner))).andExpect(status().isNoContent());
		this.mockMvc.perform(get(url(this.itinerary)).with(as(this.owner)))
			.andExpect(jsonPath("$.data.feedback").doesNotExist());
	}

	@Test
	@DisplayName("운영자 집계 — 코스·지역별 좋아요 비율과 이유별 건수. 일반 회원은 못 본다")
	void adminSummary() throws Exception {
		UUID second = insertItinerary(this.owner, "req_" + UUID.randomUUID());
		send(put(url(this.itinerary)).with(as(this.owner)), body("UP", List.of("GREAT_PLACES"), null, "A"))
			.andExpect(status().isOk());
		send(put(url(second)).with(as(this.owner)), body("DOWN", List.of("LONG_TRANSIT"), null, "A"))
			.andExpect(status().isOk());

		this.mockMvc.perform(get("/api/v1/admin/course-feedback/summary").with(as(this.owner)))
			.andExpect(status().isForbidden());
		String response = this.mockMvc
			.perform(get("/api/v1/admin/course-feedback/summary?days=7")
				.with(user(UUID.randomUUID().toString()).roles("ADMIN")))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Map<?, ?> data = (Map<?, ?>) this.json.readValue(response, Map.class).get("data");
		Map<?, ?> mine = ((List<?>) data.get("byArea")).stream()
			.map(Map.class::cast)
			.filter(b -> this.area.equals(b.get("key")))
			.findFirst()
			.orElseThrow();
		assertThat(((Number) mine.get("total")).intValue()).isEqualTo(2);
		assertThat(((Number) mine.get("upPercent")).intValue()).isEqualTo(50);
		assertThat(((Map<?, ?>) data.get("reasons")).get("LONG_TRANSIT")).isNotNull();
	}

	private static String url(UUID itineraryId) {
		return "/api/v1/itineraries/" + itineraryId + "/course-feedback";
	}

	private ResultActions send(MockHttpServletRequestBuilder request, Map<String, Object> body) throws Exception {
		return this.mockMvc
			.perform(request.contentType(MediaType.APPLICATION_JSON).content(this.json.writeValueAsString(body)));
	}

	private static Map<String, Object> body(String rating, List<String> reasons, String comment, String label) {
		Map<String, Object> body = new HashMap<>();
		body.put("rating", rating);
		body.put("reasons", reasons);
		body.put("comment", comment);
		body.put("courseLabel", label);
		return body;
	}

	private static RequestPostProcessor as(UUID id) {
		return user(id.toString()).roles("USER");
	}

	private UUID insertItinerary(UUID ownerId, String requestId) {
		UUID trip = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO trip (trip_id, owner_user_id, owner_type, start_date, end_date, origin_area_code,
				    created_at, updated_at)
				VALUES (?, ?, 'USER', ?, ?, ?, now(), now())
				""", trip, ownerId, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11), this.area);
		UUID itineraryId = UUID.randomUUID();
		this.jdbc.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, now())",
				itineraryId, trip);
		this.jdbc.update("""
				INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, operation, created_by,
				    request_id, created_at)
				VALUES (?, ?, 1, 'CREATE', ?, ?, now())
				""", UUID.randomUUID(), itineraryId, ownerId, requestId);
		return itineraryId;
	}

	private UUID insertUser(String displayName) {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, ?, 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				userId, displayName, now, now);
		return userId;
	}
}
