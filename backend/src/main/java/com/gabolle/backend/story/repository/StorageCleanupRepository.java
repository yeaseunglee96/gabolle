package com.gabolle.backend.story.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.story.domain.StorageCleanupEntry;

public interface StorageCleanupRepository extends JpaRepository<StorageCleanupEntry, String> {

	List<StorageCleanupEntry> findTop100ByOrderByFirstFailedAtAsc();
}
