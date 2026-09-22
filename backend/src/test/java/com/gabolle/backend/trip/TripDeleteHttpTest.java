package com.gabolle.backend.trip;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.trip.application.TripCreationService;
import com.gabolle.backend.trip.application.TripDeletionService;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.presentation.TripController;
import com.gabolle.backend.trip.presentation.TripExceptionHandler;

/**
 * {@code DELETE /api/v1/trips/{tripId}} 의 HTTP 경계만 잰다 — 경로와 메서드가 붙어 있는지,
 * 서비스가 던진 것이 어떤 상태 코드로 바뀌는지. 규칙 자체는 {@link TripDeleteIntegrationTest} 가 잰다.
 *
 * <p>서비스는 일부러 가짜다. 진짜를 쓰면 규칙이 바뀔 때마다 두 파일이 함께 빨개져 어느 층이
 * 깨졌는지 알 수 없다.
 */
class TripDeleteHttpTest {

	private MockMvc mockMvc;

	private TripDeletionService deletionService;

	@BeforeEach
	void setUp() {
		this.deletionService = Mockito.mock(TripDeletionService.class);
		TripController controller = new TripController(
				Mockito.mock(TripCreationService.class),
				Mockito.mock(TripQueryService.class),
				this.deletionService);
		this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new TripExceptionHandler())
				.build();
	}

	@Test
	@DisplayName("지우면 204 이고 본문이 없다")
	void deleteReturnsNoContent() throws Exception {
		String tripId = UUID.randomUUID().toString();
		String userId = UUID.randomUUID().toString();
		doNothing().when(this.deletionService).delete(any(), any());

		this.mockMvc.perform(delete("/api/v1/trips/{tripId}", tripId).principal(asUser(userId)))
				.andExpect(status().isNoContent())
				.andExpect(result -> {
					if (!result.getResponse().getContentAsString().isEmpty()) {
						throw new AssertionError("204 응답에 본문이 실렸다");
					}
				});

		// 요청 본문이나 쿼리 파라미터가 아니라 인증 principal 로 사용자를 정한다 — 누가 지우는지를
		// 클라이언트가 정하게 하면 아무나 남의 이름으로 지울 수 있다.
		verify(this.deletionService).delete(eq(tripId), eq(userId));
	}

	@Test
	@DisplayName("회원이지만 소유자가 아니면 403 TRIP_FORBIDDEN")
	void companionGetsForbidden() throws Exception {
		doThrow(new TripDeletionService.TripDeleteForbiddenException())
				.when(this.deletionService).delete(any(), any());

		this.mockMvc.perform(delete("/api/v1/trips/{tripId}", UUID.randomUUID().toString())
						.principal(asUser(UUID.randomUUID().toString())))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("TRIP_FORBIDDEN"));
	}

	@Test
	@DisplayName("🔴 없는 여행과 남의 여행은 둘 다 404 TRIP_NOT_FOUND")
	void unknownAndSomeoneElsesLookTheSame() throws Exception {
		doThrow(new TripQueryService.TripNotFoundException("trp_unknown"))
				.when(this.deletionService).delete(any(), any());

		this.mockMvc.perform(delete("/api/v1/trips/{tripId}", UUID.randomUUID().toString())
						.principal(asUser(UUID.randomUUID().toString())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));
	}

	private static Authentication asUser(String userId) {
		return new TestingAuthenticationToken(userId, null);
	}
}
