package com.gabolle.backend.editorial.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.editorial.domain.EditorialPickPlace;
import com.gabolle.backend.editorial.domain.EditorialPickPlaceId;

/** {@link EditorialPickPlace} 조회. */
public interface EditorialPickPlaceRepository
		extends JpaRepository<EditorialPickPlace, EditorialPickPlaceId> {

	/**
	 * 한 Pick 의 장소를 편집자가 정한 순서대로.
	 *
	 * <p> {@code pickRank} 로 정렬한다 — 저장 순서나 {@code place_id} 순서가 아니다. 그 순서가
	 * 코스 자체이므로, 정렬을 빼면 코스가 뒤섞인 채로 아무 오류 없이 나간다.
	 */
	List<EditorialPickPlace> findByIdPickIdOrderByPickRankAsc(UUID pickId);
}
