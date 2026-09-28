package com.gabolle.backend.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import tools.jackson.databind.ObjectMapper;
import com.gabolle.backend.common.json.JsonPayloads;
import com.gabolle.backend.common.privacy.SensitivePayloadGuard;
import com.gabolle.backend.event.application.BehaviorConsent;
import com.gabolle.backend.event.application.OutboxAppendCommand;
import com.gabolle.backend.event.application.OutboxService;
import com.gabolle.backend.event.domain.EventOutbox;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.event.domain.Producer;
import com.gabolle.backend.event.repository.EventOutboxRepository;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.repository.AppUserRepository;

/**
 * 행동 개인화를 끈 사람의 이벤트가 <b>Outbox 입구에서</b> 막히는가 — S15P21E201-1096.
 *
 * <p>이 검사가 지키려는 것은 기능이 아니라 <b>구조</b>다. 전에는 이 규칙이 수집 API
 * ({@code EventIngestService}) 안에만 있었고, {@code OutboxService} 를 직접 부르는 경로는
 * 그것을 지나지 않았다. 그 경로가 <i>부르는 쪽에서 스스로 물어보는 것</i>으로 막혀 있었는데,
 * 그것은 그때 있던 경로 하나를 막을 뿐이고 <b>다음 사람은 또 모른다.</b>
 *
 * <p>그래서 여기서는 {@code EventIngestService} 를 <b>일부러 안 쓴다.</b> 규칙을 모르는 새
 * 호출자가 입구를 직접 두드렸을 때 무슨 일이 나는지가 이 티켓의 값어치다.
 */
class OutboxConsentTest {

	private static final Instant NOW = Instant.parse("2026-09-23T00:00:00Z");

	private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

	private EventOutboxRepository repository;

	private AppUserRepository users;

	private OutboxService service;

	@BeforeEach
	void setUp() {
		this.repository = mock(EventOutboxRepository.class);
		this.users = mock(AppUserRepository.class);
		when(this.repository.findById(any())).thenReturn(Optional.empty());
		when(this.repository.save(any())).thenAnswer(call -> call.getArgument(0));
		this.service = newService(new BehaviorConsent(this.users));
	}

	private OutboxService newService(BehaviorConsent consent) {
		@SuppressWarnings("unchecked")
		ObjectProvider<BehaviorConsent> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(consent);
		return new OutboxService(this.repository, new JsonPayloads(new ObjectMapper()), new SensitivePayloadGuard(),
				provider, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private void givenPersonalization(PersonalizationMode mode) {
		when(this.users.findPersonalizationMode(any())).thenReturn(Optional.ofNullable(mode));
	}

	private OutboxAppendCommand command(EventType type, UUID userId) {
		return new OutboxAppendCommand(UUID.randomUUID(), type.wireName(), 1, "trip",
				UUID.randomUUID(), "p", Map.of("k", "v"), OffsetDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC)),
				UUID.randomUUID(), userId, null, Producer.SERVER);
	}

	@Test
	@DisplayName("🔴 껐으면 행동 신호는 입구에서 막힌다 — 부르는 쪽이 규칙을 몰라도 그렇다")
	void aBehaviorSignalIsBlockedAtTheEntrance() {
		givenPersonalization(PersonalizationMode.EXPLICIT_ONLY);

		OutboxService.AppendResult result =
				this.service.appendReportingDuplicate(command(EventType.ITINERARY_REMOVE, USER));

		assertThat(result.outcome()).isEqualTo(OutboxService.Outcome.NOT_COLLECTED);
		assertThat(result.event()).as("안 적었는데 행을 돌려주면 부르는 쪽이 적힌 줄 안다").isEmpty();
		verify(this.repository, never()).save(any());
	}

