package com.gabolle.backend.auth.service;

import com.gabolle.backend.auth.repository.AnonymousSessionRepository;
import com.gabolle.backend.trip.application.AnonymousTripClaimService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인한 사람에게 이 기기의 익명 세션을 넘긴다 — 그 세션으로 만든 여행을 계정으로 옮기고 세션은 없앤다.
 *
 * <p>가입·로그인·소셜 가입·소셜 연결마다 승계를 끼워 넣지 않고, 화면이 로그인을 마친 직후 한 번 부르는
 * 자리로 모았다. 로그인 방법이 늘어도 이 한 곳만 지나면 된다. 대신 로그인과 같은 트랜잭션이 아니어서
 * 승계가 실패해도 로그인은 남는다 — 화면이 다시 부르면 되고, 세션이 남아 있으니 여행도 남아 있다.
 *
 * <p>세션을 지우는 것이 중요하다. 남겨 두면 같은 기기에서 다음 사람이 비회원으로 쓰다 만든 여행이,
 * 앞사람이 다시 로그인할 때 앞사람 계정으로 딸려 간다. 화면은 이 호출 뒤에 출입증을 새로 받는다.
 */
@Service
@Profile({"db", "dev"})
public class AnonymousSessionHandoverService {

	private final AnonymousSessionRepository repository;
	private final SessionTokenGenerator tokenGenerator;
	private final AnonymousTripClaimService tripClaimService;
	private final Clock clock;

	@Autowired
	public AnonymousSessionHandoverService(AnonymousSessionRepository repository, SessionTokenGenerator tokenGenerator,
			AnonymousTripClaimService tripClaimService) {
		this(repository, tokenGenerator, tripClaimService, Clock.systemUTC());
	}

	AnonymousSessionHandoverService(AnonymousSessionRepository repository, SessionTokenGenerator tokenGenerator,
			AnonymousTripClaimService tripClaimService, Clock clock) {
		this.repository = repository;
		this.tokenGenerator = tokenGenerator;
		this.tripClaimService = tripClaimService;
		this.clock = clock;
	}

	/**
	 * @param rawToken 화면이 들고 있던 출입증 원본. 없거나 모르는 값이면 0 — 두 번 불러도 같은 결과다
	 * @return 옮긴 여행 수
	 */
	@Transactional
	public int handOver(String rawToken, UUID userId) {
		if (rawToken == null || rawToken.isBlank()) {
			return 0;
		}
		Instant now = clock.instant();
		return repository.findByTokenHash(tokenGenerator.hash(rawToken))
				.map(session -> {
					int claimed = tripClaimService.claimForNewUser(session.getSessionId().toString(),
							userId.toString(), now);
					repository.delete(session);
					return claimed;
				})
				.orElse(0);
	}
}
