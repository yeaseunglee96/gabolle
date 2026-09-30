package com.gabolle.backend.place.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
 *
 * <p><b>찾아 주는 조회는 모두 {@code curationStatus = CURATED} 를 본다</b> — S15P21E201-1426.
 * 사용자가 기록에 붙이려고 고른 장소는 서버가 만들지만 아무도 검증하지 않은 값이라, 검색·
 * 주변·추천 후보 어디에도 안 나온다. 조건을 질의마다 되풀이해 적는 것은 JPQL 이 조각을
 * 공유할 방법을 주지 않아서다 — 새 조회를 더할 때 이 줄을 빠뜨리면 검증 안 된 장소가 샌다.
 *
 * <p>🔴 {@code findById}·{@code findAllById}·{@link #findByPlaceIdIn} 은 거르지 <b>않는다.</b>
 * 그것은 「찾아 주기」가 아니라 「이미 이어진 것을 읽기」다. 기록에 붙은 장소를 화면에 그리려면
 * 그 행을 읽을 수 있어야 하고, 거기서 걸러 버리면 자기가 고른 장소가 글에서 사라진다.
 */
public interface PlaceRepository extends JpaRepository<Place, UUID> {

	/**
	 * 이름으로 찾는다. 한국어·영문 이름과 관광공사 일본어·중국어(간체·번체) 공식 이름을 본다.
	 *
	 * <p>🔴 일본어·중국어 이름도 본다(S15P21E201-1875). 앱이 일본어 화면에 「海雲台海水浴場」을 보여주는데
	 * 그 이름으로 검색하면 0건이었다 — 여행자는 화면·안내 책자에서 본 이름을 그대로 친다.
	 *
	 * <p>{@code pattern} 은 호출하는 쪽이 이미 이스케이프하고 {@code %} 를 붙인 값이어야 한다.
	 * 사용자가 {@code %} 를 넣으면 전체 스캔이 되고 {@code _} 를 넣으면 엉뚱한 것이 걸린다.
	 * 이스케이프 문자는 {@code \} 로 고정했다.
	 *
	 * <p>🔴 이름과 검색어 둘 다 <b>공백을 뗀 뒤</b> 비교한다(S15P21E201-1745). 「해운대 해수욕장」이 정본의
	 * 「해운대해수욕장」을 못 찾아 0건이었다. {@code pattern} 도 공백을 뗀 값이어야 한다.
	 *
	 * <p>보여주는 순서는 서비스 계층이 정한다. 아래 {@code ORDER BY} 는 그것과 다른 일을 한다 —
	 * 상한에 걸렸을 때 어느 행이 넘어올지를 정한다.
	 */
	@Query("""
			SELECT p FROM Place p
			WHERE p.curationStatus = com.gabolle.backend.place.domain.CurationStatus.CURATED
			  AND (REPLACE(LOWER(p.nameKo), ' ', '') LIKE :pattern ESCAPE '\\'
			       OR (p.nameEn IS NOT NULL AND REPLACE(LOWER(p.nameEn), ' ', '') LIKE :pattern ESCAPE '\\')
			       OR (p.nameJa IS NOT NULL AND REPLACE(LOWER(p.nameJa), ' ', '') LIKE :pattern ESCAPE '\\')
			       OR (p.nameZhHans IS NOT NULL AND REPLACE(LOWER(p.nameZhHans), ' ', '') LIKE :pattern ESCAPE '\\')
			       OR (p.nameZhHant IS NOT NULL AND REPLACE(LOWER(p.nameZhHant), ' ', '') LIKE :pattern ESCAPE '\\'))
			ORDER BY p.placeId
			""")
	List<Place> searchByName(@Param("pattern") String pattern, Limit limit);

	/** 이름으로 찾되 종류로 한 번 더 거른다. {@code category} 는 자유 문자열이라 소문자로 맞춰 비교한다. */
	@Query("""
			SELECT p FROM Place p
			WHERE (REPLACE(LOWER(p.nameKo), ' ', '') LIKE :pattern ESCAPE '\\'
			       OR (p.nameEn IS NOT NULL AND REPLACE(LOWER(p.nameEn), ' ', '') LIKE :pattern ESCAPE '\\')
			       OR (p.nameJa IS NOT NULL AND REPLACE(LOWER(p.nameJa), ' ', '') LIKE :pattern ESCAPE '\\')
			       OR (p.nameZhHans IS NOT NULL AND REPLACE(LOWER(p.nameZhHans), ' ', '') LIKE :pattern ESCAPE '\\')
			       OR (p.nameZhHant IS NOT NULL AND REPLACE(LOWER(p.nameZhHant), ' ', '') LIKE :pattern ESCAPE '\\'))
			  AND LOWER(p.category) = LOWER(:category)
			  AND p.curationStatus = com.gabolle.backend.place.domain.CurationStatus.CURATED
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
			  AND p.curationStatus = com.gabolle.backend.place.domain.CurationStatus.CURATED
			ORDER BY p.placeId
			""")
	List<Place> findWithinBoundingBox(@Param("minLat") double minLat, @Param("maxLat") double maxLat,
			@Param("minLng") double minLng, @Param("maxLng") double maxLng, Limit limit);

	/**
	 * 경계상자 안의 <b>모든</b> 장소의 이름·갈래·좌표 — 적재기가 같은 장소를 다시 넣지 않으려고 본다
	 * (S15P21E201-1620, {@code SamePlaceGuard}).
	 *
	 * <p>🔴 {@link #findWithinBoundingBox} 와 달리 상태를 가리지 않는다 — 합쳐진 줄·숨긴 줄·사용자가 고른 곳·문 닫은
	 * 곳도 나온다. 합쳐진 줄과 같은 곳이 새 번호로 들어오면 합치기가 없던 일이 되고, 숨긴 곳이 새 번호로 들어오면 숨김이
	 * 풀린다. 엔티티가 아니라 여섯 칸만 받는다 — 부산 전체가 한 덩어리에 걸리기도 한다.
	 */
	@Query("""
			SELECT p.placeId AS placeId, p.nameKo AS nameKo, p.category AS category, p.lat AS lat, p.lng AS lng,
			       p.sourceType AS sourceType
			  FROM Place p
			 WHERE p.lat BETWEEN :minLat AND :maxLat
			   AND p.lng BETWEEN :minLng AND :maxLng
			""")
	List<NamedSpot> findNamedSpotsWithin(@Param("minLat") double minLat, @Param("maxLat") double maxLat,
			@Param("minLng") double minLng, @Param("maxLng") double maxLng);

	/** {@link #findNamedSpotsWithin} 의 한 줄. */
	interface NamedSpot {

		UUID getPlaceId();

		String getNameKo();

		String getCategory();

		Double getLat();

		Double getLng();

		String getSourceType();
	}

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
			  AND p.curationStatus = com.gabolle.backend.place.domain.CurationStatus.CURATED
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
			WHERE p.curationStatus = com.gabolle.backend.place.domain.CurationStatus.CURATED
			  AND EXISTS (SELECT 1 FROM PlaceFeature f
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
	 * 기간표가 있는데 {@code [from, to]} 에 하루도 안 여는 장소 — 추천 후보에서 뺀다 (S15P21E201-1618).
	 *
	 * <p>일정 조립이 쓰는 판정({@code PlaceEventScheduleAdapter} — 여행 날짜 중 하루라도 회차 기간 안이면 연다)과
	 * 같은 뜻이다. 기간표가 없는 장소는 여기 안 나온다 — 상시 여는 곳이다.
	 */
	@Query("""
			SELECT DISTINCT e.placeId FROM PlaceEventPeriod e
			 WHERE e.placeId IN :placeIds
			   AND NOT EXISTS (SELECT o.placeEventPeriodId FROM PlaceEventPeriod o
			                    WHERE o.placeId = e.placeId AND o.startDate <= :to AND o.endDate >= :from)
			""")
	List<UUID> findEventPlacesClosedThroughout(@Param("placeIds") Collection<UUID> placeIds,
			@Param("from") LocalDate from, @Param("to") LocalDate to);

	/**
	 * 원천 식별자로 찾는다 — 같은 장소를 두 번 만들지 않기 위한 조회다 (S15P21E201-1426).
	 *
	 * <p>🔴 {@code curationStatus} 를 안 본다. 사용자가 카카오에서 해운대해수욕장을 고르면
	 * 이미 있는 <b>큐레이션 행</b>을 찾아야 한다 — 거기서 CURATED 만 보든 USER_SUBMITTED 만
	 * 보든, 못 찾으면 같은 카카오 장소로 행이 하나 더 생기고 {@code uq_place_source} 에
	 * 부딪힌다.
	 *
	 * <p>{@code uq_place_source} 가 두 칸의 짝을 유일하게 만들므로 결과는 최대 하나다.
	 */
	Optional<Place> findBySourceTypeAndSourceId(String sourceType, String sourceId);

	/**
	 * 한 출처의 장소 여럿을 원천 번호로 한 번에 찾는다 — 사진 적재기(S15P21E201-1606).
	 *
	 * <p>🔴 장소 번호를 원천 번호에서 계산해 찾지 않는 이유: 관광공사 장소 중 마이그레이션으로 손수
	 * 넣은 것은 번호가 계산 규칙과 다르다(운영의 「구상반려암」·「백양산」). 원천 번호는 모든
	 * 출처에 있고 {@code uq_place_source} 가 유일하게 지킨다.
	 */
	List<Place> findBySourceTypeAndSourceIdIn(String sourceType, Collection<String> sourceIds);

	/**
	 * 종류(category)만으로 거른다. 검색어가 없어 {@link #searchByNameAndCategory} 를 재사용할 수 없다.
	 * {@code category} 는 자유 문자열이라 소문자로 맞춰 비교하므로, 호출하는 쪽이 이미 소문자로
	 * 넘겨야 한다.
	 */
	@Query("""
			SELECT p FROM Place p
			WHERE LOWER(p.category) IN :categories
			  AND p.curationStatus = com.gabolle.backend.place.domain.CurationStatus.CURATED
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
			  AND p.curationStatus = com.gabolle.backend.place.domain.CurationStatus.CURATED
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
