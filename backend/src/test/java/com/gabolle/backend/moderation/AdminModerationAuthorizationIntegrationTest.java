package com.gabolle.backend.moderation;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
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
import com.gabolle.backend.story.StoryFixture;
import com.gabolle.testslice.StorySliceApplication;

/**
 * "ADMIN 이 아니면 거절" 을 진짜 Spring Security 필터 체인으로 확인한다. 이 컨트롤러는 인가를 코드로
 * 하지 않으므로, 컨트롤러를 직접 무는 {@code standaloneSetup} 방식으로는 이 기준을 잴 수 없다.
 *
 * 운영 {@code SecurityConfig} 는 JWT·OAuth 설정까지 끌고 와서 올리지 않고, admin 경로 →
 * {@code hasRole("ADMIN")} 한 줄만 옮긴 테스트 전용 {@link SecurityFilterChain} 을 쓴다. 그래서 그
 * 한 줄이 운영 설정에도 그대로 있는지는 이 테스트가 보장하지 않는다.
 *
 * 🔴 S15P21E201-1548 — {@code TestSecurity} 에 {@code @EnableMethodSecurity} 를 추가해
 * 컨트롤러의 {@code @PreAuthorize} 도 이 컨텍스트에서 같이 켜진다. 아래 세 시험은 이제
 * "경로 매처"와 "메서드 보안" 두 방어선이 겹쳐 있어도 서로 어긋나지 않는지(여전히 정확히
 * ADMIN 만 통과) 함께 잰다 — 방어선을 하나 더 추가한 것이 기존 동작을 깨지 않았다는 뜻이다.
 */
@SpringBootTest(classes = StorySliceApplication.class, properties = { "spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=true" })
@AutoConfigureMockMvc
@Import(AdminModerationAuthorizationIntegrationTest.TestSecurity.class)
@ExtendWith(PostgresAvailableCondition.class)
class AdminModerationAuthorizationIntegrationTest {

	@TestConfiguration(proxyBeanMethods = false)
	@EnableMethodSecurity
	static class TestSecurity {

		/** {@code SecurityConfig} 의 admin 규칙 한 줄만 옮긴 테스트 전용 체인. */
		@Bean
		SecurityFilterChain adminOnlySecurityFilterChain(HttpSecurity http) throws Exception {
			http.csrf(csrf -> csrf.disable())
					.authorizeHttpRequests(
							authorize -> authorize.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
									.anyRequest().permitAll());
			return http.build();
		}
	}

	@TempDir
	static Path storageRoot;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
		registry.add("gabolle.storage.root", () -> storageRoot.toString());
		registry.add("gabolle.storage.public-base-path", () -> StoryFixture.IMAGE_BASE);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID admin;

	private UUID ordinaryUser;

	@BeforeEach
	void setUp() {
		this.admin = StoryFixture.insertUser(this.jdbc, "운영자");
		ModerationFixture.promoteToAdmin(this.jdbc, this.admin);
		this.ordinaryUser = StoryFixture.insertUser(this.jdbc, "일반 사용자");
		StoryFixture.insertStory(this.jdbc, this.admin, "인가 테스트용", "PUBLIC",
				Instant.now().minus(Duration.ofHours(1)));
	}

	@Test
	@DisplayName("🔴 ADMIN 이 아니면 관리자 경로가 거절된다")
	void ordinaryUserIsRejected() throws Exception {
		this.mockMvc
				.perform(get("/api/v1/admin/story-reports").with(user(this.ordinaryUser.toString()).roles("USER")))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("토큰(인증) 자체가 없으면 거절된다")
	void unauthenticatedIsRejected() throws Exception {
		this.mockMvc.perform(get("/api/v1/admin/story-reports").with(anonymous())).andExpect(status().is4xxClientError());
	}

	@Test
	@DisplayName("ADMIN 이면 통과해 실제 목록을 받는다")
	void adminIsAllowed() throws Exception {
		this.mockMvc.perform(get("/api/v1/admin/story-reports").with(user(this.admin.toString()).roles("ADMIN")))
				.andExpect(status().isOk());
	}
}
