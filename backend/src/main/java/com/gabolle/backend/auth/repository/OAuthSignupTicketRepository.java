package com.gabolle.backend.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.gabolle.backend.auth.domain.OAuthSignupTicket;

import jakarta.persistence.LockModeType;

public interface OAuthSignupTicketRepository extends JpaRepository<OAuthSignupTicket, UUID> {

	/**
	 * 티켓을 쓰는 쪽이 부른다. 행 잠금이 걸려 있어, 같은 티켓으로 동시에 두 번 가입을 누르면
	 * 한쪽이 먼저 잠그고 소비하고 다른 쪽은 잠금이 풀린 뒤 이미 쓴 티켓을 본다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<OAuthSignupTicket> findByTicketHashAndKind(String ticketHash, OAuthSignupTicket.Kind kind);
}
