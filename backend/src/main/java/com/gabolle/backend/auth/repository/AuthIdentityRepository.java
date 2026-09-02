package com.gabolle.backend.auth.repository;

import com.gabolle.backend.auth.domain.AuthIdentity;
import com.gabolle.backend.auth.domain.AuthProvider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, UUID> {

	Optional<AuthIdentity> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);

	List<AuthIdentity> findAllByUserUserId(UUID userId);
}
