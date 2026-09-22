package com.gabolle.backend.place.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.place.domain.UserInputKind;
import com.gabolle.backend.place.domain.UserPlaceCodeMap;
import com.gabolle.backend.place.domain.UserPlaceCodeMapId;

/**
 * 사용자 입력 코드 ↔ 장소 피처 대조표 조회. 읽기만 한다 — 표는 마이그레이션이 채우고
 * {@code PlaceFeatureCodeMapTest} 가 빠짐을 검사하는데, 런타임에 고칠 수 있으면 그 검사가
 * 무의미해진다.
 *
 * <p>갈래 조회가 이 표를 정본으로 읽는다. 자바에 갈래 목록을 두면 정본이 둘이 되고, 마이그레이션이
 * 9번째 차원을 넣어도 응답은 여덟 개만 나온다.
 */
public interface UserPlaceCodeMapRepository extends JpaRepository<UserPlaceCodeMap, UserPlaceCodeMapId> {

	/** 취향 또는 제약 한쪽의 대조 줄 전부. */
	List<UserPlaceCodeMap> findByIdUserInputKindOrderByIdUserInputCodeAsc(UserInputKind userInputKind);

	/** 어떤 사용자 입력 코드가 어떤 피처들과 짝인지. {@code MOBILITY} 처럼 둘에 걸리는 것이 있다. */
	List<UserPlaceCodeMap> findByIdUserInputKindAndIdUserInputCode(UserInputKind userInputKind, String userInputCode);
}
