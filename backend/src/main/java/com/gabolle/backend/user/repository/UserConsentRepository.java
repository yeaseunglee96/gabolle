package com.gabolle.backend.user.repository;

import com.gabolle.backend.user.domain.UserConsent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {

	List<UserConsent> findAllByUserUserId(UUID userId);
}
