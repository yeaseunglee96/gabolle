package com.gabolle.backend.collection.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.collection.domain.CollectionItem;

public interface CollectionItemRepository extends JpaRepository<CollectionItem, UUID> {

	/** 한 컬렉션에 담긴 것 전부. 사용자가 정한 차례, 같으면 담은 순서. */
	List<CollectionItem> findByCollectionIdOrderByPositionAscCreatedAtAsc(UUID collectionId);

	/** 여러 컬렉션의 항목을 한 번에 — 목록 화면이 컬렉션마다 따로 묻지 않게 한다. */
	List<CollectionItem> findByCollectionIdInOrderByPositionAscCreatedAtAsc(List<UUID> collectionIds);

	/**
	 * 🔴 컬렉션 번호와 함께 찾는다. 항목 번호만으로 찾으면 <b>남의 컬렉션 항목</b>을 고칠
	 * 길이 열린다 — 컬렉션의 주인 검사를 통과한 뒤에도 항목이 그 컬렉션 것인지는 따로 봐야 한다.
	 */
	Optional<CollectionItem> findByIdAndCollectionId(UUID itemId, UUID collectionId);

	long countByCollectionId(UUID collectionId);
}
