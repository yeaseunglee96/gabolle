package com.gabolle.backend.collection.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.collection.domain.CollectionItem;

public interface CollectionItemRepository extends JpaRepository<CollectionItem, UUID> {

	/** 상한을 받는다 — 한 컬렉션에 담기는 개수에는 끝이 없다. */
	List<CollectionItem> findByCollectionIdOrderByPositionAscCreatedAtAsc(UUID collectionId, Pageable pageable);

	/**
	 * 목록 화면이 컬렉션마다 따로 묻지 않도록 한 번에 가져온다 (N+1 회피).
	 *
	 * <p>컬렉션별 상한은 걸 수 없다 — 한 문장이라 상한이 전체에 걸린다. 부르는 쪽이 컬렉션
	 * 수를 먼저 상한으로 자르고 그만큼만 넘긴다.
	 */
	List<CollectionItem> findByCollectionIdInOrderByPositionAscCreatedAtAsc(List<UUID> collectionIds,
			Pageable pageable);

	Optional<CollectionItem> findByIdAndCollectionId(UUID itemId, UUID collectionId);

	/** 같은 장소가 이미 담겨 있는지. 업서트로 넣은 뒤 그 행을 도로 읽는 데 쓴다. */
	Optional<CollectionItem> findByCollectionIdAndPlaceId(UUID collectionId, UUID placeId);

	long countByCollectionId(UUID collectionId);

	/**
	 * 장소 항목을 없으면 넣고, 이미 있으면 아무것도 안 한다. 읽고 나서 넣으면 같은 장소를 두
	 * 번 빠르게 담을 때 둘 다 통과한 뒤 {@code uk_collection_item_place} 에 걸려 500 이 된다.
	 * 판정을 DB 한 문장 안에서 끝내 그 사이를 없앤다.
	 *
	 * <p>{@code "position"} 은 따옴표로 감싼다 — PostgreSQL 에서 함수 이름과 겹치는 낱말이라
	 * 문맥에 따라 다르게 읽힌다.
	 *
	 * <p>{@code CUSTOM} 항목에는 쓰지 않는다. 직접 적은 곳은 이름이 같아도 다른 곳일 수 있어
	 * 유일 제약 자체가 없다({@code place_id} 가 {@code NULL} 이면 걸리지 않는다).
	 *
	 * @return 실제로 넣었으면 1, 이미 있어서 아무것도 안 했으면 0
	 */
	// @Modifying 질의는 트랜잭션을 요구하지만 Spring Data 는 직접 쓴 질의에 걸어 주지 않는다.
	// 리포지토리를 곧장 부르는 시험을 위해 여기 붙인다 — 부모 트랜잭션에는 합류한다(REQUIRED).
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			INSERT INTO collection_item (
				collection_item_id, collection_id, kind, place_id, note, "position", created_at, updated_at)
			VALUES (:itemId, :collectionId, 'PLACE', :placeId, :note, :position, :now, :now)
			ON CONFLICT (collection_id, place_id) DO NOTHING
			""", nativeQuery = true)
	int insertPlaceItemIfAbsent(@Param("itemId") UUID itemId, @Param("collectionId") UUID collectionId,
			@Param("placeId") UUID placeId, @Param("note") String note, @Param("position") int position,
			@Param("now") OffsetDateTime now);
}
