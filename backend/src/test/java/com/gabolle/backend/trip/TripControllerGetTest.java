package com.gabolle.backend.trip;

import com.gabolle.backend.user.support.ConsentGuards;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        Clock clock = Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC);
        TripCreationService creationService = newCreationService(repository, clock);
        TripQueryService queryService = new TripQueryService(repository);

        TripController controller = new TripController(creationService, queryService,
                new TripDeletionService(repository, clock));
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new TripExceptionHandler())
                .build();
    }

    /**
     * 🔴 S15P21E201-610 — 헤더가 아니라 인증 principal 로 사용자를 정한다.
     * {@code MockMvc.principal(Principal)} 은 Spring MVC 의 기본
     * {@code PrincipalMethodArgumentResolver} 를 그대로 타므로, Security 필터 체인을
     * 안 올리는 {@code standaloneSetup} 에서도 {@code Authentication} 파라미터가 채워진다.
     */
    private static Authentication asUser(String userId) {
        return new TestingAuthenticationToken(userId, null);
    }

    @Test
    @DisplayName("만든 사람이 조회하면 200 과 보낸 조건이 그대로 나온다")
    void ownerCanReadTheTripBack() throws Exception {
        String userId = UUID.randomUUID().toString();
        String body = """
                {
                  "startDate": "2026-09-06",
                  "finishDate": "2026-09-08",
                  "budgetKrw": 300000,
                  "partySize": 2,
                  "originLat": 35.1587,
                  "originLng": 129.1604,
                  "timezone": "Asia/Seoul",
                  "constraints": [
                    { "type": "MOBILITY", "constraintKey": "MAX_WALKING_METERS", "severity": "HARD", "operator": "LTE", "threshold": 5000.0, "answerStatus": "SELECTED" }
                  ]
                }""";

        String created = this.mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .principal(asUser(userId))
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String tripId = created.split("\"tripId\":\"")[1].split("\"")[0];

        this.mockMvc.perform(get("/api/v1/trips/{tripId}", tripId).principal(asUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trip.tripId").value(tripId))
                .andExpect(jsonPath("$.data.trip.budgetKrw").value(300000))
                .andExpect(jsonPath("$.data.constraints[0].type").value("MOBILITY"));
    }

    @Test
    @DisplayName("없는 여행을 조회하면 404 TRIP_NOT_FOUND")
    void unknownTripReturns404() throws Exception {
        this.mockMvc.perform(get("/api/v1/trips/{tripId}", "trp_unknown").principal(asUser(UUID.randomUUID().toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));
    }

    @Test
    @DisplayName("🔴 회원이 아닌 사람이 조회해도 404 — 403 이 아니다")
    void nonMemberAlsoGets404NotForbidden() throws Exception {
        String owner = UUID.randomUUID().toString();
        String stranger = UUID.randomUUID().toString();
        String body = """
                {
                  "startDate": "2026-09-06",
                  "finishDate": "2026-09-08",
                  "partySize": 1,
                  "originLat": 35.1587,
                  "originLng": 129.1604
                }""";

        String created = this.mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .principal(asUser(owner))
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String tripId = created.split("\"tripId\":\"")[1].split("\"")[0];

        this.mockMvc.perform(get("/api/v1/trips/{tripId}", tripId).principal(asUser(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));
    }

    /** S15P21E201-547 — 생성자에 PreferenceDefaultsService 가 붙어 한 자리에 모았다. */
    private static TripCreationService newCreationService(InMemoryTripRepository repository, Clock clock) {
        return new TripCreationService(repository, clock, new PreferenceDefaultsService(repository, clock), ConsentGuards.granting());
    }
}
