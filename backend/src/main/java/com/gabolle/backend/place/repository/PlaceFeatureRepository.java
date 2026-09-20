package com.gabolle.backend.place.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gabolle.backend.place.domain.PlaceFeature;

/**
 * 장소 피처 조회. 질의 개수가 장소 수에 비례하지 않아야 한다 — 장소마다 따로 읽으면 후보 200건에
 * 질의 201번이 나가고, 응답은 정상이라 재지 않으면 안 보인다. 그래서 {@link #findByPlaceIdIn}
 * 하나로 묶어 읽는다.
 */
public interface PlaceFeatureRepository extends JpaRepository<PlaceFeature, UUID> {

	/** 여러 장소의 피처를 한 번에. 후보 목록을 채울 때 이것 말고 다른 길로 가지 않는다. */
	List<PlaceFeature> findByPlaceIdIn(Collection<UUID> placeIds);

	/** 장소 하나의 피처 전부. 상세 조회가 쓴다. */
	List<PlaceFeature> findByPlaceId(UUID placeId);

	/**
	 * 갈래별 표식 원본 행. DB 에서 세지 않고 행을 그대로 돌려주는 이유는, JPQL 이
	 * {@code value}(JSONB) 안을 비교할 수 없어 확인된 부재(값이 {@code false} 인 {@code VERIFIED}
	 * 행)를 "있다" 로 세게 되기 때문이다. 최종 판정은 호출부가
	 * {@link com.gabolle.backend.place.domain.PlaceFeature#indicatesPresence()} 로 자바에서 한다.
	 *
	 * <p>질의는 하나다. 갈래 수나 장소 수만큼 반복하지 않는다.
	 */
	@Query("""
			SELECT f FROM PlaceFeature f
			WHERE f.featureType IN :featureTypes
			  AND f.evidenceStatus <> com.gabolle.backend.place.domain.PlaceEvidenceStatus.UNKNOWN
			ORDER BY f.featureType, f.featureKey
			""")
	List<PlaceFeature> findByFeatureTypeIn(@Param("featureTypes") Collection<String> featureTypes);
}
