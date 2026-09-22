package com.gabolle.backend.auth.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.auth.domain.AuthProvider;
import com.gabolle.backend.auth.domain.OAuthSignupTicket;
import com.gabolle.backend.auth.repository.OAuthSignupTicketRepository;
import com.gabolle.backend.user.domain.AppUser;

/**
 * 소셜 가입·연결 티켓의 발급과 소비.
 *
 * <p>원문 티켓은 한 번 만들어 호출자에게 돌려주고 끝이다. 표에는 해시만 남는다. 소비는 행 잠금
 * 아래에서 "안 썼고 안 만료됐는가" 를 보고 그 자리에서 {@code consumed_at} 을 찍는다.
 */
@Service
@Profile({ "db", "dev" })
public class OAuthSignupTicketService {

	private final OAuthSignupTicketRepository repository;
	private final SessionTokenGenerator tokenGenerator;
	private final AuthProperties properties;
	private final Clock clock;

	public OAuthSignupTicketService(OAuthSignupTicketRepository repository, SessionTokenGenerator tokenGenerator,
			AuthProperties properties, Clock clock) {
		this.repository = repository;
		this.tokenGenerator = tokenGenerator;
		this.properties = properties;
		this.clock = clock;
	}

	/** 클라이언트에 나가는 원문 티켓과 만료 시각. 원문은 이 값 밖 어디에도 남지 않는다. */
	public record IssuedTicket(String rawTicket, Instant expiresAt) {
	}

	@Transactional
	public IssuedTicket issueSignup(AuthProvider provider, String providerSubject, String providerEmail,
			String displayName, String language, String deviceId) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(properties.getOauthSignupTicketTtl());
		String raw = tokenGenerator.issue();
		repository.save(OAuthSignupTicket.forSignup(tokenGenerator.hash(raw), provider, providerSubject, providerEmail,
				displayName, language, deviceId, now, expiresAt));
		return new IssuedTicket(raw, expiresAt);
	}

	@Transactional
	public IssuedTicket issueLink(AuthProvider provider, String providerSubject, String providerEmail,
			AppUser existingUser, String deviceId) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(properties.getOauthSignupTicketTtl());
		String raw = tokenGenerator.issue();
		repository.save(OAuthSignupTicket.forLink(tokenGenerator.hash(raw), provider, providerSubject, providerEmail,
				existingUser, deviceId, now, expiresAt));
		return new IssuedTicket(raw, expiresAt);
	}

	/**
	 * 티켓을 찾아 잠그고, 쓸 수 있으면 소비 표시를 찍어 돌려준다.
	 *
	 * <p>없는 티켓·만료·이미 씀·종류 불일치는 모두 같은 응답이어야 한다. 티켓은 비밀값이라
	 * "있는데 만료됐다" 를 구분해 알려 주면 추측 시도에 정보를 준다.
	 *
	 * @throws AuthException {@code OAUTH_TICKET_INVALID}(400)
	 */
	@Transactional
	public OAuthSignupTicket consume(String rawTicket, OAuthSignupTicket.Kind kind) {
		if (rawTicket == null || rawTicket.isBlank()) {
			throw invalid();
		}
		Instant now = clock.instant();
		OAuthSignupTicket ticket = repository.findByTicketHashAndKind(tokenGenerator.hash(rawTicket), kind)
				.orElseThrow(this::invalid);
		if (!ticket.isUsableAt(now)) {
			throw invalid();
		}
		ticket.consume(now);
		return ticket;
	}

	private AuthException invalid() {
		return new AuthException("OAUTH_TICKET_INVALID", "소셜 로그인 절차가 만료됐어요. 소셜 로그인을 다시 시작해 주세요.",
				HttpStatus.BAD_REQUEST);
	}
}
