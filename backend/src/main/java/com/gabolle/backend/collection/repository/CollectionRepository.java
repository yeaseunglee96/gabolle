package com.gabolle.backend.collection.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.collection.domain.Collection;

public interface CollectionRepository extends JpaRepository<Collection, UUID> {

	/** 최근 손댄 순. 컬렉션은 사용자가 만드는 만큼 늘어나므로 {@code Pageable} 로 상한을 받는다. */
	List<Collection> findByUserIdOrderByUpdatedAtDesc(UUID userId, Pageable pageable);

	Optional<Collection> findByIdAndUserId(UUID collectionId, UUID userId);
}
