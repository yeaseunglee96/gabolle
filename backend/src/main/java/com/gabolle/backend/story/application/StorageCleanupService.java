package com.gabolle.backend.story.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.gabolle.backend.story.domain.StorageCleanupEntry;
import com.gabolle.backend.story.repository.StorageCleanupRepository;
import com.gabolle.backend.story.storage.StoragePort;

/**
 * 저장소에서 못 지운 파일을 뒷정리한다.
 *
 * <p>{@link #deleteOrEnqueue} 는 절대 예외를 밖으로 던지지 않는다. 호출자(기록 삭제·계정 탈퇴)의
 * 트랜잭션 안에서 불리므로, 사진 하나를 못 지웠다고 예외가 나가면 호출자가 롤백되어 사용자는 삭제
 * 버튼을 몇 번 눌러도 똑같이 실패한다. 저장소 실패도 대기열 기록 실패도 이 메서드 안에서 잡는다.
 *
 * <p>저장소 호출은 호출자의 DB 트랜잭션이 커밋된 뒤에 한다. 트랜잭션 안에서 곧바로 네트워크 호출을
 * 하면 그동안 DB 커넥션을 붙든 채로 있는다. 트랜잭션이 없는 곳에서 부르면 그 자리에서 바로 실행한다.
 *
 * <p>{@code afterCommit} 안에서 새로 쓸 때는 반드시 {@code REQUIRES_NEW} 다. 그 시점엔 방금 커밋된
 * 트랜잭션의 동기화가 아직 안 지워진 채라, 평범한 {@code @Transactional}(REQUIRED)로 쓰면 죽은
 * 동기화에 올라타 예외 없이 조용히 아무것도 안 쓰인다. {@link TransactionTemplate} 을
 * {@code PROPAGATION_REQUIRES_NEW} 로 쓰면 진짜 새 트랜잭션을 연다.
 */
@Service
@Profile({ "db", "dev" })
public class StorageCleanupService {

	private static final Logger log = LoggerFactory.getLogger(StorageCleanupService.class);

	private final StoragePort storagePort;

	private final StorageCleanupRepository storageCleanupRepository;

	private final Clock clock;

	private final TransactionTemplate requiresNewTransaction;

	public StorageCleanupService(StoragePort storagePort, StorageCleanupRepository storageCleanupRepository,
			Clock clock, PlatformTransactionManager transactionManager) {
		this.storagePort = storagePort;
		this.storageCleanupRepository = storageCleanupRepository;
		this.clock = clock;
		this.requiresNewTransaction = new TransactionTemplate(transactionManager);
		this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/**
	 * 지운다. 실패하면 예외를 밖으로 내지 않고 대기열에 남긴다(있으면 attempts+1).
	 *
	 * <p>진행 중인 트랜잭션이 있으면 그 트랜잭션이 커밋된 뒤에 실제 저장소 호출을 한다 —
	 * 클래스 javadoc 참고.
	 */
	public void deleteOrEnqueue(String storageKey, String reason) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			deleteNow(storageKey, reason);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				deleteNow(storageKey, reason);
			}
		});
	}

	private void deleteNow(String storageKey, String reason) {
		try {
			this.storagePort.delete(storageKey);
		}
		catch (RuntimeException e) {
			log.warn("사진 파일을 지우지 못해 뒷정리 대기열에 남긴다: storageKey={}, reason={}", storageKey, reason, e);
			enqueue(storageKey, reason, e);
		}
	}

	private void enqueue(String storageKey, String reason, Exception cause) {
		try {
			this.requiresNewTransaction.executeWithoutResult(status -> {
				Instant now = this.clock.instant();
				String message = cause.getMessage();
				this.storageCleanupRepository.findById(storageKey).ifPresentOrElse(
						entry -> {
							entry.recordFailure(now, message);
							this.storageCleanupRepository.save(entry);
						},
						() -> this.storageCleanupRepository.save(
								new StorageCleanupEntry(storageKey, reason, now, message)));
			});
		}
		catch (RuntimeException persistFailure) {
			// 대기열 기록 자체가 실패해도 호출자의 트랜잭션을 깨서는 안 된다 — 클래스 설명의 계약.
			log.warn("뒷정리 대기열 기록에도 실패했다: storageKey={}", storageKey, persistFailure);
		}
	}

	/**
	 * 대기 중인 것들을 다시 지운다. 성공한 것은 목록에서 빼고 실패한 것은 attempts 를 올린다.
	 *
	 * <p>이 메서드 자체에는 {@code @Transactional} 을 안 둔다. 최대 100건을 한 트랜잭션으로 묶으면
	 * 그 안의 네트워크 호출이 끝날 때까지 DB 커넥션 하나를 계속 붙잡는다. 저장소 메서드가 이미 건별로
	 * 자기 트랜잭션을 열므로 묶지 않아도 각 행의 갱신은 원자적이다.
	 */
	public int retryPending() {
		List<StorageCleanupEntry> pending = this.storageCleanupRepository.findTop100ByOrderByFirstFailedAtAsc();
		int deletedCount = 0;
		Instant now = this.clock.instant();
		for (StorageCleanupEntry entry : pending) {
			try {
				this.storagePort.delete(entry.getStorageKey());
				this.storageCleanupRepository.delete(entry);
				deletedCount++;
			}
			catch (RuntimeException e) {
				entry.recordFailure(now, e.getMessage());
				this.storageCleanupRepository.save(entry);
			}
		}
		return deletedCount;
	}
}
