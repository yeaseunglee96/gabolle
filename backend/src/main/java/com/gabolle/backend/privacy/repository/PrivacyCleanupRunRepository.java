package com.gabolle.backend.privacy.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.privacy.domain.PrivacyCleanupRun;

public interface PrivacyCleanupRunRepository extends JpaRepository<PrivacyCleanupRun, UUID> {
}
