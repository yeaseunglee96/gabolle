package com.gabolle.backend.place.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.place.domain.PlacePhoto;

public interface PlacePhotoRepository extends JpaRepository<PlacePhoto, Long> {

	List<PlacePhoto> findByPlaceIdOrderByPositionAsc(UUID placeId);
}
