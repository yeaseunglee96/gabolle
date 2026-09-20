package com.gabolle.backend.preference.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.preference.domain.UserTasteVector;

/**
 * 취향 벡터 판 조회. 피드를 읽는 경로는 여기를 지나가지 않는다 — 피드는 이미 만들어져 있고
 * 어느 벡터로 만들었는지는 {@code feed_build} 행에 박혀 있다. 이것을 쓰는 쪽은 벡터를 만드는
 * 쪽뿐이다.
 */
public interface UserTasteVectorRepository extends JpaRepository<UserTasteVector, UUID> {

	/** 지금 쓰이는 판. 한 사람에게 최대 하나임을 DB 의 조건부 UNIQUE 색인이 보장한다. */
	Optional<UserTasteVector> findByUserIdAndSupersededAtIsNull(UUID userId);
}
