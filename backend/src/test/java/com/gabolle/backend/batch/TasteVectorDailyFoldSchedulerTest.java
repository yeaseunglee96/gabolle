package com.gabolle.backend.batch;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.batch.application.TasteVectorBatchReport;
import com.gabolle.backend.batch.application.TasteVectorBatchService;
import com.gabolle.backend.batch.application.TasteVectorDailyFoldProperties;
import com.gabolle.backend.batch.application.TasteVectorDailyFoldScheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 새벽 접기 스케줄러 — DB 없이 확인할 수 있는 약속들 (S15P21E201-1516).
 */
class TasteVectorDailyFoldSchedulerTest {

	private static final Instant NOW = Instant.parse("2026-09-23T19:00:00Z");

	private static final OffsetDateTime AS_OF = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

	private final TasteVectorBatchService batch = mock(TasteVectorBatchService.class);

	private final TasteVectorDailyFoldProperties properties = new TasteVectorDailyFoldProperties();

	private final TasteVectorDailyFoldScheduler scheduler = new TasteVectorDailyFoldScheduler(this.batch,
			this.properties, Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	@DisplayName("🔴 기본값은 꺼짐이고, 꺼져 있으면 아무것도 안 한다 — Airflow DAG 와 두 번 접지 않게")
	void disabledByDefaultAndDoesNothing() {
		this.scheduler.runScheduled();

		verifyNoInteractions(this.batch);
	}

	@Test
	@DisplayName("찾을 때와 접을 때 같은 asOf 를 넘긴다")
	void passesTheSameAsOfToBothSteps() {
		this.properties.setEnabled(true);
		List<UUID> stale = List.of(UUID.randomUUID(), UUID.randomUUID());
		when(this.batch.staleUsers(AS_OF, 500)).thenReturn(new TasteVectorBatchService.StalePage(stale, false, 500));
		when(this.batch.rebuild(stale, AS_OF))
			.thenReturn(new TasteVectorBatchReport(AS_OF, Map.of(), List.of(), List.of()));

		this.scheduler.runScheduled();

		verify(this.batch).staleUsers(AS_OF, 500);
		verify(this.batch).rebuild(stale, AS_OF);
	}

	@Test
	@DisplayName("뒤처진 사람이 없으면 접기를 부르지 않는다 — 빈 목록은 「전부」가 아니다")
	void noStaleUsersSkipsRebuild() {
		this.properties.setEnabled(true);
		when(this.batch.staleUsers(any(), anyInt()))
			.thenReturn(new TasteVectorBatchService.StalePage(List.of(), false, 500));

		this.scheduler.runScheduled();

		verify(this.batch, never()).rebuild(any(), any());
	}

	@Test
	@DisplayName("🔴 실패해도 스케줄러 밖으로 예외가 안 나간다")
	void failureIsSwallowed() {
		this.properties.setEnabled(true);
		when(this.batch.staleUsers(any(), anyInt())).thenThrow(new IllegalStateException("DB 가 잠깐 끊겼다"));

		assertThatCode(this.scheduler::runScheduled).doesNotThrowAnyException();
	}
}
