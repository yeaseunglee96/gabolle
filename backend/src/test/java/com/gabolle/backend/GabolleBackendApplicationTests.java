package com.gabolle.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.health.registry.HealthContributorRegistry;

@SpringBootTest(properties = "spring.mail.host=127.0.0.1")
class GabolleBackendApplicationTests {

	@Autowired
	private HealthContributorRegistry healthContributors;

	@Test
	void contextLoads() {
	}

	@Test
	void disabledMailDoesNotParticipateInApplicationHealth() {
		assertThat(healthContributors.getContributor("mail")).isNull();
	}

}
