package com.gabolle.backend.collection.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.collection.domain.Collection;

public interface CollectionRepository extends JpaRepository<Collection, UUID> {

	/** 내 컬렉션 전부. 최근에 손댄 것이 먼저다 — 화면이 그 순서로 보여 준다. */
	List<Collection> findByUserIdOrderByUpdatedAtDesc(UUID userId);

	/**
	 * 🔴 <b>언제나 주인과 함께 찾는다.</b> 번호만으로 찾은 뒤 «주인이 맞나» 를 따로 보면,
	 * 그 검사를 한 자리에서 빠뜨리는 날이 온다. 안 맞으면 빈 값이 오고 호출부는 그것을
	 * «없는 컬렉션» 과 같게 다룬다 — 남의 것이 있다는 사실조차 안 흘린다.
	 */
	Optional<Collection> findByIdAndUserId(UUID collectionId, UUID userId);
}
