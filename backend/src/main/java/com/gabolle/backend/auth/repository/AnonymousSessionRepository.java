package com.gabolle.backend.auth.repository;

import com.gabolle.backend.auth.domain.AnonymousSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnonymousSessionRepository extends JpaRepository<AnonymousSession, UUID> {

	Optional<AnonymousSession> findByTokenHash(String tokenHash);
}
