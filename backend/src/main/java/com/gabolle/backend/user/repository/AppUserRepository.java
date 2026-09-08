package com.gabolle.backend.user.repository;

import com.gabolle.backend.user.domain.AppUser;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
}
