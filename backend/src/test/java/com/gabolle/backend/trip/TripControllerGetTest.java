package com.gabolle.backend.trip;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.trip.application.TripCreationService;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.infra.InMemoryTripRepository;
import com.gabolle.backend.trip.presentation.TripController;
import com.gabolle.backend.trip.presentation.TripExceptionHandler;

/**
 * {@code GET /api/v1/trips/{tripId}} 를 HTTP 로 검증한다 — S15P21E201-461.
 *
 * <p>도메인 테스트({@code TripQueryServiceTest})는 예외가 던져지는 것까지만 보고,
 * <b>그 예외가 HTTP 404 로 바뀌는지는 보지 않는다.</b> 그 사이에서 깨질 수 있어서
 * 이 테스트가 따로 있다.
 *
 * <p>Spring 컨텍스트를 안 띄우고 {@link MockMvcBuilders#standaloneSetup} 으로
 * 컨트롤러와 예외 처리기만 올린다. 🔴 {@code SecurityConfig} 가 이 경로도 인증을
 * 요구하는데 그 파일은 auth 패키지(다른 사람 소유)라 손대지 않는다.
 */
class TripControllerGetTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InMemoryTripRepository repository = new InMemoryTripRepository();
        TripCreationService creationService =
                new TripCreationService(repository, Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC));
        TripQueryService queryService = new TripQueryService(repository);

        TripController controller = new TripController(creationService, queryService);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new TripExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("만든 사람이 조회하면 200 과 보낸 조건이 그대로 나온다")
    void ownerCanReadTheTripBack() throws Exception {
        String body = """
                {
                  "startDate": "2026-09-06",
                  "finishDate": "2026-09-08",
                  "budgetKrw": 300000,
                  "partySize": 2,
                  "timezone": "Asia/Seoul",
                  "constraints": [
                    { "type": "MOBILITY", "severity": "HARD", "operator": "LTE", "threshold": 5000.0 }
                  ]
                }""";

        String created = this.mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "usr_1")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String tripId = created.split("\"tripId\":\"")[1].split("\"")[0];

        this.mockMvc.perform(get("/api/v1/trips/{tripId}", tripId).header("X-User-Id", "usr_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trip.tripId").value(tripId))
                .andExpect(jsonPath("$.data.trip.budgetKrw").value(300000))
                .andExpect(jsonPath("$.data.constraints[0].type").value("MOBILITY"));
    }

    @Test
    @DisplayName("없는 여행을 조회하면 404 TRIP_NOT_FOUND")
    void unknownTripReturns404() throws Exception {
        this.mockMvc.perform(get("/api/v1/trips/{tripId}", "trp_unknown").header("X-User-Id", "usr_1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));
    }

    @Test
    @DisplayName("🔴 회원이 아닌 사람이 조회해도 404 — 403 이 아니다")
    void nonMemberAlsoGets404NotForbidden() throws Exception {
        String body = """
                {
                  "startDate": "2026-09-06",
                  "finishDate": "2026-09-08",
                  "partySize": 1
                }""";

        String created = this.mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "usr_1")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String tripId = created.split("\"tripId\":\"")[1].split("\"")[0];

        this.mockMvc.perform(get("/api/v1/trips/{tripId}", tripId).header("X-User-Id", "usr_stranger"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));
    }
}
