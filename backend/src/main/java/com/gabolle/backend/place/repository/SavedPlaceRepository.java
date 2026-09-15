package com.gabolle.backend.place.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.place.domain.SavedPlace;

public interface SavedPlaceRepository extends JpaRepository<SavedPlace, UUID> {

	/**
	 * 내가 저장한 장소 전부 — 저장 탭과 홈 캐러셀의 하트 표시를 되살린다.
	 *
	 * <p>최근에 저장한 것이 먼저다. 저장 탭이 그 순서로 보여 준다.
	 *
	 * <p>🔴 상한을 두지 않는다. 사람이 손으로 하트를 누른 수만큼이라 <b>사람 손이 상한</b>이고,
	 * 끝없이 자라는 목록이 아니다 (S15P21E201-1011 이 상한을 붙인 목록들과 그 점이 다르다).
	 * 그래도 언젠가 쪽나눔이 필요해지면 그때는 "더 있다" 칸을 함께 넣어야 한다 — 상한만 두고
	 * 안 알리면 목록이 조용히 잘린다.
	 */
	List<SavedPlace> findByUserIdOrderByCreatedAtDesc(UUID userId);

	boolean existsByUserIdAndPlaceId(UUID userId, UUID placeId);

	void deleteByUserIdAndPlaceId(UUID userId, UUID placeId);
}
