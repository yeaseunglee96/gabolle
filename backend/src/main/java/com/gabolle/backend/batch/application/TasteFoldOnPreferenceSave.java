package com.gabolle.backend.batch.application;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.gabolle.backend.trip.domain.UserPreferenceDefaultsSaved;

/**
 * 설문이 저장되면 그 사람 하나를 그 자리에서 접는다 (S15P21E201-1515).
 *
 * <p>예전에는 판을 만드는 곳이 새벽 배치(Airflow {@code taste_vector_daily}) 하나뿐이었다. 판이
 * 없는 동안 추천은 취향 칸을 빈 목록으로 채점해 설문 답이 추천에 닿지 않았고, 카프카 소비자는
 * 이벤트를 받아도 그냥 넘겼다. 운영에서 설문 뒤 첫 판까지 7~19시간이 걸렸다 (2026-09-23 실측).
 *
 * <p>판을 만드는 규칙은 여기 다시 쓰지 않는다. {@link TasteVectorFoldService#fold} 를 그대로
 * 부른다 — 배치와 이것이 같은 문을 지나야 규칙이 두 벌이 안 된다.
 *
 * <h2>🔴 트랜잭션 두 가지</h2>
 * <ul>
 * <li><b>커밋 뒤에 돈다</b> ({@code AFTER_COMMIT}). 앞에서 돌면 롤백될 설문으로 판을 만든다.
 * 트랜잭션 밖에서 저장된 경우에도 돌게 {@code fallbackExecution} 을 켠다 — 안 켜면 그때 이벤트가
 * 조용히 버려진다</li>
 * <li><b>새 트랜잭션을 연다</b> ({@code REQUIRES_NEW}). 커밋 뒤 단계에서 그냥
 * {@code @Transactional} 을 부르면 이미 끝난 트랜잭션에 합류해서, 쓴 것이 <b>커밋되지 않고
 * 사라진다.</b> 오류도 안 난다</li>
 * </ul>
 *
 * <h2>실패는 삼킨다</h2>
 * 설문은 이미 저장됐다. 여기서 던지면 커밋 뒤 단계의 예외가 요청까지 올라가, 저장은 됐는데 앱은
 * 실패를 받는다. 접지 못한 사람은 새벽 배치가 줍는다 — {@code /stale} 이 판이 없거나 뒤처진
 * 사람을 고른다.
 */
@Component
@Profile({ "db", "dev" })
public class TasteFoldOnPreferenceSave {

	private static final Logger log = LoggerFactory.getLogger(TasteFoldOnPreferenceSave.class);

	private final TasteVectorFoldService foldService;

	private final TransactionTemplate newTransaction;

	private final Clock clock;

	public TasteFoldOnPreferenceSave(TasteVectorFoldService foldService,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.foldService = foldService;
		this.newTransaction = new TransactionTemplate(transactionManager);
		this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.clock = clock;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onSaved(UserPreferenceDefaultsSaved event) {
		try {
			// 지금을 여기서 한 번만 읽는다. fold 는 안에서 시각을 읽지 않는다 (그 클래스 머리말).
			OffsetDateTime asOf = OffsetDateTime.now(this.clock);
			TasteVectorFoldOutcome outcome = this.newTransaction
					.execute((status) -> this.foldService.fold(event.userId(), asOf));
			log.info("event=TASTE_FOLDED_ON_SAVE user={} action={}", event.userId(),
					(outcome == null) ? null : outcome.action());
		}
		catch (RuntimeException exception) {
			log.warn("event=TASTE_FOLD_ON_SAVE_FAILED user={} — 새벽 배치가 다시 줍는다", event.userId(), exception);
		}
	}
}
