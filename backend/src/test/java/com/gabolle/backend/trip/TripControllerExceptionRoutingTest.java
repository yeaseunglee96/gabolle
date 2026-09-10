package com.gabolle.backend.trip;

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
 * 🔴 S15P21E201-294 — 진미리 님이 배포에서 재현한 "여행 생성 500" 을 추적한다.
 *
 * <p>실 계정으로 직접 재현해 보니 원인은 {@code ALLERGY} + {@code constraintKey="OTHER"}
 * 조합이었다. 도메인({@code TripConstraint})은 이 조합에서 {@code SensitiveConstraintNotSupportedException}
 * 을 던지고, {@code TripExceptionHandler} 가 그것을 400 으로 번역하는 핸들러를 <b>이미 갖고
 * 있다.</b> 그런데 배포에서는 500 이 났다 — 두 {@code @RestControllerAdvice} 가 동시에 등록된
 * 실제 앱에서만 드러나는 문제라는 뜻이라, {@link TripControllerGetTest}처럼
 * {@code TripExceptionHandler} 하나만 올리면 이 버그를 못 잡는다. 그래서 여기서는
 * {@code AuthExceptionHandler}(전역, {@code assignableTypes} 없음)도 같이 올린다.
 */
class TripControllerExceptionRoutingTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		InMemoryTripRepository repository = new InMemoryTripRepository();
		Clock clock = Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC);
		TripCreationService creationService = new TripCreationService(repository, clock,
				new PreferenceDefaultsService(repository, clock));
		TripQueryService queryService = new TripQueryService(repository);

		TripController controller = new TripController(creationService, queryService,
				new TripDeletionService(repository, clock));
		// 🔴 실제 앱은 두 advice 가 함께 등록된다 — AuthExceptionHandler 는 전역이라 TripController
		// 에서 난 예외도 본다. 이 둘을 같이 올려야 배포에서 난 것과 같은 라우팅이 재현된다.
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
