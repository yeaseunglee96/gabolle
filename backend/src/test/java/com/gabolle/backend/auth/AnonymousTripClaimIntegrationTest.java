package com.gabolle.backend.auth;

import static com.gabolle.backend.trip.support.TripCommands.withLodging;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.gabolle.backend.auth.service.AnonymousSessionService;
import com.gabolle.backend.auth.service.AnonymousSessionService.IssuedAnonymousSession;
import com.gabolle.backend.auth.service.AuthCommands;
import com.gabolle.backend.auth.service.LocalAuthService;
import com.gabolle.backend.auth.support.AuthPostgresIntegrationTest;
import com.gabolle.backend.trip.application.TripCreationService;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 익명 세션이 만든 여행의 승계를 진짜 PostgreSQL 위에서 잰다.
 *
 * <p>승계는 {@code trip}·{@code trip_member} 두 표를 함께 옮긴다
 * ({@code JpaTripRepository.claimAnonymousTrips} 의 네이티브 SQL). 그 SQL 이 실제로 두 칸을
 * 바꾸는지, {@code updatable = false} 인 칸이 정말 바뀌는지는 실제 DB 에서만 알 수 있다.
 */
class AnonymousTripClaimIntegrationTest extends AuthPostgresIntegrationTest {

	@Autowired
	private AnonymousSessionService anonymousSessionService;

	@Autowired
	private LocalAuthService localAuthService;

	@Autowired
	private TripCreationService tripCreationService;

	@Autowired
	private TripQueryService tripQueryService;

	@Test
	@DisplayName("익명으로 여행 2개를 만든 뒤 가입하면 내 여행 목록에 그 2개가 보인다")
	void claimsAnonymousTripsIntoTheNewAccount() {
		IssuedAnonymousSession session = anonymousSessionService.issue();
		String sessionId = session.sessionId().toString();

		Trip firstTrip = createAnonymousTrip(sessionId).trip();
		Trip secondTrip = createAnonymousTrip(sessionId).trip();

		LocalAuthService.Registration registration = localAuthService.register(registerCommand(session.token()));

		List<TripRepository.MemberTrip> myTrips =
				tripQueryService.list(registration.userId().toString(), TripQueryService.MAX_LIST_SIZE);

		assertThat(myTrips.stream().map(m -> m.trip().tripId()))
				.contains(firstTrip.tripId(), secondTrip.tripId());
		assertThat(myTrips.stream()
				.filter(m -> m.trip().tripId().equals(firstTrip.tripId()))
				.findFirst().orElseThrow().trip().ownerType())
				.isEqualTo(Trip.OwnerType.USER);
	}

	@Test
	@DisplayName("익명 여행이 없는 상태로(발급받은 세션이지만 여행은 안 만들고) 가입해도 성공한다")
	void signupSucceedsWithAnEmptyAnonymousSession() {
		IssuedAnonymousSession session = anonymousSessionService.issue();

		LocalAuthService.Registration registration = localAuthService.register(registerCommand(session.token()));

		assertThat(registration.userId()).isNotNull();
		assertThat(tripQueryService.list(registration.userId().toString(), TripQueryService.MAX_LIST_SIZE)).isEmpty();
	}

	@Test
	@DisplayName("X-Session-Token 자체가 없는(익명 세션을 아예 안 거친) 가입도 성공한다")
	void signupSucceedsWithoutAnySessionTokenAtAll() {
		LocalAuthService.Registration registration = localAuthService.register(new AuthCommands.Register(
				"no-session-" + UUID.randomUUID() + "@example.com", "Route!2026", "여행자", "KO", true, "device-1",
				Map.of("TERMS_OF_SERVICE", true, "PRIVACY_POLICY", true), false));

		assertThat(registration.userId()).isNotNull();
	}

	private TripCreationService.Result createAnonymousTrip(String sessionId) {
		TripCreationService.Command command = withLodging(new TripCreationService.Command(
				sessionId,
				LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3),
				// 출발지 좌표는 필수다 — 없으면 여행 생성이 400 이라 승계 갈래에 닿지 못한다.
				35.1587, 129.1604, null, 1,
				null, "Asia/Seoul",
				List.<PreferenceSnapshot.PreferenceAnswer>of(),
				List.<TripCreationService.Command.ConstraintInput>of(),
				Trip.OwnerType.ANONYMOUS));
		return tripCreationService.create(command, null);
	}

	private AuthCommands.Register registerCommand(String sessionToken) {
		return new AuthCommands.Register(
				"claimer-" + UUID.randomUUID() + "@example.com", "Route!2026", "여행자", "KO",
				true, "device-1", Map.of("TERMS_OF_SERVICE", true, "PRIVACY_POLICY", true), false, sessionToken);
	}
}
