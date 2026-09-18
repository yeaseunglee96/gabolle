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
	 * <p><b>보여주는 순서</b>는 서비스 계층에서 정한다 — 정확일치·접두일치·포함을 나누는 규칙이
	 * SQL 로 표현하기에는 길고, 이 규모(수백~수천 행)에서는 자바 정렬 비용이 무의미하다.
	 *
	 * <p>🔴 <b>아래 {@code ORDER BY} 는 그것과 다른 일을 한다</b> (S15P21E201-1011). 서비스의
	 * 정렬은 <b>이미 받아 온 행들을 어떤 차례로 보여줄까</b> 이고, 이 {@code ORDER BY} 는
	 * <b>상한에 걸렸을 때 어느 행이 애초에 넘어올까</b> 다. 정렬이 없으면 SQL 은 그것을
	 * 아무것도 약속하지 않아서, <b>같은 검색어에 매번 다른 결과가 나올 수 있다</b> — 서비스가
	 * 뒤에서 아무리 잘 정렬해도 손에 든 것 자체가 매번 다르면 소용이 없다.
	 * 바로 아래 경계상자 조회가 같은 이유로 이미 {@code ORDER BY} 를 달고 있다(-724).
	 */
	@Query("""
			SELECT p FROM Place p
			WHERE LOWER(p.nameKo) LIKE :pattern ESCAPE '\\'
			   OR (p.nameEn IS NOT NULL AND LOWER(p.nameEn) LIKE :pattern ESCAPE '\\')
			ORDER BY p.placeId
			""")
	List<Place> searchByName(@Param("pattern") String pattern, Limit limit);

	/**
	 * 이름으로 찾되 종류로 한 번 더 거른다. {@code category} 는 자유 문자열이라 소문자로 맞춰 비교한다.
	 *
	 * <p>🔴 {@code ORDER BY} 는 위 조회와 같은 이유다 (-1011).
	 */
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
	 * 경계상자 안의 장소. 반경 조회(-469)와 후보 사전 필터(-102)가 이것으로 후보를 좁힌 뒤
	 * 자바에서 실제 거리를 잰다.
	 *
	 * <p>좌표가 없는 행은 애초에 빠진다 — {@code ck_place_origin_pair} 때문에 lat 과 lng 는
	 * 함께 있거나 함께 없으므로 lat 만 봐도 된다.
	 *
	 * <p>🔴 <b>{@code ORDER BY} 가 왜 있어야 하나</b> (S15P21E201-724). 이 조회에는
	 * {@code limit} 이 걸린다. 정렬이 없으면 <b>상한에 걸렸을 때 어느 행이 남는지 SQL 이
	 * 아무것도 약속하지 않는다</b> — 같은 요청을 두 번 보내면 다른 장소가 나올 수 있고,
	 * 그러면 {@code recommendation_candidate} 에 남은 기록으로도 "왜 그때 그 장소가
	 * 후보에 없었나" 를 되짚을 수 없다. 거리순으로 정렬하고 싶지만 JPQL 에 삼각함수가
	 * 없어서 못 한다(위 주석). 그래서 <b>재현 가능하기만 한</b> 순서를 쓴다 — 어느 행이
	 * 남는지가 <b>정해져 있다</b>는 것이 여기서 필요한 전부다. 지리적으로 고르게 남기는
	 * 일은 정렬이 아니라 상한을 넉넉히 두는 쪽이 한다
	 * ({@code gabolle.place.candidate-max-scanned}).
	 */
	@Query("""
			SELECT p FROM Place p
			WHERE p.lat IS NOT NULL
			  AND p.lat BETWEEN :minLat AND :maxLat
			  AND p.lng BETWEEN :minLng AND :maxLng
			ORDER BY p.placeId
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
	 *
	 * <p>🔴 {@code ORDER BY} 는 위 조회와 같은 이유다 — 상한에 걸렸을 때 어느 행이 남는지를
	 * 정해 둔다 (S15P21E201-724).
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
			ORDER BY p.placeId
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

	/**
	 * 종류(category)만으로 거른다 — 숙소 후보 조회(S15P21E201-456)가 쓴다. 이름 검색과
	 * 달리 검색어가 없어 {@link #searchByNameAndCategory} 를 재사용할 수 없다.
	 *
	 * <p>{@code category} 는 자유 문자열이라 소문자로 맞춰 비교한다 — 호출하는 쪽이
	 * 이미 소문자로 넘겨야 한다({@code AccommodationCategories} 처럼 대문자로 적어 둔
	 * 목록은 서비스 계층에서 소문자로 바꿔 넘긴다).
	 *
	 * <p>🔴 {@code ORDER BY} 가 있는 이유는 다른 조회들과 같다(S15P21E201-724) — limit 에
	 * 걸렸을 때 어느 행이 남는지가 정해져 있어야 재현 가능하다.
	 */
	@Query("""
			SELECT p FROM Place p
			WHERE LOWER(p.category) IN :categories
			ORDER BY p.nameKo, p.placeId
			""")
	List<Place> findByCategoryIn(@Param("categories") List<String> categories, Limit limit);

	/**
	 * 지금 장소가 하나라도 있는 {@code category} 값과 그 수 — S15P21E201-896.
	 *
	 * <p>취향 화면이 고를 수 있는 갈래를 정하는 데 쓴다. 추천 엔진은 앱이 보낸 갈래 코드를
	 * {@code place.category} 와 글자 그대로 비교하므로({@code BaselineCandidateTranslator}),
	 * 값이 하나도 없는 갈래를 고른 사용자는 후보 0 으로 일정 생성이 실패한다. 그 실패를
	 * 막으려면 화면이 "지금 장소가 있는 갈래" 를 알아야 한다.
	 *
	 * <p><b>갈래 목록을 자바에 적지 않는다.</b> 있는 값을 세어서 그대로 낸다 — 그래야 적재가
	 * 새 갈래를 넣으면 코드 변경 없이 나타나고, 서버가 앱의 어휘를 대신 확정하지 않는다.
	 * {@code place.category} 는 값 목록이 확정되지 않은 자유 문자열이다({@code V20260904000000}
	 * 마이그레이션 주석).
	 *
	 * <p>비교는 글자 그대로다 — 여기서 {@code LOWER} 를 쓰지 않는다. 같은 갈래가 대소문자만
	 * 다르게 적재돼 있으면 두 줄로 나오는데, 그것이 사실이고 화면이 둘 다 못 맞춘다는 신호다.
	 * 여기서 합쳐 버리면 그 어긋남이 조용히 숨는다.
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
