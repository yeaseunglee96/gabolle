package com.gabolle.backend.auth.repository;

import com.gabolle.backend.auth.domain.AuthProvider;
import com.gabolle.backend.auth.domain.OAuthChallenge;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface OAuthChallengeRepository extends JpaRepository<OAuthChallenge, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<OAuthChallenge> findByProviderAndStateHash(AuthProvider provider, String stateHash);
}
