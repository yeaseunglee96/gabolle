package com.gabolle.backend.place.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.domain.SavedPlace;

public interface SavedPlaceRepository extends JpaRepository<SavedPlace, UUID> {

	/**
	 * 내가 저장한 장소. 최근에 저장한 것이 먼저다. 저장 탭만이 아니라 홈 화면이 뜰 때마다 하트
	 * 표시를 맞추려고 부르므로 상한을 둔다.
	 *
	 * <p>부르는 쪽이 「상한+1」을 요청해서 더 있는지 판정하고, 그 사실을 응답의 {@code hasMore}
	 * 로 함께 보낸다.
	 */
	List<SavedPlace> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

	boolean existsByUserIdAndPlaceId(UUID userId, UUID placeId);

	void deleteByUserIdAndPlaceId(UUID userId, UUID placeId);

	/**
	 * 없으면 넣고, 이미 있으면 아무것도 안 한다. 「있는지 보고 없으면 넣는다」는 두 요청 사이가
	 * 벌어져서, 하트를 빠르게 두 번 누르면 {@code uk_saved_place} 위반으로 500 이 나간다.
	 * 자바에서 그 예외를 잡아도 트랜잭션이 이미 되돌리기로 표시돼 뒤따르는 조회가 실패하므로,
	 * 판정을 DB 한 문장 안에서 끝낸다.
	 *
	 * <p>{@code flushAutomatically} 로 앞선 변경을 먼저 내보내고 {@code clearAutomatically} 로
	 * 영속성 컨텍스트를 비운다 — 네이티브 문장은 그 컨텍스트를 거치지 않으므로, 비우지 않으면
	 * 같은 트랜잭션의 다음 조회가 낡은 객체를 돌려줄 수 있다.
	 *
	 * @return 실제로 넣었으면 1, 이미 있어서 아무것도 안 했으면 0
	 */
	// @Modifying 질의는 트랜잭션을 요구하는데 Spring Data 는 기본 CRUD 에만 걸어 준다.
	// 운영에서는 부모 트랜잭션에 합류하므로(REQUIRED) 동작이 달라지지 않는다.
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			INSERT INTO saved_place (saved_place_id, user_id, place_id, created_at)
			VALUES (:savedPlaceId, :userId, :placeId, :createdAt)
			ON CONFLICT (user_id, place_id) DO NOTHING
			""", nativeQuery = true)
	int insertIfAbsent(@Param("savedPlaceId") UUID savedPlaceId, @Param("userId") UUID userId,
			@Param("placeId") UUID placeId, @Param("createdAt") OffsetDateTime createdAt);
}
