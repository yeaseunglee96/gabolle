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
 * 장소 조회. 운영 코드에서 네이티브 쿼리를 쓰지 않는다 — {@code JdbcTemplate} 과 네이티브 쿼리는
 * {@code hibernate.default_schema} 를 물려받지 않아 운영의 {@code gabolle} schema 에 있는 표를 못
 * 찾는데, 테스트는 표를 {@code public} 에 만들어서 이 어긋남을 못 잡는다
 * ({@code docs/DB-STANDARD.md} 2절).
 *
 * <p>그 대가로 거리 계산을 SQL 에서 못 한다(JPQL 에 삼각함수가 없다). 그래서 반경 조회는
 * 경계상자로 후보를 좁힌 뒤 자바에서 거리를 잰다.
 *
 * <p>{@code limit} 이 걸리는 조회에는 모두 {@code ORDER BY} 가 있어야 한다. 정렬이 없으면 상한에
 * 걸렸을 때 어느 행이 남는지 SQL 이 아무것도 약속하지 않아 같은 요청이 매번 다른 결과를 낸다.
 * 거리순으로 정렬하고 싶지만 삼각함수가 없어 못 하므로, 재현 가능하기만 한 순서를 쓴다.
 */
public interface PlaceRepository extends JpaRepository<Place, UUID> {

	/**
	 * 이름으로 찾는다. 한국어 이름과 영문 이름 둘 다 본다.
	 *
	 * <p>{@code pattern} 은 호출하는 쪽이 이미 이스케이프하고 {@code %} 를 붙인 값이어야 한다.
	 * 사용자가 {@code %} 를 넣으면 전체 스캔이 되고 {@code _} 를 넣으면 엉뚱한 것이 걸린다.
	 * 이스케이프 문자는 {@code \} 로 고정했다.
	 *
	 * <p>보여주는 순서는 서비스 계층이 정한다. 아래 {@code ORDER BY} 는 그것과 다른 일을 한다 —
	 * 상한에 걸렸을 때 어느 행이 넘어올지를 정한다.
	 */
	@Query("""
			SELECT p FROM Place p
			WHERE LOWER(p.nameKo) LIKE :pattern ESCAPE '\\'
			   OR (p.nameEn IS NOT NULL AND LOWER(p.nameEn) LIKE :pattern ESCAPE '\\')
			ORDER BY p.placeId
			""")
	List<Place> searchByName(@Param("pattern") String pattern, Limit limit);

	/** 이름으로 찾되 종류로 한 번 더 거른다. {@code category} 는 자유 문자열이라 소문자로 맞춰 비교한다. */
	@Query("""
			SELECT p FROM Place p
			WHERE (LOWER(p.nameKo) LIKE :pattern ESCAPE '\\'
			       OR (p.nameEn IS NOT NULL AND LOWER(p.nameEn) LIKE :pattern ESCAPE '\\'))
			  AND LOWER(p.category) = LOWER(:category)
			ORDER BY p.placeId
			""")
	List<Place> searchByNameAndCategory(@Param("pattern") String pattern,
			@Param("category") String category, Limit limit);

	/**
	 * 경계상자 안의 장소. 반경 조회와 후보 사전 필터가 이것으로 후보를 좁힌 뒤 자바에서 실제
	 * 거리를 잰다. 좌표가 없는 행은 애초에 빠진다 — {@code ck_place_origin_pair} 때문에 lat 과 lng
	 * 는 함께 있거나 함께 없으므로 lat 만 봐도 된다.
	 *
	 * <p>지리적으로 고르게 남기는 일은 정렬이 아니라 상한을 넉넉히 두는 쪽이 한다
	 * ({@code gabolle.place.candidate-max-scanned}).
	 *
	 * <p>문 닫은 가게는 뺀다. 다만 {@code closedOn IS NULL} 은 「영업 중」이 아니라 「모른다」라,
	 * 빼는 것은 폐업일자가 실제로 있는 줄뿐이고 모르는 것은 후보에 남는다.
	 */
	@Query("""
			SELECT p FROM Place p
			WHERE p.lat IS NOT NULL
			  AND p.lat BETWEEN :minLat AND :maxLat
			  AND p.lng BETWEEN :minLng AND :maxLng
			  AND p.closedOn IS NULL
			ORDER BY p.placeId
			""")
	List<Place> findWithinBoundingBox(@Param("minLat") double minLat, @Param("maxLat") double maxLat,
			@Param("minLng") double minLng, @Param("maxLng") double maxLng, Limit limit);

