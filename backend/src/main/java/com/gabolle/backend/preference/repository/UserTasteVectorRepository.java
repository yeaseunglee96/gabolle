package com.gabolle.backend.preference.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.preference.domain.UserTasteVector;

/**
 * 취향 벡터 판 조회.
 *
 * <p>🔴 <b>피드를 읽는 경로는 이 저장소를 지나가지 않는다.</b> 피드는 이미 만들어져 있고,
 * 어느 벡터로 만들었는지는 {@code feed_build} 행에 박혀 있기 때문이다. 읽을 때 벡터를
 * 다시 불러오면 조회가 하나 늘고, 그건 이 티켓이 없애려던 바로 그 비용이다.
 *
 * <p>이 저장소를 쓰는 것은 <b>벡터를 만드는 쪽</b>이다 — 그 코드는 아직 없다.
 */
public interface UserTasteVectorRepository extends JpaRepository<UserTasteVector, UUID> {

	/**
	 * 지금 쓰이는 판. 한 사람에게 최대 하나임을 DB 의 조건부 UNIQUE 색인
	 * ({@code uq_user_taste_vector_current})이 보장하므로 {@code Optional} 이 맞다.
	 */
	Optional<UserTasteVector> findByUserIdAndSupersededAtIsNull(UUID userId);
}
