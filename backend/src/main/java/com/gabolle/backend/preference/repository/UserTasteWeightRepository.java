package com.gabolle.backend.preference.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.preference.domain.UserTasteWeight;
import com.gabolle.backend.preference.domain.UserTasteWeightId;

/**
 * 취향 벡터의 성분 저장. 읽기 경로는 여기를 지나가지 않는다 — 이것을 쓰는 쪽은 벡터를 만드는
 * 쪽뿐이다.
 */
public interface UserTasteWeightRepository extends JpaRepository<UserTasteWeight, UserTasteWeightId> {

	List<UserTasteWeight> findByIdTasteVectorId(java.util.UUID tasteVectorId);
}
