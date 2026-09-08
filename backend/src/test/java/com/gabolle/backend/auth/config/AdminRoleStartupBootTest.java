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
 * 잘못된 운영자 설정이 <b>정말로 기동을 막는가</b> — S15P21E201-225 의 완료 기준.
 *
 * <h2>🔴 왜 이 파일이 따로 있나</h2>
 *
 * {@link AdminRoleStartupSynchronizerIntegrationTest} 는 동기화 메서드를 직접 불러 결과를 본다.
 * 그것만으로는 확인되지 않는 것이 하나 남는다 — <b>그 메서드가 기동 경로에 실제로 연결돼 있는가</b>다.
 * 아무도 부르지 않는 검사는 통과해도 아무것도 막지 못하고, 그 상태에서도 위 테스트는 전부 초록이다.
 *
 * <p>그래서 여기서는 애플리케이션을 <b>진짜로 띄운다.</b> 배포 설정에 없는 계정을 적어 두면
 * {@code SpringApplication.run} 자체가 실패해야 한다. 이 저장소는 기동 실패로 운영이 멈춘 적이
 * 있으므로(INC-DEPLOY-001) 반대 방향도 함께 본다 — 설정이 비어 있을 때는 아무 일 없이 떠야 한다.
 *
 * <p>{@code @SpringBootTest} 를 쓰지 않는다. 기동이 실패하는 것을 <b>기대</b>하는 시험이라
 * 컨텍스트 캐시에 실패한 컨텍스트를 남기게 되고, 그러면 뒤에 도는 테스트가 같은 실패를
 * 물려받는다. 여기서는 컨텍스트를 직접 띄우고 직접 닫는다.
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
