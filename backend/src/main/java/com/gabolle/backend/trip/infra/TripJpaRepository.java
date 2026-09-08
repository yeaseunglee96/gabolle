package com.gabolle.backend.trip.infra;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TripJpaRepository extends JpaRepository<TripJpaEntity, UUID> {
}
