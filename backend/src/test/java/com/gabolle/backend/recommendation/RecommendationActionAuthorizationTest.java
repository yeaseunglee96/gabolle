package com.gabolle.backend.recommendation;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.recommendation.application.RecommendationActionService;
import com.gabolle.backend.recommendation.domain.RecommendationPlaceAction;
import com.gabolle.backend.recommendation.presentation.RecommendationActionController;
import com.gabolle.backend.recommendation.presentation.RecommendationJobExceptionHandler;
import com.gabolle.backend.recommendation.repository.RecommendationPlaceActionRepository;
import com.gabolle.backend.trip.application.TripQueryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DB 없이 도는 슬라이스다. 앞쪽은 경로의 인가와 경계를, 뒤쪽은 덮어쓰기 규칙을 본다 —
 * 뒤쪽은 저장소를 mock 으로 세워 서비스만 직접 부른다.
 */
class RecommendationActionAuthorizationTest {

	private RecommendationActionService service;
	private MockMvc mockMvc;

	private final UUID ownerId = UUID.randomUUID();
	private final String tripId = UUID.randomUUID().toString();
	private final String placeId = UUID.randomUUID().toString();

	@BeforeEach
	void setUp() {
		this.service = mock(RecommendationActionService.class);
		this.mockMvc = MockMvcBuilders.standaloneSetup(new RecommendationActionController(this.service))
				.setControllerAdvice(new RecommendationJobExceptionHandler())
				.build();
	}

	private static Authentication principal(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
	}