	/**
	 * 어떤 표식을 실제로 가진 장소만, 경계상자 안에서.
	 *
	 * <p>{@code evidenceStatus <> UNKNOWN} 이 빠지면 안 된다. UNKNOWN 행은 "그 표식이 있다" 가
	 * 아니라 "모른다" 라서, 이 조건이 없으면 확인 안 된 장소가 그 갈래에 섞인다.
	 * {@code featureKey} 가 {@code null} 이면 종류만 보고 키는 안 본다 — 점수형·참거짓형 피처는
	 * 키가 아예 없다 ({@code ck_place_feature_key_shape}).
	 */
	@Query("""
			SELECT p FROM Place p
			WHERE p.lat IS NOT NULL
			  AND p.lat BETWEEN :minLat AND :maxLat
			  AND p.lng BETWEEN :minLng AND :maxLng
			  AND p.closedOn IS NULL
			  AND EXISTS (SELECT 1 FROM PlaceFeature f
			              WHERE f.placeId = p.placeId
			                AND f.featureType = :featureType
			                AND (:featureKey IS NULL OR f.featureKey = :featureKey)
			                AND f.evidenceStatus <> com.gabolle.backend.place.domain.PlaceEvidenceStatus.UNKNOWN)
			ORDER BY p.placeId
			""")
	List<Place> findWithinBoundingBoxHavingFeature(@Param("minLat") double minLat, @Param("maxLat") double maxLat,
			@Param("minLng") double minLng, @Param("maxLng") double maxLng,
			@Param("featureType") String featureType, @Param("featureKey") String featureKey, Limit limit);

	/** 표식을 가진 장소 (경계상자 없이). UNKNOWN 제외 규칙은 위와 같다. */
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

	/**
	 * 종류(category)만으로 거른다. 검색어가 없어 {@link #searchByNameAndCategory} 를 재사용할 수 없다.
	 * {@code category} 는 자유 문자열이라 소문자로 맞춰 비교하므로, 호출하는 쪽이 이미 소문자로
	 * 넘겨야 한다.
	 */
	@Query("""
			SELECT p FROM Place p
			WHERE LOWER(p.category) IN :categories
			ORDER BY p.nameKo, p.placeId
			""")
	List<Place> findByCategoryIn(@Param("categories") List<String> categories, Limit limit);

	/**
	 * 지금 장소가 하나라도 있는 {@code category} 값과 그 수. 추천 엔진이 갈래 코드를
	 * {@code place.category} 와 글자 그대로 비교하므로, 값이 없는 갈래를 고른 사용자는 후보 0 으로
	 * 일정 생성이 실패한다. 갈래 목록을 자바에 적지 않고 있는 값을 세는 것은 적재가 새 갈래를
	 * 넣으면 코드 변경 없이 나타나게 하기 위해서다.
	 *
	 * <p>{@code LOWER} 를 쓰지 않는다. 같은 갈래가 대소문자만 다르게 적재돼 있으면 두 줄로 나오는데,
	 * 그것이 사실이고 화면이 둘 다 못 맞춘다는 신호다.
	 */
	@Query("""
			SELECT p.category AS code, COUNT(p) AS placeCount
			FROM Place p
			WHERE p.category IS NOT NULL AND p.category <> ''
			GROUP BY p.category
			ORDER BY COUNT(p) DESC, p.category ASC
			""")
	List<CategoryCount> countByCategory();

	/** {@link #countByCategory()} 한 줄. */
	interface CategoryCount {

		String getCode();

		long getPlaceCount();
	}
}
