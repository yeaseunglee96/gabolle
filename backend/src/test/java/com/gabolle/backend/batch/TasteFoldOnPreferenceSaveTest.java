package com.gabolle.backend.batch;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import com.gabolle.backend.batch.application.TasteFoldOnPreferenceSave;
import com.gabolle.backend.batch.application.TasteVectorFoldService;
import com.gabolle.backend.trip.domain.UserPreferenceDefaultsSaved;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 설문 저장 직후의 접기 — DB 없이 확인할 수 있는 약속 둘 (S15P21E201-1515).
 */
class TasteFoldOnPreferenceSaveTest {

	private static final Instant NOW = Instant.parse("2026-09-23T01:00:00Z");

	@Test
	@DisplayName("🔴 접기가 실패해도 예외가 저장 쪽으로 안 올라간다 — 설문은 이미 저장됐다")
	void foldFailureDoesNotReachTheSave() {
		TasteVectorFoldService fold = mock(TasteVectorFoldService.class);
		UUID userId = UUID.randomUUID();
		when(fold.fold(eq(userId), any())).thenThrow(new IllegalStateException("판 번호가 겹쳤다"));
		TasteFoldOnPreferenceSave listener = new TasteFoldOnPreferenceSave(fold,
				mock(PlatformTransactionManager.class), Clock.fixed(NOW, ZoneOffset.UTC));

		// 커밋 뒤 단계의 예외는 요청까지 올라간다. 삼키지 않으면 저장은 됐는데 앱은 실패를 받는다.
		assertThatCode(() -> listener.onSaved(new UserPreferenceDefaultsSaved(userId)))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("🔴 새 트랜잭션을 연다 — 커밋이 끝난 트랜잭션에 합류하면 쓴 것이 조용히 사라진다")
	void opensANewTransaction() {
		TasteVectorFoldService fold = mock(TasteVectorFoldService.class);
		PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
		UUID userId = UUID.randomUUID();
		TasteFoldOnPreferenceSave listener = new TasteFoldOnPreferenceSave(fold, transactions,
				Clock.fixed(NOW, ZoneOffset.UTC));

		listener.onSaved(new UserPreferenceDefaultsSaved(userId));

		verify(transactions).getTransaction(argThat((TransactionDefinition definition) ->
				definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
		verify(fold).fold(userId, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
	}
}
