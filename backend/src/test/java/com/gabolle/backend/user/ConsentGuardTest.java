package com.gabolle.backend.user;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.user.application.ConsentGuard;
import com.gabolle.backend.user.support.ConsentGuards;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.ConsentStatus;
import com.gabolle.backend.user.domain.ConsentType;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserConsent;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.UserConsentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 동의 판정 — S15P21E201-549.
 *
 * <p>🔴 <b>없는 것을 어떻게 다루는가</b>가 이 판정의 전부다. 동의를 한 번도 안 한 사람과
 * 철회한 사람은 둘 다 "쓰면 안 되는" 쪽이어야 하는데, 판정을 잘못 쓰면 전자가 조용히
 * 통과한다 — {@code Optional.isPresent()} 만 보고 상태를 안 보는 실수가 그것이다.
 *
 * <p>정렬(가장 최근 결정이 이긴다)은 DB 질의가 하므로 여기서는 재지 않는다.
 * 그건 {@code VisitVerificationIntegrationTest} 가 진짜 DB 로 잰다.
 *
 * <p>🔴 항목마다 따로 재는 이유 — 하나로 뭉쳐 재면 <b>한 항목의 동의로 다른 항목이
 * 열리는</b> 실수를 못 잡는다. 티켓의 완료 기준이 "동의 세 종류를 하나의 값으로 우회할 수
 * 없다" 인 것이 정확히 그 뜻이다.
 */
class ConsentGuardTest {

	private UserConsentRepository consents;

	private ConsentGuard guard;

	private final UUID userId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		this.consents = mock(UserConsentRepository.class);
		this.guard = new ConsentGuard(ConsentGuards.providerOf(this.consents));
		givenNoDecisionAtAll();
	}

	private void givenNoDecisionAtAll() {
		given(this.consents.findFirstByUserUserIdAndConsentTypeOrderByDecidedAtDesc(any(), any()))
				.willReturn(Optional.empty());
	}

	private void givenLatestDecision(ConsentType type, ConsentStatus status) {
		given(this.consents.findFirstByUserUserIdAndConsentTypeOrderByDecidedAtDesc(any(), eq(type)))
				.willReturn(Optional.of(decision(type, status)));
	}

	private static UserConsent decision(ConsentType type, ConsentStatus status) {
		AppUser user = AppUser.register("여행자", "KO", Instant.now(), "2026-01",
				PersonalizationMode.EXPLICIT_ONLY, UserStatus.ACTIVE);
		return UserConsent.decide(user, type, status, "2026-01");
	}

	// ── 정밀 위치 ────────────────────────────────────────────────────────────

	@Test
	@DisplayName("동의했으면 통과한다")
	void grantedPreciseLocationPasses() {
		givenLatestDecision(ConsentType.PRECISE_LOCATION, ConsentStatus.GRANTED);

		assertThatCode(() -> this.guard.requirePreciseLocation(this.userId)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("🔴 한 번도 정한 적이 없으면 막는다 — 행이 없다는 것은 동의가 아니다")
	void noDecisionAtAllIsRefused() {
		assertThat(this.guard.isGranted(this.userId, ConsentType.PRECISE_LOCATION)).isFalse();
	}

	@Test
	@DisplayName("🔴 철회했으면 막는다 — 행이 있다는 것만 보면 통과해 버린다")
	void revokedIsRefused() {
		givenLatestDecision(ConsentType.PRECISE_LOCATION, ConsentStatus.REVOKED);

		assertThat(this.guard.isGranted(this.userId, ConsentType.PRECISE_LOCATION)).isFalse();
	}

	@Test
	@DisplayName("사용자가 없으면 막는다")
	void nullUserIsRefused() {
		assertThat(this.guard.isGranted(null, ConsentType.PRECISE_LOCATION)).isFalse();
	}

	// ── 건강·식이 ────────────────────────────────────────────────────────────

	@Test
	@DisplayName("동의했으면 통과한다")
	void grantedHealthPasses() {
		givenLatestDecision(ConsentType.HEALTH_CONSTRAINTS, ConsentStatus.GRANTED);

		assertThatCode(() -> this.guard.requireHealthConstraints(this.userId)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("🔴 정밀 위치에 동의했다고 건강 정보가 열리지 않는다 — 하나로 셋을 우회할 수 없다")
	void oneConsentDoesNotUnlockAnother() {
		givenLatestDecision(ConsentType.PRECISE_LOCATION, ConsentStatus.GRANTED);

		assertThatCode(() -> this.guard.requirePreciseLocation(this.userId)).doesNotThrowAnyException();
		assertThatThrownBy(() -> this.guard.requireHealthConstraints(this.userId))
				.isInstanceOf(AuthException.class);
	}

	// ── 거절의 모양 ──────────────────────────────────────────────────────────

	/**
	 * 🔴 401 이 아니라 403 이다. 로그인은 돼 있고 <b>이 동작에 필요한 동의</b>가 없는
	 * 상태다. 401 로 내보내면 앱이 토큰을 갱신하러 갔다가 같은 자리에서 다시 막힌다.
	 *
	 * <p>코드가 항목마다 다른 이유는 앱이 응답만 보고 <b>어느 동의 화면</b>으로 보낼지
	 * 정해야 하기 때문이다.
	 */
	@Test
	@DisplayName("🔴 막을 때는 403 과 항목별 코드로 막는다 — 앱이 어느 동의 화면으로 보낼지 알아야 한다")
	void refusalIsForbiddenWithAnActionableCodePerType() {
		assertThatThrownBy(() -> this.guard.requirePreciseLocation(this.userId))
				.isInstanceOf(AuthException.class)
				.satisfies(thrown -> {
					AuthException exception = (AuthException) thrown;
					assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
					assertThat(exception.getCode()).isEqualTo("PRECISE_LOCATION_CONSENT_REQUIRED");
				});

		assertThatThrownBy(() -> this.guard.requireHealthConstraints(this.userId))
				.isInstanceOf(AuthException.class)
				.satisfies(thrown -> assertThat(((AuthException) thrown).getCode())
						.isEqualTo("HEALTH_CONSENT_REQUIRED"));
	}
}
