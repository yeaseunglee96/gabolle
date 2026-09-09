package com.gabolle.backend.story.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.story.domain.StorageCleanupEntry;
import com.gabolle.backend.story.repository.StorageCleanupRepository;
import com.gabolle.backend.story.storage.StoragePort;

/**
 * 저장소에서 못 지운 파일을 뒷정리한다 — S15P21E201-226·-426.
 *
 * <h2>🔴 {@link #deleteOrEnqueue} 는 절대 예외를 밖으로 던지지 않는다</h2>
 * 이 메서드는 기록 삭제·계정 탈퇴 같은 호출자의 트랜잭션 <b>안에서</b> 불릴 수 있다. 사진 파일
 * 하나를 못 지웠다고 그 예외가 밖으로 나가면 호출자의 트랜잭션이 롤백되어 "기록은 남아 있는데
 * 사진도 안 지워진" 상태가 된다 — 사용자는 삭제 버튼을 몇 번을 눌러도 똑같이 실패한다. 티켓
 * -226 이 요구하는 것은 그 반대다: 파일 삭제가 실패해도 기록 삭제는 끝내고, 실패한 파일만
 * {@code storage_cleanup_queue} 에 남겨 나중에 {@link #retryPending} 이 다시 지운다. 그래서
 * 저장소 실패는 물론 대기열 기록 자체의 실패까지 이 메서드 안에서 잡는다.
 */
@Service
@Profile({ "db", "dev" })
public class StorageCleanupService {

	private static final Logger log = LoggerFactory.getLogger(StorageCleanupService.class);

	private final StoragePort storagePort;

	private final StorageCleanupRepository storageCleanupRepository;

	private final Clock clock;

	public StorageCleanupService(StoragePort storagePort, StorageCleanupRepository storageCleanupRepository,
			Clock clock) {
		this.storagePort = storagePort;
		this.storageCleanupRepository = storageCleanupRepository;
		this.clock = clock;
	}

	/** 지운다. 실패하면 예외를 밖으로 내지 않고 대기열에 남긴다(있으면 attempts+1). */
	public void deleteOrEnqueue(String storageKey, String reason) {
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
			Instant now = this.clock.instant();
			String message = cause.getMessage();
			this.storageCleanupRepository.findById(storageKey).ifPresentOrElse(
					entry -> {
						entry.recordFailure(now, message);
						this.storageCleanupRepository.save(entry);
					},
					() -> this.storageCleanupRepository.save(
							new StorageCleanupEntry(storageKey, reason, now, message)));
		}
		catch (RuntimeException persistFailure) {
			// 대기열 기록 자체가 실패해도 호출자의 트랜잭션을 깨서는 안 된다 — 클래스 설명의 계약.
			log.warn("뒷정리 대기열 기록에도 실패했다: storageKey={}", storageKey, persistFailure);
		}
	}

	/** 대기 중인 것들을 다시 지운다. 성공한 것은 목록에서 빼고 실패한 것은 attempts 를 올린다. */
	@Transactional
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
