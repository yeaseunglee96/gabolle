package com.gabolle.backend.place.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gabolle.backend.place.domain.PlaceEventPeriod;

public interface PlaceEventPeriodRepository extends JpaRepository<PlaceEventPeriod, UUID> {

	/**
	 * 주어진 기간과 겹치는 회차를 시작일 순으로. 겹침 판정을 화면이 아니라 여기서 한다 — 거르는
	 * 규칙이 두 곳에 생기면 한쪽만 고쳐지는 날이 온다.
	 *
	 * <p>{@code ORDER BY} 가 완전히 정해져 있어야({@code startDate}, 동률이면 식별자) 쪽을 나눠도
	 * 같은 행이 두 번 나오거나 조용히 건너뛰어지지 않는다.
	 *
	 * <p>{@code Page} 로 받는 것은 "더 있는지" 를 정확히 알기 위해서다 — 목록만 받으면 상한에 걸린
	 * 것과 마침 그만큼 있는 것을 구분할 수 없고, 화면이 목록을 조용히 자른다.
	 * {@code countQuery} 를 손으로 적은 것은 본문의 {@code ORDER BY} 때문이다 — 자동으로 만든 개수
	 * 질의에 정렬이 섞이면 DB 가 거부한다.
	 */
	@Query(value = """
			SELECT p FROM PlaceEventPeriod p
			WHERE p.startDate <= :to AND p.endDate >= :from
			ORDER BY p.startDate ASC, p.placeEventPeriodId ASC
			""",
			countQuery = """
			SELECT COUNT(p) FROM PlaceEventPeriod p
			WHERE p.startDate <= :to AND p.endDate >= :from
			""")
	Page<PlaceEventPeriod> findOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to,
			Pageable pageable);

	List<PlaceEventPeriod> findByPlaceIdOrderByStartDateAsc(UUID placeId);

	/** 추천 후보 여럿의 회차를 한 번에 — 후보마다 물으면 그 수만큼 질의가 는다. */
	List<PlaceEventPeriod> findByPlaceIdIn(Collection<UUID> placeIds);
}
