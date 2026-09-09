package com.gabolle.backend.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

class ApiAuthenticationEntryPointTest {

	@Test
	void respondsWithApiErrorAndRequestId() throws Exception {
		ApiAuthenticationEntryPoint entryPoint = new ApiAuthenticationEntryPoint(new ObjectMapper());
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Request-Id", "request-123");
		MockHttpServletResponse response = new MockHttpServletResponse();

		entryPoint.commence(request, response, null);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentType()).startsWith("application/json");
		assertThat(response.getContentAsString()).contains("AUTHENTICATION_REQUIRED", "request-123");
	}
}
