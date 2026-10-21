package com.gabolle.backend.facility.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gabolle.backend.facility.domain.Facility;
import com.gabolle.backend.facility.domain.FacilityKind;

/**
 * 근처 편의시설 조회·적재 (S15P21E201-2052). 운영 코드에서 네이티브 쿼리를 쓰지 않는 이 저장소의 규칙대로 전부 JPQL 이다.
 *
 * <p>거리 계산은 DB 가 하지 않는다 — PostGIS 를 쓰지 않고(docs/DB-STANDARD.md 4절) JPQL 에는 삼각함수가 없다. 경계상자(반경을
 * 감싸는 네모)로 후보를 좁혀 받아 서비스가 실제 거리로 다시 거른다.
 */
public interface FacilityRepository extends JpaRepository<Facility, UUID> {

	/**
	 * 경계상자 안의 한 종류. {@code ix_facility_kind_coords}(종류, 위도, 경도) 색인을 탄다. 순서를 위도·경도·번호로 못 박은 것은
	 * 상한({@code limit})에 걸렸을 때 어느 행이 남을지가 늘 같게 하려는 것이다 — 결과의 순서는 서비스가 다시 정한다.
	 */
	@Query("""
			SELECT f FROM Facility f
			 WHERE f.kind = :kind
			   AND f.lat BETWEEN :minLat AND :maxLat
			   AND f.lng BETWEEN :minLng AND :maxLng
			 ORDER BY f.lat, f.lng, f.facilityId
			""")
	List<Facility> findInBox(@Param("kind") FacilityKind kind, @Param("minLat") double minLat,
			@Param("maxLat") double maxLat, @Param("minLng") double minLng, @Param("maxLng") double maxLng, Limit limit);

	/** 적재기가 이미 있는 행을 찾는다 — 있으면 갱신하고 없으면 넣는다. */
	List<Facility> findByKindAndSourceIdIn(FacilityKind kind, Collection<String> sourceIds);

	/** 한 종류의 행 수. 적재기가 「파일에 없는 기존 행」을 세는 데 쓴다. */
	long countByKind(FacilityKind kind);
}
