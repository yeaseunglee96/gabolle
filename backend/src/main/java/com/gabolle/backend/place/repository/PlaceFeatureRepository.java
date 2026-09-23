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

	/**
	 * 갈래마다 <b>몇 곳</b>에 그 표식이 있는지. 화면이 「이 조건은 판정할 자료가 없어요」를
	 * 말할 수 있게 하는 값이다 (S15P21E201-1508, 프론트는 S15P21E201-1044).
	 *
	 * <p>🔴 <b>{@code UNKNOWN} 근거는 안 센다.</b> 위 {@link #findByFeatureTypeIn} 과 같은
	 * 규칙이고 같은 이유다 — {@code PlaceFeature.imported} 가 <i>"UNKNOWN 피처는 넣지 않는다,
	 * 모른다는 것은 행이 없다는 뜻이다"</i> 라고 못 박아 뒀다. 세는 쪽만 그것을 세면 「자료가
	 * 있다」고 말해 놓고 판정은 못 하는 상태가 된다 — 이 기능이 고치려는 거짓말과 같은 종류다.
	 *
	 * <p>장소 하나에 같은 갈래가 여러 줄일 수 있어({@code featureKey} 가 다르다) 장소를
	 * {@code DISTINCT} 로 센다. 줄을 세면 알레르기 표식 열 줄이 붙은 한 곳이 「열 곳」이 된다.
	 *
	 * <p>질의는 하나다. 갈래 수만큼 반복하지 않는다.
	 */
	@Query("""
			SELECT f.featureType AS featureType, COUNT(DISTINCT f.placeId) AS placeCount
			FROM PlaceFeature f
			WHERE f.featureType IN :featureTypes
			  AND f.evidenceStatus <> com.gabolle.backend.place.domain.PlaceEvidenceStatus.UNKNOWN
			GROUP BY f.featureType
			""")
	List<FeatureTypePlaceCount> countPlacesByFeatureType(@Param("featureTypes") Collection<String> featureTypes);

	/**
	 * 갈래 하나와 그 갈래를 가진 장소 수.
	 *
	 * <p>자료가 <b>한 곳도 없는</b> 갈래는 이 결과에 <b>줄이 아예 없다</b> — 0 을 돌려주지
	 * 않는다. 그것이 이 기능에서 가장 중요한 경우라, 읽는 쪽이 「없는 열쇠 = 0곳」을 알고
	 * 채워야 한다({@code ConditionCoverageService} 가 그렇게 한다).
	 */
	interface FeatureTypePlaceCount {

		String getFeatureType();

		long getPlaceCount();
	}
}
