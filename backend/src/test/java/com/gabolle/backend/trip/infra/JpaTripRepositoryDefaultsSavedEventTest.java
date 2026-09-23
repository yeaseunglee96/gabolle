package com.gabolle.backend.trip.infra;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.UserPreferenceDefaultsSaved;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 계정 기본 취향을 쓰는 문이 「새 판이 생겼다」를 알리는가 (S15P21E201-1515).
 *
 * <p>설문과 씀씀이가 모두 {@code saveUserDefaults} 를 지난다. 여기서 안 알리면 새 사용자는 다시
 * 새벽 배치까지 설문 없이 추천을 받는다 — 그리고 그것은 어떤 시험도 빨개지지 않는 종류의 퇴행이다.
 */
class JpaTripRepositoryDefaultsSavedEventTest {

	@Test
	@DisplayName("계정 기본 취향을 저장하면 그 사람의 판을 다시 접으라고 알린다")
	void savingUserDefaultsAnnouncesIt() {
		ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
		JpaTripRepository repository = new JpaTripRepository(mock(TripJpaRepository.class),
				mock(TripMemberJpaRepository.class), mock(PreferenceSnapshotJpaRepository.class),
				mock(PreferenceAnswerJpaRepository.class), mock(ConstraintSnapshotJpaRepository.class),
				mock(ConstraintAnswerJpaRepository.class), events);
		UUID userId = UUID.randomUUID();

		repository.saveUserDefaults(userId.toString(),
				List.of(new PreferenceSnapshot.PreferenceAnswer("LOCALITY", "0.75",
						PreferenceSnapshot.AnswerStatus.SELECTED)),
				Instant.parse("2026-09-23T01:00:00Z"));

		verify(events).publishEvent(new UserPreferenceDefaultsSaved(userId));
	}
}
