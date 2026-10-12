package com.gabolle.backend.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.HttpMethod;
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
import com.gabolle.testslice.FeedbackSliceApplication;
import com.gabolle.testslice.FeedbackSliceApplication.Recorder;

import tools.jackson.databind.json.JsonMapper;

/** 의견 보내기 — 접수·검증·하루 상한·관리자 처리·답변 알림(S15P21E201-2049). */
@SpringBootTest(classes = FeedbackSliceApplication.class, properties = { "spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=true" })
@AutoConfigureMockMvc
@Import(FeedbackIntegrationTest.TestSecurity.class)
@ExtendWith(PostgresAvailableCondition.class)
class FeedbackIntegrationTest {

	@TestConfiguration(proxyBeanMethods = false)
	@EnableMethodSecurity
	static class TestSecurity {

		@Bean
		SecurityFilterChain feedbackTestChain(HttpSecurity http) throws Exception {
			http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(authorize -> authorize
					.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
					.requestMatchers(HttpMethod.POST, "/api/v1/feedback").permitAll()
					.anyRequest().authenticated());
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

	@Autowired
	private Recorder recorder;

	private final JsonMapper json = JsonMapper.builder().build();

	private UUID member;

	private UUID admin;

	@BeforeEach
	void setUp() {
		this.member = insertUser("여행자");
		this.admin = insertUser("운영자");
		this.recorder.issued.clear();
		this.recorder.replied.clear();
	}

	@Test
	@DisplayName("로그인한 사람의 의견은 계정에 묶여 RECEIVED 로 쌓이고, Jira 번호가 붙는다")
	void memberSubmitIsLinkedAndIssued() throws Exception {
		String id = idOf(send(post("/api/v1/feedback").with(as(this.member)),
				body("BUG", "일정 화면이 안 열려요. 계속 빈 화면입니다."))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("RECEIVED")));

		Map<String, Object> row = this.jdbc.queryForMap("SELECT * FROM feedback WHERE feedback_id = ?::uuid", id);
		assertThat(row.get("user_id")).isEqualTo(this.member);
		assertThat(row.get("screen")).isEqualTo("trip/detail");
		assertThat(row.get("jira_key")).isEqualTo("TEST-1");
	}

	@Test
	@DisplayName("🔴 로그인 없이도 보낼 수 있다 — 계정 칸은 비고, 같은 기기는 하루 5건까지")
	void anonymousSubmitIsRateLimitedPerDevice() throws Exception {
		String device = "dev-" + UUID.randomUUID();
		for (int i = 0; i < 5; i++) {
			send(post("/api/v1/feedback").header("X-Device-Id", device), body("IDEA", "지도에 화장실도 보여 주면 좋겠어요 " + i))
					.andExpect(status().isCreated());
		}
		send(post("/api/v1/feedback").header("X-Device-Id", device), body("IDEA", "여섯 번째 의견은 막혀야 합니다"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.error.code").value("FEEDBACK_RATE_LIMITED"));
		assertThat(this.jdbc.queryForObject(
				"SELECT count(*) FROM feedback WHERE user_id IS NULL AND content LIKE '지도에 화장실%'", Integer.class))
				.isGreaterThanOrEqualTo(5);
	}

	@Test
	@DisplayName("🔴 내용은 10~1000자, 갈래는 BUG·IDEA·OTHER, 로그인 안 한 사람은 사진을 못 붙인다")
	void validation() throws Exception {
		send(post("/api/v1/feedback").with(as(this.member)), body("BUG", "짧아요")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("FEEDBACK_INVALID"));
		send(post("/api/v1/feedback").with(as(this.member)), body("BUG", "가".repeat(1001)))
				.andExpect(status().isBadRequest());
		send(post("/api/v1/feedback").with(as(this.member)), body("PRAISE", "열 글자가 넘는 내용입니다"))
				.andExpect(status().isBadRequest());
		Map<String, Object> withPhoto = body("BUG", "사진을 붙인 익명 의견입니다");
		withPhoto.put("screenshotUrls", List.of("https://example.com/a.jpg"));
		send(post("/api/v1/feedback").header("X-Device-Id", "d-" + UUID.randomUUID()), withPhoto)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("FEEDBACK_SCREENSHOT_INVALID"));
		Map<String, Object> badEmail = body("BUG", "답장 주소가 이상한 의견입니다");
		badEmail.put("replyEmail", "not-an-email");
		send(post("/api/v1/feedback").with(as(this.member)), badEmail).andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("🔴 관리자만 목록을 본다. DONE 으로 답하면 보낸 사람에게 알림이 간다")
	void adminListAndReplyNotifies() throws Exception {
		String id = idOf(send(post("/api/v1/feedback").with(as(this.member)), body("BUG", "저장 버튼이 두 번 눌러야 돼요"))
				.andExpect(status().isCreated()));

		this.mockMvc.perform(get("/api/v1/admin/feedback").with(as(this.member))).andExpect(status().isForbidden());
		this.mockMvc.perform(get("/api/v1/admin/feedback").param("status", "RECEIVED").with(asAdmin(this.admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[?(@.feedbackId == '" + id + "')]").exists());

		send(patch("/api/v1/admin/feedback/" + id).with(asAdmin(this.admin)), Map.of("status", "IN_PROGRESS"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
		assertThat(this.recorder.replied).isEmpty();

		send(patch("/api/v1/admin/feedback/" + id).with(asAdmin(this.admin)),
				Map.of("status", "DONE", "adminReply", "고쳤어요. 고맙습니다!"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.adminReply").value("고쳤어요. 고맙습니다!"));
		assertThat(this.recorder.replied).containsExactly(this.member);

		send(patch("/api/v1/admin/feedback/" + id).with(asAdmin(this.admin)), Map.of("status", "WAITING"))
				.andExpect(status().isBadRequest());
	}

	private Map<String, Object> body(String type, String content) {
		Map<String, Object> m = new HashMap<>();
		m.put("type", type);
		m.put("content", content);
		m.put("appVersion", "1.0.2");
		m.put("platform", "web");
		m.put("osVersion", "iOS 18");
		m.put("language", "ko");
		m.put("screen", "trip/detail");
		m.put("tripId", "trip-123");
		return m;
	}

	private ResultActions send(MockHttpServletRequestBuilder request, Map<String, Object> body) throws Exception {
		return this.mockMvc
			.perform(request.contentType(MediaType.APPLICATION_JSON).content(this.json.writeValueAsString(body)));
	}

	private String idOf(ResultActions result) throws Exception {
		Map<?, ?> parsed = this.json.readValue(result.andReturn().getResponse().getContentAsString(), Map.class);
		return (String) ((Map<?, ?>) parsed.get("data")).get("feedbackId");
	}

	private static RequestPostProcessor as(UUID id) {
		return user(id.toString()).roles("USER");
	}

	private static RequestPostProcessor asAdmin(UUID id) {
		return user(id.toString()).roles("ADMIN");
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
