package com.gabolle.backend.dish.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.dish.domain.DishDescription;

public interface DishDescriptionRepository extends JpaRepository<DishDescription, UUID> {

	/**
	 * 이 음식의 이 언어 설명이 이미 있나.
	 *
	 * <p>있으면 바깥 모델을 안 부른다 — 1.3~1.9초와 호출값이 통째로 사라진다. 부산에서
	 * 같은 메뉴를 찍는 사람은 여럿이라 이 되찾기가 대부분 맞는다.
	 */
	Optional<DishDescription> findByNameKeyAndLanguage(String nameKey, String language);
}
