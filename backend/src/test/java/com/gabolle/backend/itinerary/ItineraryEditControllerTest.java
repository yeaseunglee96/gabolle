package com.gabolle.backend.itinerary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gabolle.backend.itinerary.application.ItineraryEditService;
import com.gabolle.backend.itinerary.infra.InMemoryItineraryRepository;
import com.gabolle.backend.itinerary.presentation.ItineraryEditController;
import com.gabolle.backend.itinerary.presentation.ItineraryExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * ITN-03 을 HTTP 로 검증한다 — S15P21E201-313.
 *
 * <p>🔴 M1 완료 조건 ② 가 "409 충돌이 <b>실제로 재현된다</b>" 다.
 * 도메인 테스트({@code ItineraryVersionConflictTest})는 예외가 던져지는 것까지만 보고,
 * <b>그 예외가 HTTP 409 와 명세가 지정한 오류 코드로 바뀌는지는 보지 않는다.</b>
 * 그 사이에서 깨질 수 있어서 이 테스트가 따로 있다.
 *
 * <p>Spring 컨텍스트를 띄우지 않고 {@link MockMvcBuilders#standaloneSetup} 으로
 * 컨트롤러와 예외 처리기만 올린다. 🔴 {@code SecurityConfig} 가 {@code /actuator/health}
 * 외 전부를 차단하는데 그 파일은 auth 패키지(다른 사람 소유)라 손대지 않는다 —
 * 실제 서버에서 이 경로를 열어야 하는 것은 <b>별도 협업 항목</b>이다.
 */
class ItineraryEditControllerTest {

    private MockMvc mockMvc;
    private InMemoryItineraryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryItineraryRepository();
        repository.seed("itn_1", "trp_1", 5);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ItineraryEditController(new ItineraryEditService(repository)))
                .setControllerAdvice(new ItineraryExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("ITN-03 — 최신 판으로 고정하면 201 과 새 판 번호가 온다")
    void lockItemReturns201() throws Exception {
        mockMvc.perform(post("/api/v1/itineraries/{id}/items/{itemId}/lock", "itn_1", "item_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "usr_a")
                        .content("{\"baseVersion\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.version").value(6))
                .andExpect(jsonPath("$.data.baseVersion").value(5))
                .andExpect(jsonPath("$.data.operation").value("LOCK_ITEM"))
                .andExpect(jsonPath("$.data.createdBy").value("usr_a"))
                .andExpect(jsonPath("$.data.lockedItemId").value("item_1"))
                // API 명세 2.1 — 공통 envelope 에 meta.requestId 가 있어야 한다
                .andExpect(jsonPath("$.meta.requestId").exists())
                // 🔴 2026-09-03 — 팀 공용 ApiResponse 로 바꾼 뒤: 성공이면 error 는 명시적으로 null,
                //    시각은 meta(칸 없음)가 아니라 data.createdAt 에 실린다.
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.createdAt").exists());
    }

    /** 🔴 이 테스트가 M1 완료 조건 ② 를 증명한다. */
    @Test
    @DisplayName("🔴 ITN-03 — 낡은 판으로 고정하면 409 와 ITINERARY_VERSION_CONFLICT 가 온다")
    void staleBaseVersionReturns409() throws Exception {
        // 먼저 한 번 편집해서 최신을 6으로 올린다.
        mockMvc.perform(post("/api/v1/itineraries/{id}/items/{itemId}/lock", "itn_1", "item_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "usr_a")
                        .content("{\"baseVersion\":5}"))
                .andExpect(status().isCreated());

        // 화면이 아직 5를 보고 있다고 가정하고 다시 시도한다.
        mockMvc.perform(post("/api/v1/itineraries/{id}/items/{itemId}/lock", "itn_1", "item_2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "usr_b")
                        .content("{\"baseVersion\":5}"))
                .andExpect(status().isConflict())
                // 🔴 명세가 지정한 코드여야 한다. 서버가 임의로 정하면 FE·APP 이 못 받는다.
                .andExpect(jsonPath("$.error.code").value("ITINERARY_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.error.messageKey").value("error.itinerary.conflict"))
                // 🔴 최신 번호가 있어야 화면이 무엇으로 다시 불러올지 안다.
                .andExpect(jsonPath("$.error.details.latestVersion").value(6))
                .andExpect(jsonPath("$.error.details.attemptedBaseVersion").value(5))
                // 오류일 때 data 는 null 이다 (명세 2.1)
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("baseVersion 이 없으면 400 — 편집을 받을 수 없다 (API-09)")
    void missingBaseVersionIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/itineraries/{id}/items/{itemId}/lock", "itn_1", "item_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "usr_a")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
