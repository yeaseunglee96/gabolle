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
 * S15P21E201-267 — "ADMIN 이 아니면 거절" 을 <b>진짜 Spring Security 필터 체인</b>으로 확인한다.
 *
 * <h2>왜 이 파일이 {@code AdminModerationQueueIntegrationTest} 와 따로 있나</h2>
 * 다른 통합 테스트들은 전부 {@code standaloneSetup(controller)} 다 — 컨트롤러를 DispatcherServlet
 * 없이 직접 문다. 그 방식은 빠르지만 {@code SecurityConfig} 의
 * {@code requestMatchers("/api/v1/admin/**").hasRole("ADMIN")} 을 전혀 거치지 않는다(그 규칙을
 * 적용하는 필터 자체가 요청 경로에 없다). 그런데 이 컨트롤러는 인가를 코드로 하지 않기로
 * 했다({@code AdminModerationController} 주석 — {@code @PreAuthorize} 는 메서드 보안이 꺼져 있어
 * 조용히 무시된다). 그래서 "ADMIN 이 아니면 거절된다" 는 완료 기준은 <b>실제 인가 필터가 도는
 * 경로</b>로만 검증할 수 있다.
 *
 * <h2>🔴 진짜 {@code SecurityConfig} 를 올리지 않는다</h2>
 * {@code SecurityConfig}·{@code HmacJwtAuthenticationFilter} 는 JWT 서명·세션 조회·OAuth 설정까지
 * 필요해 이 슬라이스로 끌어오면 인증 인프라 전체를 새로 짜야 한다(이번 작업의 파일 범위 밖이기도
 * 하다). 대신 {@code SecurityConfig} 와 <b>같은 한 줄</b>(admin 경로 → {@code hasRole("ADMIN")})만
 * 옮긴 최소 테스트 전용 {@link SecurityFilterChain} 을 두고, {@code spring-security-test} 의
 * {@code user(...).roles(...)} 로 인증을 흉내 낸다. 이렇게 하면 <b>Spring Security 의 진짜
 * 인가 필터</b>가 이 규칙을 적용하는지 검증하는 것이지, 우리 코드의 흉내가 아니다. 다만 이 규칙이
 * 실제 운영 {@code SecurityConfig} 에도 똑같이 박혀 있는지는 이 테스트가 보장하지 못한다 — 그건
 * {@code SecurityConfig} 를 만든 감독자의 몫이고, 이미 그 파일 자체에 같은 주석과 함께 커밋돼 있다.
 */
@SpringBootTest(classes = StorySliceApplication.class, properties = { "spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=true" })
@AutoConfigureMockMvc
@Import(AdminModerationAuthorizationIntegrationTest.TestSecurity.class)
@ExtendWith(PostgresAvailableCondition.class)
class AdminModerationAuthorizationIntegrationTest {

	@TestConfiguration(proxyBeanMethods = false)
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
