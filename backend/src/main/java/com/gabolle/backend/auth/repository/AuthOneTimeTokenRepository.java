package com.gabolle.backend.auth.repository;

import com.gabolle.backend.auth.domain.AuthOneTimeToken;
import com.gabolle.backend.auth.domain.AuthTokenPurpose;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AuthOneTimeTokenRepository extends JpaRepository<AuthOneTimeToken, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<AuthOneTimeToken> findByTokenHashAndPurpose(String tokenHash, AuthTokenPurpose purpose);

	Optional<AuthOneTimeToken> findTopByLocalCredentialAndPurposeOrderByCreatedAtDesc(
			com.gabolle.backend.auth.domain.LocalCredential localCredential, AuthTokenPurpose purpose);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<AuthOneTimeToken> findAllByLocalCredentialAndPurposeAndConsumedAtIsNull(
			com.gabolle.backend.auth.domain.LocalCredential localCredential, AuthTokenPurpose purpose);
}
