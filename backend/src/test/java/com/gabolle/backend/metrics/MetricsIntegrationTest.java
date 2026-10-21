package com.gabolle.backend.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.MetricsSliceApplication;

/** 활동일 기록과 운영 지표(S15P21E201-2010). */
@SpringBootTest(classes = MetricsSliceApplication.class, properties = { "spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=true" })
@AutoConfigureMockMvc
@Import(MetricsIntegrationTest.TestSecurity.class)
@ExtendWith(PostgresAvailableCondition.class)
class MetricsIntegrationTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@TestConfiguration(proxyBeanMethods = false)
	@EnableMethodSecurity
	static class TestSecurity {

		@Bean
		SecurityFilterChain metricsTestChain(HttpSecurity http) throws Exception {
			http.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(a -> a.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
					.anyRequest()
					.authenticated());
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
	private ActivityDayRecorder recorder;

	@Test
	@DisplayName("로그인한 요청이 오면 (사용자, 오늘) 한 줄이 남고, 같은 날 여러 번 와도 한 줄이다")
	void authenticatedRequestRecordsOneRowPerDay() throws Exception {
		UUID admin = insertUser(OffsetDateTime.now(SEOUL));
		for (int i = 0; i < 3; i++) {
			this.mockMvc.perform(get("/api/v1/admin/metrics").with(user(admin.toString()).roles("ADMIN")))
				.andExpect(status().isOk());
		}
		List<Map<String, Object>> rows = this.jdbc
			.queryForList("SELECT day, source FROM user_activity_day WHERE user_id = ?", admin);
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).get("source")).isEqualTo("REQUEST");
		assertThat(rows.get(0).get("day").toString()).isEqualTo(LocalDate.now(SEOUL).toString());
	}

	@Test
	@DisplayName("🔴 일반 사용자는 지표를 못 본다")
	void nonAdminIsForbidden() throws Exception {
		this.mockMvc.perform(get("/api/v1/admin/metrics").with(user(UUID.randomUUID().toString()).roles("USER")))
			.andExpect(status().isForbidden());
		this.mockMvc.perform(get("/api/v1/admin/metrics/daily.csv")
			.with(user(UUID.randomUUID().toString()).roles("USER"))).andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("익명 출입증도 기기 수로 따로 센다")
	void anonymousIsCountedSeparately() {
		UUID session = UUID.randomUUID();
		this.recorder.anonymous(session);
		this.recorder.anonymous(session);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM anonymous_activity_day WHERE session_id = ?",
				Integer.class, session)).isEqualTo(1);
	}

	@Test
	@DisplayName("DAU·MAU 와 가입일 묶음의 D1·D7 재방문을 센다. 아직 안 온 D30 은 비운다")
	void reportCountsActiveUsersAndRetention() throws Exception {
		LocalDate today = LocalDate.now(SEOUL);
		LocalDate signup = today.minusDays(10);
		UUID u = insertUser(signup.atTime(12, 0).atZone(SEOUL).toOffsetDateTime());
		activity(u, signup);
		activity(u, signup.plusDays(1));
		activity(u, signup.plusDays(7));
		activity(u, today);

		int dauBefore = this.jdbc.queryForObject("SELECT count(DISTINCT user_id) FROM user_activity_day WHERE day = ?",
				Integer.class, today);
		this.mockMvc.perform(get("/api/v1/admin/metrics?days=30").with(user(UUID.randomUUID().toString()).roles("ADMIN")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.daily.length()").value(30))
			.andExpect(jsonPath("$.data.totals.dau").value(dauBefore))
			.andExpect(jsonPath("$.data.cohorts[?(@.signupDay == '" + signup + "')].d1").value(
					org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.greaterThanOrEqualTo(1))))
			.andExpect(jsonPath("$.data.cohorts[?(@.signupDay == '" + signup + "')].d30").value(
					org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())));

		this.mockMvc.perform(get("/api/v1/admin/metrics/daily.csv?days=7")
			.with(user(UUID.randomUUID().toString()).roles("ADMIN")))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.startsWith("day,dau,wau,mau,")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString(today.toString())));
	}

	@Test
	@DisplayName("🔴 계정을 지우면 활동일도 함께 지워진다")
	void deletingUserDeletesActivity() {
		UUID u = insertUser(OffsetDateTime.now(SEOUL));
		this.recorder.user(u);
		this.jdbc.update("DELETE FROM app_user WHERE user_id = ?", u);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM user_activity_day WHERE user_id = ?", Integer.class,
				u)).isZero();
	}

	private void activity(UUID user, LocalDate day) {
		this.jdbc.update("INSERT INTO user_activity_day (user_id, day) VALUES (?, ?) ON CONFLICT DO NOTHING", user,
				day);
	}

	private UUID insertUser(OffsetDateTime createdAt) {
		UUID userId = UUID.randomUUID();
		this.jdbc.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, '지표', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				userId, createdAt, createdAt);
		return userId;
	}
}
