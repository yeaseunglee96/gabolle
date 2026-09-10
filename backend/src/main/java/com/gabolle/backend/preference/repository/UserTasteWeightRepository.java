package com.gabolle.backend.preference.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.preference.domain.UserTasteWeight;
import com.gabolle.backend.preference.domain.UserTasteWeightId;

/**
 * 취향 벡터의 성분 저장 — MLOps Phase 1.
 *
 * <p>🔴 <b>읽기 경로는 이 저장소를 지나가지 않는다.</b> 피드는 이미 만들어져 있고 어느
 * 벡터로 만들었는지는 {@code feed_build} 행에 박혀 있다. 이것을 쓰는 것은 벡터를
 * <b>만드는</b> 쪽뿐이다.
 */
public interface UserTasteWeightRepository extends JpaRepository<UserTasteWeight, UserTasteWeightId> {

	List<UserTasteWeight> findByIdTasteVectorId(java.util.UUID tasteVectorId);
}
