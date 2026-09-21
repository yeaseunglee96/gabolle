package com.gabolle.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.auth.service.AnonymousSessionService;
import com.gabolle.backend.auth.service.AnonymousSessionService.IssuedAnonymousSession;
import com.gabolle.backend.auth.service.AuthCommands;
import com.gabolle.backend.auth.service.LocalAuthService;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.trip.application.AnonymousTripClaimService;
import com.gabolle.backend.trip.domain.TripRepository;
import com.gabolle.testslice.AuthSliceApplication;

/**
 * 승계 도중 실패하면 계정도 만들어지지 않는다.
 *
 * <p>승계({@code AnonymousTripClaimService})가 항상 실패하는 구현을 {@code @Primary} 로 끼운다.
 * {@code LocalAuthService.register} 가 한 트랜잭션이면 그 실패는 이미 저장한 {@code app_user}·
 * {@code local_credential} 행까지 되돌린다. 승계를 별도 트랜잭션이나 트랜잭션 밖에서 부르면
 * 계정만 남는다.
 */
@SpringBootTest(classes = AuthSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true",
		"gabolle.auth.jwt-secret=test-only-secret-value-at-least-32-chars-long",
		"gabolle.mail.enabled=false"
})
@Import(SignupRollsBackOnAnonymousTripClaimFailureIntegrationTest.BrokenClaim.class)
@ExtendWith(PostgresAvailableCondition.class)
class SignupRollsBackOnAnonymousTripClaimFailureIntegrationTest {

	@TestConfiguration(proxyBeanMethods = false)
	static class BrokenClaim {

		@Bean
		@Primary
		AnonymousTripClaimService brokenAnonymousTripClaimService(TripRepository repository) {
			return new AnonymousTripClaimService(repository) {
				@Override
				public int claimForNewUser(String sessionId, String newOwnerId, Instant at) {
					throw new IllegalStateException("승계가 끊겼다(테스트)");
				}
			};
		}
	}

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private AnonymousSessionService anonymousSessionService;

	@Autowired
	private LocalAuthService localAuthService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("🔴 승계 도중 실패하면 방금 만들려던 계정도 함께 롤백된다")
	void accountIsNotCreatedWhenClaimFails() {
		IssuedAnonymousSession session = anonymousSessionService.issue();
		String email = "broken-claim-" + UUID.randomUUID() + "@example.com";

		assertThatThrownBy(() -> localAuthService.register(new AuthCommands.Register(
				email, "Route!2026", "여행자", "KO", true, "device-1",
				Map.of("TERMS_OF_SERVICE", true, "PRIVACY_POLICY", true), false, session.token())))
				.isInstanceOf(IllegalStateException.class);

		Integer credentialCount = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM local_credential WHERE email = ?", Integer.class, email);
		assertThat(credentialCount).isZero();
	}
}
