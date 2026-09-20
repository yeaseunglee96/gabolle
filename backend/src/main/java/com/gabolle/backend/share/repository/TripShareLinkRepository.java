package com.gabolle.backend.share.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.share.domain.TripShareLink;

public interface TripShareLinkRepository extends JpaRepository<TripShareLink, UUID> {

	/** 비로그인 조회가 URL 의 token 으로 찾는다. 유일 인덱스가 하나임을 보장한다. */
	Optional<TripShareLink> findByToken(String token);

	List<TripShareLink> findByTripIdOrderByCreatedAtDesc(UUID tripId);
}
