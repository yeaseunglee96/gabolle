package com.gabolle.backend.auth.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.AuthSliceApplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 잘못된 운영자 설정이 정말로 기동을 막는가.
 *
 * <p>{@link AdminRoleStartupSynchronizerIntegrationTest} 는 동기화 메서드를 직접 불러 결과를
 * 본다. 그것만으로는 그 메서드가 기동 경로에 연결돼 있는지가 확인되지 않는다. 그래서 여기서는
 * 애플리케이션을 실제로 띄운다 — 배포 설정에 없는 계정을 적으면 {@code SpringApplication.run}
 * 자체가 실패해야 하고, 설정이 비어 있으면 아무 일 없이 떠야 한다.
 *
 * <p>{@code @SpringBootTest} 를 쓰지 않는다. 기동 실패를 기대하는 검사라 컨텍스트 캐시에
 * 실패한 컨텍스트가 남아 뒤에 도는 검사가 같은 실패를 물려받는다. 여기서는 컨텍스트를 직접
 * 띄우고 직접 닫는다.
 */
@ExtendWith(PostgresAvailableCondition.class)
class AdminRoleStartupBootTest {

	@Test
	@DisplayName("🔴 완료 기준 — 설정에 적은 계정이 없으면 애플리케이션이 아예 뜨지 않는다")
	void applicationRefusesToStartWhenAConfiguredAdminAccountIsMissing() {
		String absent = "zebra-" + UUID.randomUUID() + "@example.test";

		assertThatThrownBy(() -> boot(absent).close())
				.as("기동이 성공했다면 오타 하나로 운영자가 아무도 없는 상태가 조용히 유지된다")
				.rootCause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(AdminRoleStartupSynchronizer.PROPERTY)
				.hasMessageContaining(AdminRoleStartupSynchronizer.ENVIRONMENT_VARIABLE);
	}

	@Test
	@DisplayName("완료 기준 — 운영자를 지정하지 않은 배포는 정상 기동한다")
	void applicationStartsWhenNoAdminIsConfigured() {
		try (ConfigurableApplicationContext context = boot("")) {
			assertThat(context.isRunning()).isTrue();
			assertThat(context.getBean(AuthProperties.class).getAdminEmails())
					.as("기본값이 있으면 여기서 비어 있지 않다 — 그것이 이 티켓이 막으려던 상태다")
					.isEmpty();
		}
	}

	private ConfigurableApplicationContext boot(String adminEmails) {
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("spring.profiles.active", "db");
		properties.put("spring.main.banner-mode", "off");
		// 포트를 고정하면 다른 테스트가 남긴 서버와 부딪힌다. 0 이면 OS 가 빈 포트를 준다.
		properties.put("server.port", "0");
		properties.put("spring.jpa.hibernate.ddl-auto", "validate");
		properties.put("spring.flyway.enabled", "true");
		properties.put("spring.flyway.clean-disabled", "false");
		properties.put("spring.flyway.clean-on-validation-error", "true");
		properties.put("spring.datasource.url", TestDatabase.url());
		properties.put("spring.datasource.username", TestDatabase.username());
		properties.put("spring.datasource.password", TestDatabase.password());
		properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
		// 이 컨텍스트도 연결 풀을 하나 새로 만든다. 캐시된 테스트 컨텍스트들과 자리를
		// 나눠 쓰므로 작게 잡는다 (TestDatabase 주석의 그 사고).
		properties.put("spring.datasource.hikari.maximum-pool-size", "2");
		properties.put("spring.datasource.hikari.minimum-idle", "0");
		properties.put("gabolle.auth.jwt-secret", "test-only-secret-value-at-least-32-chars-long");
		properties.put("gabolle.mail.enabled", "false");
		properties.put("gabolle.auth.admin-emails", adminEmails);
		return new SpringApplicationBuilder(AuthSliceApplication.class).properties(properties).run();
	}
}
