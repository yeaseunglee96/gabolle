package com.gabolle.backend.auth.config;

import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 익명 세션이 비용이 드는 것을 만드는 횟수를 막는다 — 여행 생성·공유 복제·추천 작업.
 *
 * <p>회원에게는 아무것도 하지 않는다. 막는 단위는 세션이라, 출입증을 새로 받으면 다시 처음부터다
 * ({@code POST /api/v1/auth/anonymous} 는 발급 제한이 없다). 그래도 한 기기에서 버튼을 반복해 누르는
 * 것과 스크립트 한 줄로 일정 생성을 무한히 돌리는 것의 비용을 갈라 놓는다. IP 로 막지 않는 이유는
 * 교육장처럼 출구 IP 를 함께 쓰는 곳에서 두 번째 사람부터 막히기 때문이다.
 *
 * <p>컨트롤러 안이 아니라 여기에 둔 것은 세 경로의 규칙을 한 곳에서 읽게 하려는 것이다. 숫자를 바꿀
 * 때 세 파일을 돌지 않는다.
 *
 * <p>429 로 답한다. 화면의 요청기는 503 만 자동으로 다시 보내므로 이 응답이 되풀이되지 않고,
 * 보안 사건 기록(401·403)에도 섞이지 않는다.
 */
@Component
@Profile({"db", "dev"})
public class AnonymousQuotaInterceptor implements HandlerInterceptor {

	static final String LIMIT_CODE = "ANONYMOUS_LIMIT_REACHED";

	@PersistenceContext
	private EntityManager entityManager;

	private final int maxActiveTrips;
	private final int maxRecommendationJobsPerDay;
	private final Clock clock;

	public AnonymousQuotaInterceptor(
			@Value("${gabolle.anonymous.max-active-trips:5}") int maxActiveTrips,
			@Value("${gabolle.anonymous.max-recommendation-jobs-per-day:20}") int maxRecommendationJobsPerDay,
			Clock clock) {
		this.maxActiveTrips = maxActiveTrips;
		this.maxRecommendationJobsPerDay = maxRecommendationJobsPerDay;
		this.clock = clock;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!"POST".equals(request.getMethod())) {
			return true;
		}
		Optional<UUID> session = AuthenticatedUsers
				.optionalAnonymousSessionId(SecurityContextHolder.getContext().getAuthentication());
		if (session.isEmpty()) {
			return true;
		}
		String path = request.getRequestURI().substring(request.getContextPath().length());
		if (path.endsWith("/recommendation-jobs")) {
			requireUnder(countRecentJobs(session.get()), maxRecommendationJobsPerDay,
					"비회원은 하루에 일정을 " + maxRecommendationJobsPerDay + "번까지 만들 수 있어요. 로그인하면 계속 만들 수 있어요.");
		}
		else {
			requireUnder(countActiveTrips(session.get()), maxActiveTrips,
					"비회원은 여행을 " + maxActiveTrips + "개까지 만들 수 있어요. 로그인하거나 지난 여행을 지워 주세요.");
		}
		return true;
	}

	private static void requireUnder(long count, int max, String message) {
		if (count >= max) {
			throw new AuthException(LIMIT_CODE, message, HttpStatus.TOO_MANY_REQUESTS);
		}
	}

	private long countActiveTrips(UUID sessionId) {
		return entityManager.createQuery("""
				SELECT COUNT(t) FROM TripJpaEntity t
				WHERE t.ownerType = 'ANONYMOUS' AND t.ownerUserId = :sessionId AND t.deletedAt IS NULL
				""", Long.class)
				.setParameter("sessionId", sessionId)
				.getSingleResult();
	}

	private long countRecentJobs(UUID sessionId) {
		OffsetDateTime since = OffsetDateTime.ofInstant(clock.instant().minus(Duration.ofDays(1)), ZoneOffset.UTC);
		return entityManager.createQuery(
				"SELECT COUNT(j) FROM RecommendationJob j WHERE j.userId = :sessionId AND j.createdAt >= :since",
				Long.class)
				.setParameter("sessionId", sessionId)
				.setParameter("since", since)
				.getSingleResult();
	}
}
