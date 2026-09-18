package com.gabolle.backend.dish.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.dish.domain.DishImage;

public interface DishImageRepository extends JpaRepository<DishImage, UUID> {

	/**
	 * 이 음식의 그림 자리가 이미 있나 — 만들어진 것, 만드는 중인 것, 실패한 것 모두.
	 *
	 * <p>🔴 <b>{@code READY} 만 찾지 않는다.</b> 만드는 중인 것을 못 보면 두 사람이
	 * 동시에 누를 때 같은 그림을 두 번 만든다. 표의 {@code UNIQUE(name_key)} 가 마지막
	 * 관문이지만, 그 앞에서 한 번 보는 것만으로 대부분이 걸러진다.
	 */
	Optional<DishImage> findByNameKey(String nameKey);
}
