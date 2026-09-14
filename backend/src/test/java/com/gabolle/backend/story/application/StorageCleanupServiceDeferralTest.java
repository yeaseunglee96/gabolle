package com.gabolle.backend.story.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.gabolle.backend.story.domain.StorageCleanupEntry;
import com.gabolle.backend.story.repository.StorageCleanupRepository;
import com.gabolle.backend.story.storage.StoragePort;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * S15P21E201-945 — {@code deleteOrEnqueue} 가 진행 중인 트랜잭션이 있으면 저장소 호출을 커밋
 * 이후로 미루는지 순수 단위 테스트로 확인한다(DB 없이 {@link TransactionSynchronizationManager}
 * 만 직접 다룬다 — 실제 Postgres 를 띄우는 통합 테스트는 {@code StorageCleanupServiceTest} 참고).
 */
class StorageCleanupServiceDeferralTest {

	private final StoragePort storagePort = mock(StoragePort.class);
	private final StorageCleanupRepository storageCleanupRepository = mock(StorageCleanupRepository.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-09-14T00:00:00Z"), ZoneOffset.UTC);
	private final StorageCleanupService service = new StorageCleanupService(this.storagePort,
			this.storageCleanupRepository, this.clock);

	@AfterEach
	void clearSynchronization() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	@DisplayName("진행 중인 트랜잭션이 없으면 곧바로 지운다")
	void deletesImmediatelyWithoutATransaction() {
		this.service.deleteOrEnqueue("story/x.jpg", StorageCleanupEntry.REASON_STORY_DELETED);

		verify(this.storagePort).delete("story/x.jpg");
	}

	@Test
	@DisplayName("🔴 진행 중인 트랜잭션이 있으면 커밋 전에는 저장소를 부르지 않고, 커밋돼야 부른다")
	void deletesOnlyAfterCommitWhenATransactionIsActive() {
		TransactionSynchronizationManager.initSynchronization();
		try {
			this.service.deleteOrEnqueue("story/x.jpg", StorageCleanupEntry.REASON_STORY_DELETED);

			verify(this.storagePort, never()).delete(anyString());

			TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());

			verify(this.storagePort).delete("story/x.jpg");
		}
		finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}
}
