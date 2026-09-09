package com.gabolle.backend.place.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gabolle.backend.place.domain.PlaceEventPeriod;

public interface PlaceEventPeriodRepository extends JpaRepository<PlaceEventPeriod, UUID> {

	/**
	 * 주어진 기간과 겹치는 회차를 시작일 순으로 (S15P21E201-465).
	 *
	 * <p>겹침 판정을 여기서 하는 것이 이 묶음의 핵심이다 — 화면이 전체를 받아 거르면 안 된다는
	 * 것이 완료 기준(-117)에 명시돼 있다. 축제가 수천 건이 되면 화면이 그걸 다 받을 수 없고,
	 * 무엇보다 거르는 규칙이 두 곳에 생기면 한쪽만 고쳐지는 날이 온다.
	 */
	@Query("""
			SELECT p FROM PlaceEventPeriod p
			WHERE p.startDate <= :to AND p.endDate >= :from
			ORDER BY p.startDate ASC, p.placeEventPeriodId ASC
			""")
	List<PlaceEventPeriod> findOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to);

	List<PlaceEventPeriod> findByPlaceIdOrderByStartDateAsc(UUID placeId);
}
