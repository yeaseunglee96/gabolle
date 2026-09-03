package com.gabolle.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.web.client.RestClient;

@SpringBootTest
class RestClientBuilderContextTest {

	@Test
	void restClientBuilderIsAutoConfigured(ApplicationContext applicationContext) {
		assertThat(applicationContext.getBean(RestClient.Builder.class)).isNotNull();
	}
}
