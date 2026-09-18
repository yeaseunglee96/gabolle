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

	/** 상한을 받는다 (S15P21E201-1037). 한 컬렉션에 담기는 개수에 끝이 없었다. */
	List<CollectionItem> findByCollectionIdOrderByPositionAscCreatedAtAsc(UUID collectionId, Pageable pageable);

	/**
	 * 목록 화면이 컬렉션마다 따로 묻지 않도록 한 번에 가져온다 (N+1 회피).
	 *
	 * <p>여기에는 컬렉션별 상한을 걸 수 없다 — 한 문장으로 가져오는 값이라 상한이
	 * 전체에 걸린다. 그래서 부르는 쪽이 <b>컬렉션 수를 먼저 상한으로 자르고</b> 그 만큼만
	 * 넘긴다. 전체 상한은 그 곱으로 자연히 묶인다.
	 */
	List<CollectionItem> findByCollectionIdInOrderByPositionAscCreatedAtAsc(List<UUID> collectionIds,
			Pageable pageable);

	Optional<CollectionItem> findByIdAndCollectionId(UUID itemId, UUID collectionId);

	/** 같은 장소가 이미 담겨 있는지. 업서트로 넣은 뒤 그 행을 도로 읽는 데 쓴다. */
	Optional<CollectionItem> findByCollectionIdAndPlaceId(UUID collectionId, UUID placeId);

	long countByCollectionId(UUID collectionId);

	/**
	 * 장소 항목을 없으면 넣고, 이미 있으면 아무것도 안 한다 — S15P21E201-1037.
	 *
	 * <p>그전에는 그 컬렉션의 항목을 전부 읽어 같은 장소가 있는지 본 뒤 넣었다. 같은 장소를
	 * 두 번 빠르게 담으면 두 요청이 둘 다 통과한 뒤 하나가 {@code uk_collection_item_place} 에
	 * 걸려 500 이 됐다. 판정을 DB 한 문장 안에서 끝내면 그 사이가 없어진다.
	 *
	 * <p>{@code "position"} 은 따옴표로 감싼다 — PostgreSQL 에서 함수 이름과 겹치는 낱말이라
	 * 문맥에 따라 다르게 읽힌다.
	 *
	 * <p>{@code CUSTOM} 항목에는 쓰지 않는다. 직접 적은 곳은 이름이 같아도 다른 곳일 수 있어
	 * 유일 제약 자체가 없다({@code place_id} 가 {@code NULL} 이면 걸리지 않는다).
	 *
	 * @return 실제로 넣었으면 1, 이미 있어서 아무것도 안 했으면 0
	 */
	//
	// @Transactional 을 여기 붙인다 (S15P21E201-1037). @Modifying 질의는 트랜잭션을
	//    요구하는데, Spring Data 는 기본 CRUD 에만 트랜잭션을 걸어 주고 직접 쓴 질의에는
	//    안 걸어 준다. 부르는 서비스가 전부 @Transactional 이라 운영에서는 안 드러나고,
	//    리포지토리를 곧장 부르는 시험에서 "flush 를 처리할 수 없다" 로 터졌다.
	//    운영에서는 부모 트랜잭션에 합류하므로(REQUIRED) 동작이 달라지지 않는다.
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