	// ── 인가와 경계 ──────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 남의 여행의 판단은 404 — 추천 요청과 같은 관문을 지난다")
	void listHidesOthersTrip() throws Exception {
		when(this.service.list(this.tripId, this.ownerId.toString()))
				.thenThrow(new TripQueryService.TripNotFoundException(this.tripId));

		this.mockMvc.perform(get("/api/v1/trips/{tripId}/recommendation-actions", this.tripId)
						.principal(principal(this.ownerId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));
	}

	/** 아직 아무것도 안 눌렀다를 404 로 답하면 화면이 없는 여행과 구분할 수 없다. */
	@Test
	@DisplayName("🔴 아직 아무것도 안 누른 내 여행은 빈 목록이다 — 404 가 아니다")
	void emptyTripIsEmptyListNotNotFound() throws Exception {
		when(this.service.list(this.tripId, this.ownerId.toString()))
				.thenReturn(new RecommendationActionService.Page(List.of(), false));

		this.mockMvc.perform(get("/api/v1/trips/{tripId}/recommendation-actions", this.tripId)
						.principal(principal(this.ownerId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isArray())
				.andExpect(jsonPath("$.data.items").isEmpty())
				.andExpect(jsonPath("$.data.count").value(0));
	}

	@Test
	@DisplayName("판단을 적으면 적힌 값이 그대로 돌아온다")
	void putReturnsWhatWasWritten() throws Exception {
		when(this.service.put(this.tripId, this.ownerId.toString(), this.placeId,
				RecommendationPlaceAction.Action.EXCLUDED))
				.thenReturn(action(RecommendationPlaceAction.Action.EXCLUDED, this.ownerId));

		this.mockMvc.perform(put("/api/v1/trips/{tripId}/recommendation-actions/{placeId}", this.tripId, this.placeId)
						.contentType("application/json")
						.content("{\"action\":\"EXCLUDED\"}")
						.principal(principal(this.ownerId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.placeId").value(this.placeId))
				.andExpect(jsonPath("$.data.action").value("EXCLUDED"))
				.andExpect(jsonPath("$.data.decidedByUserId").value(this.ownerId.toString()));
	}

	/** 판단은 여행의 것이라 동행자가 남긴 것도 그대로 온다. */
	@Test
	@DisplayName("🔴 동행자가 남긴 판단도 목록에 함께 온다 — 초대로 들어온 사람들이 함께 본다")
	void listIncludesWhatOtherMembersDecided() throws Exception {
		UUID companion = UUID.randomUUID();
		when(this.service.list(this.tripId, this.ownerId.toString()))
				.thenReturn(new RecommendationActionService.Page(
						List.of(action(RecommendationPlaceAction.Action.EXCLUDED, companion)), false));

		this.mockMvc.perform(get("/api/v1/trips/{tripId}/recommendation-actions", this.tripId)
						.principal(principal(this.ownerId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.count").value(1))
				.andExpect(jsonPath("$.data.items[0].action").value("EXCLUDED"))
				.andExpect(jsonPath("$.data.items[0].decidedByUserId").value(companion.toString()));
	}

	/**
	 * 판단 없는 요청을 받아 주면 무엇으로 적혔는지 아무도 모르는 행이 생긴다. 400 으로 막고
	 * 서비스는 부르지 않는다.
	 */
	@Test
	@DisplayName("🔴 action 이 없으면 400 이고 서비스를 부르지도 않는다")
	void putWithoutActionIsRejectedBeforeReachingService() throws Exception {
		this.mockMvc.perform(put("/api/v1/trips/{tripId}/recommendation-actions/{placeId}", this.tripId, this.placeId)
						.contentType("application/json")
						.content("{}")
						.principal(principal(this.ownerId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("RECOMMENDATION_JOB_VALIDATION_FAILED"));

		verify(this.service, never()).put(any(), any(), any(), any());
	}

	@Test
	@DisplayName("판단을 거두면 204 — 본문이 없다")
	void removeReturnsNoContent() throws Exception {
		this.mockMvc.perform(delete("/api/v1/trips/{tripId}/recommendation-actions/{placeId}", this.tripId,
						this.placeId).principal(principal(this.ownerId)))
				.andExpect(status().isNoContent());

		verify(this.service).remove(this.tripId, this.ownerId.toString(), this.placeId);
	}

	private RecommendationPlaceAction action(RecommendationPlaceAction.Action value, UUID decidedBy) {
		return RecommendationPlaceAction.of(UUID.randomUUID(), UUID.fromString(this.tripId),
				UUID.fromString(this.placeId), value, decidedBy, OffsetDateTime.now(ZoneOffset.UTC));
	}

	// ── 덮어쓰기 규칙 (서비스를 직접 부른다) ──────────────────────────────────

	/**
	 * 동행자가 이미 정해 둔 판단을 내가 바꾸는 경우다. 새 행이 생기면
	 * {@code uk_recommendation_place_action}(여행+장소에 행 하나)에 걸려 저장이 실패한다.
	 *
	 * 여기서는 서비스가 무엇을 시켰는지만 본다. 행이 하나로 유지되는가는 가짜 리포지토리로
	 * 확인할 수 없어 진짜 PostgreSQL 위에서 따로 본다 ({@code EndpointGuardsPostgresTest}).
	 */
	@Test
	@DisplayName("판단을 적을 때 새로 만들지 않고 업서트 한 문장으로 맡긴다 — 정한 사람도 함께 넘긴다")
	void putDelegatesToASingleUpsertStatement() {
		RecommendationPlaceActionRepository repository = mock(RecommendationPlaceActionRepository.class);
		TripQueryService tripQueryService = mock(TripQueryService.class);
		RecommendationActionService real = new RecommendationActionService(tripQueryService, repository,
				Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC));

		RecommendationPlaceAction written = action(RecommendationPlaceAction.Action.EXCLUDED, this.ownerId);
		when(repository.findByTripIdAndPlaceId(UUID.fromString(this.tripId), UUID.fromString(this.placeId)))
				.thenReturn(Optional.of(written));

		RecommendationPlaceAction result = real.put(this.tripId, this.ownerId.toString(), this.placeId,
				RecommendationPlaceAction.Action.EXCLUDED);

		verify(repository).upsert(any(), eq(UUID.fromString(this.tripId)), eq(UUID.fromString(this.placeId)),
				eq("EXCLUDED"), eq(this.ownerId), any());
		verify(repository, never()).save(any());

		assertThat(result.getAction()).isEqualTo(RecommendationPlaceAction.Action.EXCLUDED);
		// 마지막에 정한 사람이 바뀐다 — 공유 상태에서 "누가 뺐나" 에 답할 수 있어야 한다.
		assertThat(result.getDecidedByUserId()).isEqualTo(this.ownerId);
	}

	/**
	 * 넣은 직후 그 행을 도로 못 읽는 것은 서버 쪽 불변식이 깨진 것이다. 400 을 주면 사용자는
	 * 자기 입력을 고치려 들고 서버 오류 그래프에서는 이 사고가 안 보인다.
	 */
	@Test
	@DisplayName("적은 뒤 도로 못 읽으면 400 이 아니라 서버 오류로 올린다")
	void aFailedReadBackIsNotTreatedAsABadRequest() {
		RecommendationPlaceActionRepository repository = mock(RecommendationPlaceActionRepository.class);
		TripQueryService tripQueryService = mock(TripQueryService.class);
		RecommendationActionService real = new RecommendationActionService(tripQueryService, repository,
				Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC));

		when(repository.findByTripIdAndPlaceId(any(), any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> real.put(this.tripId, this.ownerId.toString(), this.placeId,
				RecommendationPlaceAction.Action.SAVED))
				.isInstanceOf(RecommendationActionService.WriteReadBackFailedException.class)
				.as("IllegalArgumentException 이면 어드바이스가 400 으로 내린다")
				.isNotInstanceOf(IllegalArgumentException.class);
	}
}
