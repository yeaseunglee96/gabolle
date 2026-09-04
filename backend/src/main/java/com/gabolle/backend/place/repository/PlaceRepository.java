package com.gabolle.backend.place.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gabolle.backend.place.domain.Place;

/**
 * 장소 조회. 🔴 <b>운영 코드에서 네이티브 쿼리를 쓰지 않는다.</b>
 *
 * <p>{@code JdbcTemplate} 과 네이티브 쿼리는 {@code hibernate.default_schema} 를 물려받지 않아
 * 운영의 {@code gabolle} schema 에 있는 표를 못 찾는다. 그런데 <b>테스트로는 안 잡힌다</b> —
 * 테스트는 표를 {@code public} 에 만들기 때문이다. 실제로 그 상태로 머지돼서 품질 게이트가
 * 운영에서만 죽은 적이 있다 (S15P21E201-546). {@code docs/DB-STANDARD.md} 2절이 그 근거다.
 *
 * <p>그 대가로 거리 계산을 SQL 에서 못 한다(JPQL 에 삼각함수가 없다). 그래서 반경 조회는
 * 경계상자로 후보를 좁힌 뒤 자바에서 거리를 잰다.
 */
public interface PlaceRepository extends JpaRepository<Place, UUID> {

	/**
	 * 이름으로 찾는다 (S15P21E201-462). 한국어 이름과 영문 이름 둘 다 본다.
	 *
	 * <p>🔴 {@code pattern} 은 호출하는 쪽이 이미 이스케이프하고 {@code %} 를 붙인 값이어야 한다.
	 * 사용자가 {@code %} 를 넣으면 전체 스캔이 되고, {@code _} 를 넣으면 엉뚱한 것이 걸린다.
	 * 이스케이프 문자는 {@code \} 로 고정했다.
	 *
	 * <p>정렬은 서비스 계층에서 한다 — 정확일치·접두일치·포함을 나누는 규칙이 SQL 로 표현하기에는
	 * 길고, 이 규모(수백~수천 행)에서는 자바 정렬 비용이 무의미하다.
	 */
	@Query("""
			SELECT p FROM Place p
			WHERE LOWER(p.nameKo) LIKE :pattern ESCAPE '\\'
			   OR (p.nameEn IS NOT NULL AND LOWER(p.nameEn) LIKE :pattern ESCAPE '\\')
			""")
	List<Place> searchByName(@Param("pattern") String pattern, Limit limit);

	/** 이름으로 찾되 종류로 한 번 더 거른다. {@code category} 는 자유 문자열이라 소문자로 맞춰 비교한다. */
	@Query("""
			SELECT p FROM Place p
			WHERE (LOWER(p.nameKo) LIKE :pattern ESCAPE '\\'
			       OR (p.nameEn IS NOT NULL AND LOWER(p.nameEn) LIKE :pattern ESCAPE '\\'))
			  AND LOWER(p.category) = LOWER(:category)
			""")
	List<Place> searchByNameAndCategory(@Param("pattern") String pattern,
			@Param("category") String category, Limit limit);

	/**
	 * 경계상자 안의 장소. 반경 조회(-469)와 후보 사전 필터(-102)가 이것으로 후보를 좁힌 뒤
	 * 자바에서 실제 거리를 잰다.
	 *
	 * <p>좌표가 없는 행은 애초에 빠진다 — {@code ck_place_origin_pair} 때문에 lat 과 lng 는
	 * 함께 있거나 함께 없으므로 lat 만 봐도 된다.
	 */
	@Query("""
			SELECT p FROM Place p
			WHERE p.lat IS NOT NULL
			  AND p.lat BETWEEN :minLat AND :maxLat
			  AND p.lng BETWEEN :minLng AND :maxLng
			""")
	List<Place> findWithinBoundingBox(@Param("minLat") double minLat, @Param("maxLat") double maxLat,
			@Param("minLng") double minLng, @Param("maxLng") double maxLng, Limit limit);

	/**
	 * 어떤 표식을 실제로 가진 장소만, 경계상자 안에서 (-102, -473).
	 *
	 * <p>🔴 {@code evidenceStatus <> UNKNOWN} 이 빠지면 안 된다. UNKNOWN 행은 "그 표식이 있다" 가
	 * 아니라 "모른다" 다. 이 조건이 없으면 확인 안 된 장소가 그 갈래에 섞이고, 그것이 정확히
	 * -473 의 완료 기준 "그 갈래의 표식이 없는 장소가 섞이지 않는다" 를 깨뜨린다.
	 *
	 * <p>{@code featureKey} 가 {@code null} 이면 종류만 보고 키는 안 본다 — 점수형·참거짓형 피처는
	 * 키가 아예 없기 때문이다 ({@code ck_place_feature_key_shape}).
	 */
	@Query("""
			SELECT p FROM Place p
			WHERE p.lat IS NOT NULL
			  AND p.lat BETWEEN :minLat AND :maxLat
			  AND p.lng BETWEEN :minLng AND :maxLng
			  AND EXISTS (SELECT 1 FROM PlaceFeature f
			              WHERE f.placeId = p.placeId
			                AND f.featureType = :featureType
			                AND (:featureKey IS NULL OR f.featureKey = :featureKey)
			                AND f.evidenceStatus <> com.gabolle.backend.place.domain.PlaceEvidenceStatus.UNKNOWN)
			""")
	List<Place> findWithinBoundingBoxHavingFeature(@Param("minLat") double minLat, @Param("maxLat") double maxLat,
			@Param("minLng") double minLng, @Param("maxLng") double maxLng,
			@Param("featureType") String featureType, @Param("featureKey") String featureKey, Limit limit);

	/**
	 * 표식을 가진 장소 (경계상자 없이). 갈래별 목록 조회(-473)가 쓴다.
	 *
	 * <p>UNKNOWN 제외 규칙은 위와 같다.
	 */
	@Query("""
			SELECT p FROM Place p
			WHERE EXISTS (SELECT 1 FROM PlaceFeature f
			              WHERE f.placeId = p.placeId
			                AND f.featureType = :featureType
			                AND (:featureKey IS NULL OR f.featureKey = :featureKey)
			                AND f.evidenceStatus <> com.gabolle.backend.place.domain.PlaceEvidenceStatus.UNKNOWN)
			ORDER BY p.nameKo, p.placeId
			""")
	List<Place> findHavingFeature(@Param("featureType") String featureType,
			@Param("featureKey") String featureKey, Limit limit);

	List<Place> findByPlaceIdIn(Collection<UUID> placeIds);
}
