package com.gabolle.backend.trip;

import java.util.Optional;
import com.gabolle.backend.user.support.ConsentGuards;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.trip.application.PreferenceDefaultsService;
import com.gabolle.backend.trip.application.TripCreationService;
import com.gabolle.backend.trip.application.TripDeletionService;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.TimeWindows;
import com.gabolle.backend.trip.domain.TravelModes;
import com.gabolle.backend.trip.infra.InMemoryTripRepository;
import com.gabolle.backend.trip.presentation.TripController;
import com.gabolle.backend.trip.presentation.TripExceptionHandler;

/**
 * S15P21E201-664 — {@code TripExceptionHandler} 가 새로 추가한 두 예외를 400 으로
 * 정확히 옮기는지 HTTP 계층에서 검증한다.
 *
 * <p>{@link TripCreationTest} 는 {@code TripCreationService} 를 직접 부르는 순수
 * 단위 테스트라 예외가 <b>던져지는 것까지만</b> 본다. {@link TripControllerGetTest} 와
 * 같은 이유로, "그 예외가 HTTP 400·정확한 코드로 바뀌는지"는 이 파일에서 따로 본다 —
 * 그래서 이 티켓 작업에서 새 파일로 추가했다({@code TripCreationTest} 에 더하지
 * 않은 이유는 그 파일이 도메인 단위 테스트라 MockMvc 를 안 쓰기 때문이다).
 *
 * <p>🔴 {@link TimeWindows.InvalidTimeWindowException} 과
 * {@link TravelModes.UnsupportedTravelModeException} 은 둘 다
 * {@link IllegalArgumentException} 의 하위 타입이다. 이 테스트는 그보다 <b>더 구체적인
 * 핸들러가 먼저 잡히는지</b>(= {@code handleIllegalArgument} 의 범용 코드
 * {@code TRIP_VALIDATION_FAILED} 로 뭉개지지 않는지)를 코드값으로 직접 확인한다.
 */
class TripCreationValidationTest {

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
                .setControllerAdvice(new TripExceptionHandler())
                .build();
    }

    private static Authentication asUser(String userId) {
        return new TestingAuthenticationToken(userId, null);
    }

    @Test
    @DisplayName("timeWindow 가 범위 모양인데 끝이 시작보다 앞이면 400 INVALID_TIME_WINDOW")
    void invalidTimeWindowRangeReturns400() throws Exception {
        String body = """
                {
                  "startDate": "2026-09-06",
                  "finishDate": "2026-09-08",
                  "partySize": 1,
                  "originLat": 35.1587,
                  "originLng": 129.1604,
                  "timeWindow": "18:00-09:00"
                }""";

        this.mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .principal(asUser(UUID.randomUUID().toString()))
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TIME_WINDOW"))
                .andExpect(jsonPath("$.error.fields[0]").value("timeWindow"));
    }

    @Test
    @DisplayName("transport 값이 모르는 이동수단이면 400 UNSUPPORTED_TRAVEL_MODE")
    void unsupportedTravelModeReturns400() throws Exception {
        String body = """
                {
                  "startDate": "2026-09-06",
                  "finishDate": "2026-09-08",
                  "partySize": 1,
                  "originLat": 35.1587,
                  "originLng": 129.1604,
                  "preferences": [
                    { "dimension": "transport", "value": "\\"HELICOPTER\\"", "answerStatus": "SELECTED" }
                  ]
                }""";

        this.mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .principal(asUser(UUID.randomUUID().toString()))
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_TRAVEL_MODE"));
    }

    @Test
    @DisplayName("timeWindow 가 실제 범위이고 transport 도 지원되면 201 로 만들어진다")
    void validRangeAndSupportedTransportReturns201() throws Exception {
        String body = """
                {
                  "startDate": "2026-09-06",
                  "finishDate": "2026-09-08",
                  "partySize": 1,
                  "originLat": 35.1587,
                  "originLng": 129.1604,
                  "timeWindow": "09:00-18:00",
                  "preferences": [
                    { "dimension": "transport", "value": "\\"TRANSIT\\"", "answerStatus": "SELECTED" }
                  ]
                }""";

        this.mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .principal(asUser(UUID.randomUUID().toString()))
                        .content(body))
                .andExpect(status().isCreated());
    }
}
