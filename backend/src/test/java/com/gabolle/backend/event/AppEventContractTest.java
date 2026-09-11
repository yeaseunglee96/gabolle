package com.gabolle.backend.event;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.event.application.EventIngestService;
import com.gabolle.backend.event.application.OutboxAppendCommand;
import com.gabolle.backend.event.application.OutboxService;
import com.gabolle.backend.event.domain.EventOutbox;
import com.gabolle.backend.event.domain.Producer;
import com.gabolle.backend.event.presentation.EventIngestController;
import com.gabolle.backend.event.presentation.EventIngestExceptionHandler;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 앱이 <b>실제로 보내는 본문 그대로</b> 서버가 받는가 — S15P21E201-735.
 *
 * <h2>🔴 이 테스트가 있는 이유</h2>
 * MR !326(S15P21E201-717·-718)이 앱에 행동 기록 전송을 넣었는데 <b>서버가 그 요청을 하나도
 * 안 받았다.</b> 막은 것은 버그가 아니라 계약이었다.
 *
 * <ol>
 * <li>{@code requestId} 가 모든 이벤트에 필수인데 그 값을 앱에 주는 응답이 없었다</li>
 * <li>{@code place_like}·{@code place_dislike}·{@code place_visit} 가 서버 전용이었다</li>
 * <li>여행 축 이벤트는 {@code tripId} 없이 안 적히는데 홈·장소 상세는 여행 밖 화면이다</li>
 * </ol>
 *
 * <p>🔴 <b>본문을 손으로 다시 쓰지 않는다.</b> 아래 본문은 {@code frontend/src/analytics/appEvents.ts}
 * 가 만드는 모양 그대로다 — 앱이 안 보내는 키({@code tripId}·{@code requestId}·{@code userId})는
 * {@code JSON.stringify} 가 지우므로 여기서도 <b>키 자체가 없다.</b> 값을 {@code null} 로 넣으면
 * 그건 앱이 보내는 것과 다른 요청이고, 그런 테스트는 초록이어도 아무것도 증명하지 않는다.
 *
 * <p>🔴 <b>HTTP 층을 지난다.</b> 서비스를 직접 부르면 Bean Validation 과 컨트롤러의 신원 판정을
 * 건너뛴다 — 이 저장소가 같은 함정으로 이미 여러 번 헛돌았다({@code EventSubjectAuthorizationTest}
 * 의 주석 참고). {@link OutboxService} 만 가짜로 두고 그 아래로 무엇이 갔는지를 확인한다.
 */
class AppEventContractTest {

	/** 앱 본문의 {@code occurredAt}(2026-09-07 09:00 KST = 00:00Z)보다 뒤여야 한다. */
	private static final Instant NOW = Instant.parse("2026-09-08T00:00:00Z");

	private OutboxService outboxService;

	private MockMvc mockMvc;

	private final UUID me = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		this.outboxService = mock(OutboxService.class);
		given(this.outboxService.appendReportingDuplicate(any()))
				.willReturn(new OutboxService.AppendResult(mock(EventOutbox.class), true));

		// 🔴 이 계약 검사는 <b>앱이 보내는 본문의 모양</b>을 잰다. 행동 기반 개인화가 켜져
		//    있는 사람으로 고정해 두지 않으면, 본문이 맞는데도 수집 차단(S15P21E201-549)에
		//    걸려 202 만 보고 통과해 버린다 — 모양이 깨져도 초록인 검사가 된다.
		AppUserRepository users = mock(AppUserRepository.class);
		given(users.findPersonalizationMode(any())).willReturn(Optional.of(PersonalizationMode.BEHAVIOR_ENABLED));

