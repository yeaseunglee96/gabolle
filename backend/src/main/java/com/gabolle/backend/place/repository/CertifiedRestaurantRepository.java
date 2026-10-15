package com.gabolle.backend.place.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.gabolle.backend.place.domain.Place;

/**
 * 「맛집」·「카페」 갈래 조회 (S15P21E201-2051, 카페는 S15P21E201-2059) — 갈래 표식과 구·동 표식이 둘 다 붙은 장소. 어느 갈래인지는
 * 질의의 {@code tagKey} 가 정한다({@code LOCAL_RESTAURANT} · {@code LOCAL_CAFE}) — 질의는 하나다.
 *
 * <p>{@link PlaceRepository} 에 보태지 않고 따로 둔 것은 이 조회가 {@code place} 와 {@code place_feature} 를 함께 보는 질의라서다.
 * 운영 코드에서 네이티브 쿼리를 쓰지 않는다는 규칙({@link PlaceRepository} 설명)은 그대로 지킨다 — 전부 JPQL 이다.
 *
 * <p>모든 조회가 {@code curationStatus = CURATED} 이고 {@code closedOn IS NULL} 인 장소만 본다. 숨긴 곳·합쳐진 줄·사용자가 고른 곳과
 * 폐업일자가 있는 곳은 「인증 맛집」으로 보여 주면 안 된다. 표식 쪽은 {@code evidenceStatus <> UNKNOWN} 만 건다 — 「모른다」는
 * 있다는 뜻이 아니다. 값이 {@code false} 인 확인된 부재는 JPQL 이 JSONB 안을 못 봐서 여기서 거르지 못하는데, 이 표식들은 적재기가
 * 늘 {@code true} 로만 넣는다.
 */
public interface CertifiedRestaurantRepository extends Repository<Place, UUID> {

	/**
	 * 맛집마다 구·동 표식 하나씩 — {@code (장소, 구/동 열쇠)} 쌍이다. 쌍으로 받아 자바가 구·동별로 센다: 한 장소가 구·동 표식을 둘
	 * 가져도 (적재기가 막지만 DB 는 못 막는다) 구 수가 부풀지 않게 장소를 중복 없이 세려는 것이다.
	 */
	@Query("""
			SELECT p.placeId AS placeId, a.featureKey AS areaKey
			  FROM Place p, PlaceFeature a
			 WHERE a.placeId = p.placeId
			   AND a.featureType = :areaType
			   AND a.evidenceStatus <> com.gabolle.backend.place.domain.PlaceEvidenceStatus.UNKNOWN
			   AND p.curationStatus = com.gabolle.backend.place.domain.CurationStatus.CURATED
			   AND p.closedOn IS NULL
			   AND EXISTS (SELECT 1 FROM PlaceFeature t
			                WHERE t.placeId = p.placeId
			                  AND t.featureType = :tagType
			                  AND t.featureKey = :tagKey
			                  AND t.evidenceStatus <> com.gabolle.backend.place.domain.PlaceEvidenceStatus.UNKNOWN)
			""")
	List<AreaPlace> findAreaPlaces(@Param("tagType") String tagType, @Param("tagKey") String tagKey,
			@Param("areaType") String areaType);

	/** {@link #findAreaPlaces} 한 줄. */
	interface AreaPlace {

		UUID getPlaceId();

		String getAreaKey();
	}

