package com.gabolle.backend.trip.infra;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface TripJpaRepository extends JpaRepository<TripJpaEntity, UUID> {

	/**
	 * S15P21E201-738 — 목록에 넣을 여행을 한 번에 읽는다.
	 *
	 * <p>🔴 정렬과 상한을 <b>질의에서</b> 건다. 전부 읽어 자바에서 자르면 한 사람이 여행
	 * 수백 개에 들어 있을 때 그만큼을 메모리로 가져온다. {@code deleted_at} 이 채워진 행은
	 * 여기서 걸러 낸다 — 부르는 쪽에서 거르면 상한이 지워진 여행에 먼저 먹힌다.
	 */
	List<TripJpaEntity> findByTripIdInAndDeletedAtIsNullOrderByUpdatedAtDesc(
			Collection<UUID> tripIds, Pageable pageable);

	/** S15P21E201-317 — 가입할 때 "이 익명 세션이 만든 여행" 을 찾는 자리. */
	List<TripJpaEntity> findByOwnerTypeAndOwnerUserId(String ownerType, UUID ownerUserId);
}
