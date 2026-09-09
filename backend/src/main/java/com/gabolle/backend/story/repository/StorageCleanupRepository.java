package com.gabolle.backend.story.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.story.domain.StorageCleanupEntry;

public interface StorageCleanupRepository extends JpaRepository<StorageCleanupEntry, String> {

	/** 뒷정리 작업이 한 번에 처리할 묶음. 오래 실패한 것부터. */
	List<StorageCleanupEntry> findTop100ByOrderByFirstFailedAtAsc();
}