	/**
	 * 경계상자 안에 있는 그 갈래의 장소와 구·동 열쇠, 좌표 (S15P21E201-2059) — 「내 위치에서 가장 가까운 곳의 구」를 구하는 재료다.
	 * JPQL 에 삼각함수가 없어({@code GeoDistance} 설명) 상자로 후보를 좁히고 실제 거리는 자바가 잰다. 구·동 목록 질의
	 * ({@link #findAreaPlaces})와 같은 조건에 좌표 범위만 더했다 — 좌표가 없는 장소는 {@code BETWEEN} 에서 빠진다.
	 */
	@Query("""
			SELECT p.placeId AS placeId, a.featureKey AS areaKey, p.lat AS lat, p.lng AS lng
			  FROM Place p, PlaceFeature a
			 WHERE a.placeId = p.placeId
			   AND a.featureType = :areaType
			   AND a.evidenceStatus <> com.gabolle.backend.place.domain.PlaceEvidenceStatus.UNKNOWN
			   AND p.curationStatus = com.gabolle.backend.place.domain.CurationStatus.CURATED
			   AND p.closedOn IS NULL
			   AND p.lat BETWEEN :minLat AND :maxLat
			   AND p.lng BETWEEN :minLng AND :maxLng
			   AND EXISTS (SELECT 1 FROM PlaceFeature t
			                WHERE t.placeId = p.placeId
			                  AND t.featureType = :tagType
			                  AND t.featureKey = :tagKey
			                  AND t.evidenceStatus <> com.gabolle.backend.place.domain.PlaceEvidenceStatus.UNKNOWN)
			""")
	List<AreaPoint> findAreaPointsWithin(@Param("tagType") String tagType, @Param("tagKey") String tagKey,
			@Param("areaType") String areaType, @Param("minLat") double minLat, @Param("maxLat") double maxLat,
			@Param("minLng") double minLng, @Param("maxLng") double maxLng, Limit limit);

	/** {@link #findAreaPointsWithin} 한 줄. */
	interface AreaPoint {

		UUID getPlaceId();

		String getAreaKey();

		Double getLat();

		Double getLng();
	}

	/**
	 * 구·동 열쇠가 정확히 같은 맛집. 이름·장소 번호 순으로 이미 정렬해 돌려준다 — 상한에 걸렸을 때 어느 행이 남을지가 재현되게.
	 */
	@Query("""
			SELECT p FROM Place p
			 WHERE p.curationStatus = com.gabolle.backend.place.domain.CurationStatus.CURATED
			   AND p.closedOn IS NULL
			   AND EXISTS (SELECT 1 FROM PlaceFeature t
			                WHERE t.placeId = p.placeId
			                  AND t.featureType = :tagType
			                  AND t.featureKey = :tagKey
			                  AND t.evidenceStatus <> com.gabolle.backend.place.domain.PlaceEvidenceStatus.UNKNOWN)
			   AND EXISTS (SELECT 1 FROM PlaceFeature a
			                WHERE a.placeId = p.placeId
			                  AND a.featureType = :areaType
			                  AND a.featureKey = :areaKey
			                  AND a.evidenceStatus <> com.gabolle.backend.place.domain.PlaceEvidenceStatus.UNKNOWN)
			 ORDER BY p.nameKo, p.placeId
			""")
	List<Place> findInArea(@Param("tagType") String tagType, @Param("tagKey") String tagKey,
			@Param("areaType") String areaType, @Param("areaKey") String areaKey, Limit limit);

	/**
	 * 구 전체의 맛집 — 구/동 열쇠가 {@code areaKeyPattern}({@code "해운대구/%"} 꼴, 구 이름의 {@code %}·{@code _} 는 부르는 쪽이
	 * 이스케이프한다. 이스케이프 문자는 {@code \} 로 고정)에 걸리는 장소.
	 */
	@Query("""
			SELECT p FROM Place p
			 WHERE p.curationStatus = com.gabolle.backend.place.domain.CurationStatus.CURATED
			   AND p.closedOn IS NULL
			   AND EXISTS (SELECT 1 FROM PlaceFeature t
			                WHERE t.placeId = p.placeId
			                  AND t.featureType = :tagType
			                  AND t.featureKey = :tagKey
			                  AND t.evidenceStatus <> com.gabolle.backend.place.domain.PlaceEvidenceStatus.UNKNOWN)
			   AND EXISTS (SELECT 1 FROM PlaceFeature a
			                WHERE a.placeId = p.placeId
			                  AND a.featureType = :areaType
			                  AND a.featureKey LIKE :areaKeyPattern ESCAPE '\\'
			                  AND a.evidenceStatus <> com.gabolle.backend.place.domain.PlaceEvidenceStatus.UNKNOWN)
			 ORDER BY p.nameKo, p.placeId
			""")
	List<Place> findInGu(@Param("tagType") String tagType, @Param("tagKey") String tagKey,
			@Param("areaType") String areaType, @Param("areaKeyPattern") String areaKeyPattern, Limit limit);
}