	@Test
	@DisplayName("🔴 껐어도 «운영 기록»은 그대로 적힌다 — 끊으면 그 사람의 장애를 조사할 수 없다")
	void anOperationalRecordIsStillWritten() {
		givenPersonalization(PersonalizationMode.EXPLICIT_ONLY);

		OutboxService.AppendResult result =
				this.service.appendReportingDuplicate(command(EventType.RECOMMENDATION_REQUESTED, USER));

		assertThat(result.outcome()).isEqualTo(OutboxService.Outcome.STORED);
		verify(this.repository).save(any());
	}

	@Test
	@DisplayName("켰으면 행동 신호가 적힌다 — 막는 쪽만 보면 «전부 막아도» 초록이다")
	void aBehaviorSignalIsWrittenWhenEnabled() {
		givenPersonalization(PersonalizationMode.BEHAVIOR_ENABLED);

		assertThat(this.service.appendReportingDuplicate(command(EventType.ITINERARY_REMOVE, USER)).outcome())
				.isEqualTo(OutboxService.Outcome.STORED);
	}

	@Test
	@DisplayName("🔴 사람은 있는데 계정을 못 찾으면 안 적는다 — 「모른다」를 「켜짐」으로 읽지 않는다")
	void anUnknownAccountIsNotTreatedAsConsent() {
		givenPersonalization(null);

		assertThat(this.service.appendReportingDuplicate(command(EventType.ITINERARY_REMOVE, USER)).outcome())
				.isEqualTo(OutboxService.Outcome.NOT_COLLECTED);
	}

	@Test
	@DisplayName("주인 없는 이벤트는 적는다 — 이미 익명이라 막아도 지켜지는 것이 없고 집계만 사라진다")
	void anAnonymousEventIsStillWritten() {
		assertThat(this.service.appendReportingDuplicate(command(EventType.ITINERARY_REMOVE, null)).outcome())
				.isEqualTo(OutboxService.Outcome.STORED);
	}

	@Test
	@DisplayName("🔴 동의를 «물어볼 수 없으면» 행동 신호를 안 적는다 — 확인 못 한 채 적지 않는다")
	void aMissingConsentBeanBlocksBehaviorSignals() {
		OutboxService withoutConsent = newService(null);

		assertThat(withoutConsent.appendReportingDuplicate(command(EventType.ITINERARY_REMOVE, USER)).outcome())
				.isEqualTo(OutboxService.Outcome.NOT_COLLECTED);
		assertThat(withoutConsent.appendReportingDuplicate(command(EventType.RECOMMENDATION_REQUESTED, USER))
				.outcome()).as("운영 기록은 동의와 무관하다").isEqualTo(OutboxService.Outcome.STORED);
	}

	@Test
	@DisplayName("🔴 모르는 종류는 «조용히 통과»시키지 않는다 — 넘기면 이 검사가 있으나 마나다")
	void anUnknownEventTypeIsNotSilentlyLetThrough() {
		OutboxAppendCommand unknown = new OutboxAppendCommand(UUID.randomUUID(), "shiny_new_signal", 1, "trip",
				UUID.randomUUID(), "p", Map.of("k", "v"), OffsetDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC)),
				UUID.randomUUID(), USER, null, Producer.SERVER);

		org.assertj.core.api.Assertions
				.assertThatThrownBy(() -> this.service.appendReportingDuplicate(unknown))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("shiny_new_signal");
		verify(this.repository, never()).save(any());
	}

	@Test
	@DisplayName("멱등은 그대로다 — 같은 eventId 로 두 번 와도 행은 하나고, 두 번째는 DUPLICATE 다")
	void anExistingEventIsReportedAsDuplicate() {
		givenPersonalization(PersonalizationMode.BEHAVIOR_ENABLED);
		when(this.repository.findById(any())).thenReturn(Optional.of(mock(EventOutbox.class)));

		OutboxService.AppendResult result =
				this.service.appendReportingDuplicate(command(EventType.ITINERARY_REMOVE, USER));

		assertThat(result.outcome()).isEqualTo(OutboxService.Outcome.DUPLICATE);
		verify(this.repository, never()).save(any());
	}
}
