package com.gabolle.backend.user;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.user.application.PreciseLocationConsent;
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
 * 정밀 위치 동의 판정 — S15P21E201-549 후속.
 *
 * <p>🔴 <b>없는 것을 어떻게 다루는가</b>가 이 판정의 전부다. 동의를 한 번도 안 한 사람과
 * 철회한 사람은 둘 다 "쓰면 안 되는" 쪽이어야 하는데, 판정을 잘못 쓰면 전자가 조용히
 * 통과한다 — {@code Optional.isPresent()} 만 보고 상태를 안 보는 실수가 그것이다.
 *
 * <p>정렬(가장 최근 결정이 이긴다)은 DB 질의가 하므로 여기서는 재지 않는다.
 * 그건 {@code VisitVerificationIntegrationTest} 가 진짜 DB 로 잰다.
 */
class PreciseLocationConsentTest {

	private UserConsentRepository consents;

	private PreciseLocationConsent guard;

	private final UUID userId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		this.consents = mock(UserConsentRepository.class);
		this.guard = new PreciseLocationConsent(this.consents);
	}

	private void givenLatestDecision(UserConsent consent) {
		given(this.consents.findFirstByUserUserIdAndConsentTypeOrderByDecidedAtDesc(any(),
				eq(ConsentType.PRECISE_LOCATION))).willReturn(Optional.ofNullable(consent));
	}

	private static UserConsent decision(ConsentStatus status) {
		AppUser user = AppUser.register("여행자", "KO", Instant.now(), "2026-01",
				PersonalizationMode.EXPLICIT_ONLY, UserStatus.ACTIVE);
		return UserConsent.decide(user, ConsentType.PRECISE_LOCATION, status, "2026-01");
	}

	@Test
	@DisplayName("동의했으면 통과한다")
	void grantedPasses() {
		givenLatestDecision(decision(ConsentStatus.GRANTED));

		assertThat(this.guard.isGranted(this.userId)).isTrue();
		assertThatCode(() -> this.guard.requireGranted(this.userId)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("🔴 한 번도 정한 적이 없으면 막는다 — 행이 없다는 것은 동의가 아니다")
	void noDecisionAtAllIsRefused() {
		givenLatestDecision(null);

		assertThat(this.guard.isGranted(this.userId)).isFalse();
	}

	@Test
	@DisplayName("🔴 철회했으면 막는다 — 행이 있다는 것만 보면 통과해 버린다")
	void revokedIsRefused() {
		givenLatestDecision(decision(ConsentStatus.REVOKED));

		assertThat(this.guard.isGranted(this.userId)).isFalse();
	}

	@Test
	@DisplayName("사용자가 없으면 막는다")
	void nullUserIsRefused() {
		assertThat(this.guard.isGranted(null)).isFalse();
	}

	/**
	 * 🔴 401 이 아니라 403 이다. 로그인은 돼 있고 <b>이 동작에 필요한 동의</b>가 없는
	 * 상태다. 401 로 내보내면 앱이 토큰을 갱신하러 갔다가 같은 자리에서 다시 막힌다.
	 */
	@Test
	@DisplayName("🔴 막을 때는 403 과 앱이 알아볼 수 있는 코드로 막는다 — 동의 화면으로 보내야 한다")
	void refusalIsForbiddenWithAnActionableCode() {
		givenLatestDecision(null);

		assertThatThrownBy(() -> this.guard.requireGranted(this.userId))
				.isInstanceOf(AuthException.class)
				.satisfies(thrown -> {
					AuthException exception = (AuthException) thrown;
					assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
					assertThat(exception.getCode()).isEqualTo("PRECISE_LOCATION_CONSENT_REQUIRED");
				});
	}
}
