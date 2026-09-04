package com.gabolle.backend.place.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gabolle.backend.place.domain.PlaceFeature;

/**
 * 장소 피처 조회.
 *
 * <p>🔴 여기가 S15P21E201-102 의 완료 기준 <b>"질의 개수가 장소 수에 비례하지 않는다"</b> 를 지키는
 * 자리다. 장소마다 피처를 따로 읽으면 후보 200건에 질의 201번이 나가고, 그것은 응답이 정상이라
 * 테스트로 재지 않으면 안 보인다. 그래서 {@link #findByPlaceIdIn} 하나로 묶어 읽는다.
 */
public interface PlaceFeatureRepository extends JpaRepository<PlaceFeature, UUID> {

	/** 여러 장소의 피처를 한 번에. 🔴 후보 목록을 채울 때 이것 말고 다른 길로 가지 않는다. */
	List<PlaceFeature> findByPlaceIdIn(Collection<UUID> placeIds);

	/** 장소 하나의 피처 전부. 상세 조회(-476)가 쓴다. */
	List<PlaceFeature> findByPlaceId(UUID placeId);

	/**
	 * 갈래별 표식 원본 행 (-473 의 "응답에 건수가 들어 있다" 를 만드는 재료).
	 *
	 * <p>🔴 예전에는 {@code countPlacesByFeature} 로 DB 에서 바로 세었는데, 그 질의는
	 * {@code evidenceStatus <> UNKNOWN} 까지만 걸러서 <b>확인된 부재</b>(값이 JSON 리터럴
	 * {@code false} 인 {@code VERIFIED} 행)를 "있다" 로 세는 결함이 있었다 — JPQL 은
	 * {@code value}(JSONB) 안의 값을 비교할 수 없다. 그래서 지금은 행을 그대로 돌려주고,
	 * 최종 판정(있다·없다)은 호출부인 {@code PlaceFacetService} 가
	 * {@link com.gabolle.backend.place.domain.PlaceFeature#indicatesPresence()} 로 자바에서 한다.
	 * 경계상자로 좁히고 자바에서 실제 거리를 재는 것과 같은 구조다.
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
