package com.gabolle.backend.auth.service;

import com.gabolle.backend.auth.domain.AnonymousSession;
import com.gabolle.backend.auth.repository.AnonymousSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 익명 출입증 발급·대조. 원본 문자열은 응답으로만 나가고 서버에는
 * {@link SessionTokenGenerator#hash(String)} 이 만든 값만 남는다 — 이 저장소의 다른 토큰들과
 * 같은 방식이다.
 */
@Service
@Profile({"db", "dev"})
public class AnonymousSessionService {

	private final AnonymousSessionRepository repository;
	private final SessionTokenGenerator tokenGenerator;
	private final Clock clock;

	@Autowired
	public AnonymousSessionService(AnonymousSessionRepository repository, SessionTokenGenerator tokenGenerator) {
		this(repository, tokenGenerator, Clock.systemUTC());
	}

	AnonymousSessionService(AnonymousSessionRepository repository, SessionTokenGenerator tokenGenerator, Clock clock) {
		this.repository = repository;
		this.tokenGenerator = tokenGenerator;
		this.clock = clock;
	}

	/** 새 출입증을 발급한다. 부를 때마다 무작위값이 새로 나오므로 두 번 불러도 같은 값이 나올 수 없다. */
	@Transactional
	public IssuedAnonymousSession issue() {
		Instant now = clock.instant();
		String rawToken = tokenGenerator.issue();
		AnonymousSession session = repository.save(AnonymousSession.issue(tokenGenerator.hash(rawToken), now));
		return new IssuedAnonymousSession(session.getSessionId(), rawToken, now);
	}

	/**
	 * 요청 헤더로 들어온 출입증을 대조한다.
	 *
	 * <p>일치하는 세션이 있으면 접속 시각을 갱신하고 그 세션을 돌려준다. 존재하지 않거나 비어
	 * 있는 출입증은 어떤 세션과도 연결되지 않는다 — {@code Optional.empty()}.
	 */
	@Transactional
	public Optional<AnonymousSession> resolve(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return Optional.empty();
		}
		return repository.findByTokenHash(tokenGenerator.hash(rawToken))
				.map(session -> {
					session.touch(clock.instant());
					return session;
				});
	}

	public record IssuedAnonymousSession(UUID sessionId, String token, Instant issuedAt) {
	}
}
