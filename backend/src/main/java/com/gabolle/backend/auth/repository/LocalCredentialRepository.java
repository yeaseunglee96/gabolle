package com.gabolle.backend.auth.repository;

import com.gabolle.backend.auth.domain.LocalCredential;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocalCredentialRepository extends JpaRepository<LocalCredential, UUID> {

	Optional<LocalCredential> findByEmail(String email);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select credential from LocalCredential credential where credential.email = :email")
	Optional<LocalCredential> findByEmailForUpdate(@Param("email") String email);

	Optional<LocalCredential> findByUserUserId(UUID userId);
}