		EventIngestService service = new EventIngestService(this.outboxService, users,
				Clock.fixed(NOW, ZoneOffset.UTC));
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(new EventIngestController(service))
				.setControllerAdvice(new EventIngestExceptionHandler())
				.build();
	}

	// ── 앱이 보내는 네 가지 ──────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 홈 하트 — 여행도 추천 요청도 없이 저장했다. 202 로 받고 사용자 축에 적는다")
	void homeScreenSaveIsAccepted() throws Exception {
		this.mockMvc.perform(post("/api/v1/events")
				.contentType(MediaType.APPLICATION_JSON)
				.principal(asMe())
				.content("""
						{"eventId":"%s","eventType":"place_like","eventVersion":1,
						 "occurredAt":"2026-09-07T09:00:00.000+09:00",
						 "payload":{"place_id":"seomyeon-1","surface":"home"}}
						""".formatted(UUID.randomUUID())))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.accepted").value(true));

		OutboxAppendCommand command = captureCommand();
		assertThat(command.eventType()).isEqualTo("place_like");
		assertThat(command.aggregateType()).isEqualTo("user");
		assertThat(command.aggregateId()).isEqualTo(this.me);
		assertThat(command.userId()).isEqualTo(this.me);
		assertThat(command.requestId()).isNull();
		assertThat(command.tripId()).isNull();
		assertThat(command.producer()).isEqualTo(Producer.CLIENT);
		assertThat(command.payload()).containsEntry("surface", "home");
	}

	@Test
	@DisplayName("장소 상세 하트 — 홈과 같은 모양이다. surface 만 다르다")
	void placeDetailSaveIsAccepted() throws Exception {
		this.mockMvc.perform(post("/api/v1/events")
				.contentType(MediaType.APPLICATION_JSON)
				.principal(asMe())
				.content("""
						{"eventId":"%s","eventType":"place_like","eventVersion":1,
						 "occurredAt":"2026-09-07T09:00:00.000+09:00",
						 "payload":{"place_id":"haeundae-1","surface":"place_detail"}}
						""".formatted(UUID.randomUUID())))
				.andExpect(status().isAccepted());

		assertThat(captureCommand().payload()).containsEntry("surface", "place_detail");
	}

	@Test
	@DisplayName("추천 화면 저장·제외 — tripId 는 있고 requestId 는 아직 없다. 그래도 받는다")
	void recommendationScreenExcludeIsAccepted() throws Exception {
		UUID tripId = UUID.randomUUID();

		this.mockMvc.perform(post("/api/v1/events")
				.contentType(MediaType.APPLICATION_JSON)
				.principal(asMe())
				.content("""
						{"eventId":"%s","eventType":"place_dislike","eventVersion":1,"tripId":"%s",
						 "occurredAt":"2026-09-07T09:00:00.000+09:00",
						 "payload":{"place_id":"course-2","surface":"recommendations"}}
						""".formatted(UUID.randomUUID(), tripId)))
				.andExpect(status().isAccepted());

		OutboxAppendCommand command = captureCommand();
		assertThat(command.eventType()).isEqualTo("place_dislike");
		// 축은 사용자다 — 같은 이벤트가 여행 밖에서도 나므로 한 종류에 한 축을 준다.
		// 여행과의 관계는 trip_id 실컬럼이 그대로 들고 있어서 잃는 조인이 없다.
		assertThat(command.aggregateId()).isEqualTo(this.me);
		assertThat(command.tripId()).isEqualTo(tripId);
	}

	@Test
	@DisplayName("🔴 체크인 후기 — 1~5 눈금과 태그 배열이 payload 에 그대로 실린다")
	void checkInReviewIsAcceptedWithItsRatingAndChips() throws Exception {
		UUID tripId = UUID.randomUUID();

		this.mockMvc.perform(post("/api/v1/events")
				.contentType(MediaType.APPLICATION_JSON)
				.principal(asMe())
				.content("""
						{"eventId":"%s","eventType":"place_visit","eventVersion":1,"tripId":"%s",
						 "occurredAt":"2026-09-07T09:00:00.000+09:00",
						 "payload":{"rating":5,"chips":["sea","alley","accurate"],
						            "accuracy":"accurate","has_note":false}}
						""".formatted(UUID.randomUUID(), tripId)))
				.andExpect(status().isAccepted());

		OutboxAppendCommand command = captureCommand();
		assertThat(command.aggregateType()).isEqualTo("trip");
		assertThat(command.aggregateId()).isEqualTo(tripId);
		assertThat(command.payload()).containsEntry("rating", 5).containsEntry("has_note", false);
		assertThat(command.payload().get("chips")).isEqualTo(List.of("sea", "alley", "accurate"));
	}

	@Test
	@DisplayName("눈금 1 도 5 와 똑같이 받는다 — 낮은 점수가 조용히 버려지면 안 된다")
	void theLowestRatingIsAcceptedToo() throws Exception {
		this.mockMvc.perform(post("/api/v1/events")
				.contentType(MediaType.APPLICATION_JSON)
				.principal(asMe())
				.content("""
						{"eventId":"%s","eventType":"place_visit","eventVersion":1,"tripId":"%s",
						 "occurredAt":"2026-09-07T09:00:00.000+09:00",
						 "payload":{"rating":1,"chips":[],"accuracy":"off","has_note":true}}
						""".formatted(UUID.randomUUID(), UUID.randomUUID())))
				.andExpect(status().isAccepted());

		assertThat(captureCommand().payload()).containsEntry("rating", 1);
	}

	// ── 앱이 requestId 를 알게 된 뒤 ─────────────────────────────────────────

	@Test
	@DisplayName("추천 응답에서 받은 requestId 를 실어 보내면 그 칸에 그대로 적힌다")
	void aKnownRequestIdIsCarriedIntoItsColumn() throws Exception {
		UUID requestId = UUID.randomUUID();

		this.mockMvc.perform(post("/api/v1/events")
				.contentType(MediaType.APPLICATION_JSON)
				.principal(asMe())
				.content("""
						{"eventId":"%s","eventType":"place_like","eventVersion":1,"tripId":"%s","requestId":"%s",
						 "occurredAt":"2026-09-07T09:00:00.000+09:00",
						 "payload":{"place_id":"course-1","surface":"recommendations"}}
						""".formatted(UUID.randomUUID(), UUID.randomUUID(), requestId)))
				.andExpect(status().isAccepted());

		assertThat(captureCommand().requestId()).isEqualTo(requestId);
	}

	// ── 풀지 않은 것 ────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 노출 이벤트는 여전히 requestId 가 필수다 — 그 값이 이 이벤트의 대상 자체다")
	void impressionStillRequiresARequestId() throws Exception {
		this.mockMvc.perform(post("/api/v1/events")
				.contentType(MediaType.APPLICATION_JSON)
				.principal(asMe())
				.content("""
						{"eventId":"%s","eventType":"recommendation_impression","eventVersion":1,
						 "occurredAt":"2026-09-07T09:00:00.000+09:00","payload":{}}
						""".formatted(UUID.randomUUID())))
				// 🔴 400 이어야 한다. 500 이면 앱은 "내가 잘못 보냈다" 와 "서버가 죽었다" 를 못 가른다
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("EVENT_REJECTED"));
	}

	@Test
	@DisplayName("🔴 서버만 만들 수 있는 이벤트는 앱이 보내면 여전히 거부된다 (DR-13)")
	void serverOnlyEventsAreStillRejected() throws Exception {
		this.mockMvc.perform(post("/api/v1/events")
				.contentType(MediaType.APPLICATION_JSON)
				.principal(asMe())
				.content("""
						{"eventId":"%s","eventType":"trip_created","eventVersion":1,"tripId":"%s",
						 "occurredAt":"2026-09-07T09:00:00.000+09:00","payload":{}}
						""".formatted(UUID.randomUUID(), UUID.randomUUID())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("EVENT_REJECTED"));
	}

	@Test
	@DisplayName("모르는 이벤트 이름은 400 이다 — 지어낸 이름이 조용히 쌓이지 않는다")
	void unknownEventNamesAreRejected() throws Exception {
		this.mockMvc.perform(post("/api/v1/events")
				.contentType(MediaType.APPLICATION_JSON)
				.principal(asMe())
				.content("""
						{"eventId":"%s","eventType":"place_super_like","eventVersion":1,
						 "occurredAt":"2026-09-07T09:00:00.000+09:00","payload":{}}
						""".formatted(UUID.randomUUID())))
				.andExpect(status().isBadRequest());
	}

	private OutboxAppendCommand captureCommand() {
		ArgumentCaptor<OutboxAppendCommand> captor = ArgumentCaptor.forClass(OutboxAppendCommand.class);
		verify(this.outboxService).appendReportingDuplicate(captor.capture());
		return captor.getValue();
	}

	private Authentication asMe() {
		return new UsernamePasswordAuthenticationToken(this.me.toString(), null, List.of());
	}
}
