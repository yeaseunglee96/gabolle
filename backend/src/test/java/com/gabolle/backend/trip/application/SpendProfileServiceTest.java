package com.gabolle.backend.trip.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gabolle.backend.event.application.EventIngestService;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.TripRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * S15P21E201-709 — 계정 기본 SPEND_PROFILE 읽기·쓰기.
 *
 * <p>🔴 {@link SpendProfileService#put} 이 다른 차원의 답을 지우지 않는지가 이 테스트의
 * 핵심이다 — {@code PreferenceDefaultsService.replace} 를 그대로 썼다면(전체 교체) 이미
 * 저장된 다른 차원이 조용히 사라진다.
 */
class SpendProfileServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");
	private static final UUID USER_ID = UUID.randomUUID();

	private TripRepository repository;
	private EventIngestService eventIngestService;
	private SpendProfileService service;

	@BeforeEach
	void setUp() {
		this.repository = mock(TripRepository.class);
		this.eventIngestService = mock(EventIngestService.class);
		this.service = new SpendProfileService(this.repository, this.eventIngestService,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private PreferenceSnapshot snapshotWith(List<PreferenceSnapshot.PreferenceAnswer> answers) {
		return new PreferenceSnapshot(UUID.randomUUID().toString(), null, 1, answers,
				PersonalizationScope.USER, List.of(), NOW);
	}

	@Test
	@DisplayName("저장한 적 없으면 find 는 비어 있다")
	void findIsEmptyWhenNeverSaved() {
		given(this.repository.findUserDefaults(USER_ID.toString())).willReturn(Optional.empty());

		assertThat(this.service.find(USER_ID)).isEmpty();
	}

	@Test
	@DisplayName("put 이후 find 로 그 값을 되찾는다")
	void findReturnsWhatWasPut() {
		given(this.repository.findUserDefaults(USER_ID.toString())).willReturn(Optional.empty());
		this.service.put(USER_ID, "\"MODERATE\"", "SELECTED", UUID.randomUUID());

		ArgumentCaptor<List<PreferenceSnapshot.PreferenceAnswer>> captor = ArgumentCaptor.forClass(List.class);
		verify(this.repository).saveUserDefaults(eq(USER_ID.toString()), captor.capture(), eq(NOW));

		assertThat(captor.getValue()).hasSize(1);
		assertThat(captor.getValue().get(0).dimension()).isEqualTo("SPEND_PROFILE");
		assertThat(captor.getValue().get(0).valueJson()).isEqualTo("\"MODERATE\"");
		assertThat(captor.getValue().get(0).status()).isEqualTo(PreferenceSnapshot.AnswerStatus.SELECTED);
	}

	@Test
	@DisplayName("🔴 다른 차원의 답이 이미 있으면 SPEND_PROFILE 만 바꾸고 나머지는 그대로 옮긴다")
	void preservesOtherDimensionsWhenReplacing() {
		PreferenceSnapshot.PreferenceAnswer category = new PreferenceSnapshot.PreferenceAnswer(
				"CATEGORY", "\"CAFE\"", PreferenceSnapshot.AnswerStatus.SELECTED);
		PreferenceSnapshot.PreferenceAnswer oldSpend = new PreferenceSnapshot.PreferenceAnswer(
				"SPEND_PROFILE", "\"CHEAP\"", PreferenceSnapshot.AnswerStatus.SELECTED);
		given(this.repository.findUserDefaults(USER_ID.toString()))
				.willReturn(Optional.of(snapshotWith(List.of(category, oldSpend))));

		this.service.put(USER_ID, "\"LUXURY\"", "SELECTED", UUID.randomUUID());

		ArgumentCaptor<List<PreferenceSnapshot.PreferenceAnswer>> captor = ArgumentCaptor.forClass(List.class);
		verify(this.repository).saveUserDefaults(eq(USER_ID.toString()), captor.capture(), any());

		assertThat(captor.getValue()).hasSize(2);
		assertThat(captor.getValue()).anySatisfy(a -> {
			assertThat(a.dimension()).isEqualTo("CATEGORY");
			assertThat(a.valueJson()).isEqualTo("\"CAFE\"");
		});
		assertThat(captor.getValue()).filteredOn(a -> a.dimension().equals("SPEND_PROFILE"))
				.hasSize(1)
				.allSatisfy(a -> assertThat(a.valueJson()).isEqualTo("\"LUXURY\""));
	}

	@Test
	@DisplayName("PREFERENCE_SET 이벤트를 dimension=SPEND_PROFILE payload 로 발행한다 — USER 축, tripId 없음")
	void emitsPreferenceSetEventOnUserAxis() {
		given(this.repository.findUserDefaults(USER_ID.toString())).willReturn(Optional.empty());

		this.service.put(USER_ID, "\"MODERATE\"", "SELECTED", UUID.randomUUID());

		verify(this.eventIngestService).recordFromServer(any(), eq(EventType.PREFERENCE_SET), eq(1),
				eq(USER_ID), isNull(), any(), eq(Map.of("dimension", "SPEND_PROFILE")));
	}

	@Test
	@DisplayName("🔴 answerStatus 를 모르면 예외 — 조용히 무시하지 않는다")
	void rejectsUnknownAnswerStatus() {
		given(this.repository.findUserDefaults(USER_ID.toString())).willReturn(Optional.empty());

		assertThatThrownBy(() -> this.service.put(USER_ID, "\"MODERATE\"", "MAYBE", UUID.randomUUID()))
				.isInstanceOf(IllegalArgumentException.class);

		verify(this.eventIngestService, never()).recordFromServer(any(), any(), anyInt(), any(), any(), any(), any());
	}
}
