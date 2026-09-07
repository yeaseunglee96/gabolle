package com.gabolle.backend.trip.infra;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PreferenceSnapshotJpaRepository extends JpaRepository<PreferenceSnapshotJpaEntity, UUID> {

	Optional<PreferenceSnapshotJpaEntity> findByTripIdAndVersion(UUID tripId, int version);

	Optional<PreferenceSnapshotJpaEntity> findTopByTripIdOrderByVersionDesc(UUID tripId);

	/**
	 * 계정 기본값의 최신 판 (S15P21E201-547).
	 *
	 * <p>🔴 {@code TripIdIsNull} 조건이 필수다. 빼면 그 사용자가 만든 <b>여행 스냅샷</b>도
	 * 함께 걸려서, 여행에서 답한 값이 계정 기본값으로 읽힌다 — 이 티켓이 막으려는 것이
	 * 정확히 그것이다. 부분 유니크 색인({@code uq_preference_snapshot_user})도 같은
	 * 조건을 쓴다.
	 */
	Optional<PreferenceSnapshotJpaEntity> findTopByUserIdAndTripIdIsNullOrderByVersionDesc(UUID userId);
}
