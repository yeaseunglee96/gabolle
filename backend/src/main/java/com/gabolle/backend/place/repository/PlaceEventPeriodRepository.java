package com.gabolle.backend.place.repository;

import java.time.LocalDate;
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
	 * 주어진 기간과 겹치는 회차를 시작일 순으로 (S15P21E201-465).
	 *
	 * <p>겹침 판정을 여기서 하는 것이 이 묶음의 핵심이다 — 화면이 전체를 받아 거르면 안 된다는
	 * 것이 완료 기준(-117)에 명시돼 있다. 축제가 수천 건이 되면 화면이 그걸 다 받을 수 없고,
	 * 무엇보다 거르는 규칙이 두 곳에 생기면 한쪽만 고쳐지는 날이 온다.
	 *
	 * <p>🔴 <b>쪽을 나눠 준다</b> (S15P21E201-1011). 위 주석이 "축제가 수천 건이 되면 화면이
	 * 그걸 다 받을 수 없다" 고 적어 두고도 <b>행 수에는 상한이 없었다</b> — 기간을 좁히는
	 * 것만으로는 한 기간 <i>안</i>의 회차 수를 못 막는다. 이미 {@code ORDER BY} 가 완전히
	 * 정해져 있어서({@code startDate}, 동률이면 식별자) 쪽을 나눠도 같은 행이 두 번 나오거나
	 * 조용히 건너뛰어지지 않는다.
	 *
	 * <p>🔴 {@code Page} 로 받는 이유는 <b>"더 있는지" 를 정확히 알기 위해서</b>다. 목록만
	 * 받으면 "상한에 걸린 것" 과 "마침 그만큼 있는 것" 을 구분할 수 없고, 구분 못 하면 화면이
	 * 목록을 <b>조용히 자른다.</b> {@code countQuery} 를 손으로 적은 것은 본문에
	 * {@code ORDER BY} 가 있어서다 — 자동으로 만든 개수 질의에 정렬이 섞이면 DB 가 거부한다.
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
}
