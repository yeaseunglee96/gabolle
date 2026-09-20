package com.gabolle.backend.trip;

import java.util.Optional;
import com.gabolle.backend.user.support.ConsentGuards;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.auth.api.AuthExceptionHandler;
import com.gabolle.backend.trip.application.PreferenceDefaultsService;
import com.gabolle.backend.trip.application.TripCreationService;
import com.gabolle.backend.trip.application.TripDeletionService;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.infra.InMemoryTripRepository;
import com.gabolle.backend.trip.presentation.TripController;
import com.gabolle.backend.trip.presentation.TripExceptionHandler;

/**
 * 전역 {@code AuthExceptionHandler} 까지 같이 올린다 — {@code TripExceptionHandler} 하나만 올리면
 * 두 {@code @RestControllerAdvice} 사이의 라우팅이 어긋나 500 이 나가는 것을 못 잡는다.
 */
class TripControllerExceptionRoutingTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		InMemoryTripRepository repository = new InMemoryTripRepository();
		Clock clock = Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC);
		TripCreationService creationService = new TripCreationService(repository, clock,
				new PreferenceDefaultsService(repository, clock), ConsentGuards.granting(), Optional.empty(), Optional.empty());
		TripQueryService queryService = new TripQueryService(repository);

		TripController controller = new TripController(creationService, queryService,
				new TripDeletionService(repository, clock));
		this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new TripExceptionHandler(), new AuthExceptionHandler())
				.build();
	}

	private static Authentication user() {
		return new TestingAuthenticationToken(java.util.UUID.randomUUID().toString(), null);
	}

	@Test
	@DisplayName("🔴 재현 — ALLERGY constraintKey=OTHER 는 500 이 아니라 400 SENSITIVE_CONSTRAINT_NOT_SUPPORTED 여야 한다")
	void allergyOtherReturnsHandled400NotRaw500() throws Exception {
		String body = """
				{
				  "startDate": "2026-09-10",
				  "finishDate": "2026-09-12",
				  "partySize": 1,
				  "originLat": 35.1587,
				  "originLng": 129.1604,
				  "timezone": "Asia/Seoul",
				  "constraints": [
				    { "type": "ALLERGY", "constraintKey": "OTHER", "severity": "SOFT", "answerStatus": "NONE" }
				  ]
				}""";

		this.mockMvc.perform(post("/api/v1/trips")
						.contentType(MediaType.APPLICATION_JSON)
						.principal(user())
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("SENSITIVE_CONSTRAINT_NOT_SUPPORTED"));
	}
}
