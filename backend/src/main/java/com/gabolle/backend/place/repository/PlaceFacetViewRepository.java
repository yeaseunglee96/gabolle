package com.gabolle.backend.place.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.gabolle.backend.place.domain.PlaceFacetView;

public interface PlaceFacetViewRepository extends JpaRepository<PlaceFacetView, UUID> {

	/**
	 * 갈래별 열람 수를 많은 순으로. 세는 것을 DB 에서 한다 — 행을 전부 읽어 와 세면 기록이 쌓이는
	 * 만큼 느려지는데, 이 집계는 원래 많이 쌓인 뒤에 보는 것이다.
	 *
	 * <p>같은 수일 때 갈래 코드로 한 번 더 정렬한다 — 순서가 호출마다 흔들리면 사람이 두 번
	 * 조회해서 비교할 수 없다.
	 */
	@Query("""
			SELECT v.facetKey, COUNT(v)
			FROM PlaceFacetView v
			GROUP BY v.facetKey
			ORDER BY COUNT(v) DESC, v.facetKey ASC
			""")
	List<Object[]> countByFacetKey();
}
