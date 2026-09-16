package com.gabolle.backend.collection.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.collection.domain.Collection;

public interface CollectionRepository extends JpaRepository<Collection, UUID> {

	/**
	 * 최근 손댄 순. {@code Pageable} 로 <b>상한을 받는다</b> (S15P21E201-1037) — 그전에는
	 * 전부 돌려줬고, 컬렉션은 사용자가 만드는 만큼 늘어난다.
	 */
	List<Collection> findByUserIdOrderByUpdatedAtDesc(UUID userId, Pageable pageable);

	Optional<Collection> findByIdAndUserId(UUID collectionId, UUID userId);
}
