package com.gabolle.backend.place;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.api.PlaceSummaryResponse;
import com.gabolle.backend.place.service.PlaceSearchService;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 부산 대표 명소를 넣는 마이그레이션이 실제로 도는지, 그리고 그 결과를 앱의 검색이 찾는지 본다.
 *
 * <p>통합 시험들이 한 DB 를 같이 쓰고 그중 하나가 조건 없는 {@code DELETE FROM place} 를
 * 돌리므로, 바탕을 남에게 맡기지 않고 검사 직전에 그 SQL 을 다시 돌린다. 두 번 돌려도
 * 안전하다 — 마이그레이션이 {@code WHERE NOT EXISTS} 가드를 들고 있다.
 *
 * <p>마이그레이션의 약속은 "네 곳이 정본으로 존재한다" 이지 "반드시 우리가 넣는다" 가
 * 아니다. 같은 이름이 이미 있으면 일부러 넣지 않으므로, 삽입 여부가 아니라 존재 여부를 본다.
 */
class CuratedLandmarkMigrationIntegrationTest extends PlacePostgresIntegrationTest {

	/** 이름 → 카카오 Local 출처 ID. 마이그레이션이 넣는 네 곳이다. */
	private static final Map<String, String> CORE_LANDMARKS = Map.of(
			"감천문화마을", "21362956",
			"해운대해수욕장", "7913306",
			"범어사", "26884008",
			"광안리해수욕장", "8202423");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlaceSearchService placeSearchService;

	@BeforeEach
	void reapplyCuratedLandmarkMigration() {
		clearRemovableTestLeftovers();
		this.jdbcTemplate.execute(curatedLandmarkMigrationSql());
	}

	/**
	 * 같은 이름을 좌표 없이 넣고 간 다른 시험의 흔적을 걷어낸다. 남아 있으면 마이그레이션의
	 * 이름 가드가 삽입을 건너뛴다. 어떤 적재도 {@code source_type} 을 비우지 않으므로
	 * 비어 있는 행은 시험이 남긴 것이다. 남의 일정이 그 행을 가리키고 있으면 외래키가
	 * 삭제를 막는데, 그건 정상이라 그대로 넘어간다.
	 */
	private void clearRemovableTestLeftovers() {
		CORE_LANDMARKS.keySet().forEach(nameKo -> {
			try {
				this.jdbcTemplate.update("""
						DELETE FROM place
						WHERE source_type IS NULL
						  AND LOWER(REPLACE(name_ko, ' ', '')) = LOWER(REPLACE(?, ' ', ''))
						""", nameKo);
			}
			catch (DataIntegrityViolationException referencedByAnotherTest) {
				// 위 javadoc 참고 — 지울 수 없는 것은 그대로 둔다.
			}
		});
	}

	/**
	 * 마이그레이션 번호는 머지 순서 때문에 바뀌므로 파일 이름에 번호를 박지 않고 패턴으로 찾는다.
	 */
	private String curatedLandmarkMigrationSql() {
		try {
			Resource[] found = new PathMatchingResourcePatternResolver()
					.getResources("classpath*:db/migration/V*__curated_core_busan_landmarks.sql");
			assertThat(found).as("랜드마크 마이그레이션 파일이 정확히 하나 있어야 한다").hasSize(1);
			return found[0].getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("랜드마크 마이그레이션 파일을 읽지 못했다", ex);
		}
	}

	/**
	 * "한 벌만 있다" 는 이 DB 에서 셀 수 없다 — 다른 통합 검사들이 같은 이름을 각자 넣고
	 * 남기기 때문이다. 그래서 있는지만 보고, 중복을 안 만든다는 성질은
	 * {@link #reapplyingTheMigrationDoesNotDuplicatePlaces} 가 전후 변화량으로 확인한다.
	 */
	@Test
	@DisplayName("부산 대표 명소 네 곳이 이름으로 찾아진다")
	void everyCoreLandmarkIsFindableByName() {
		CORE_LANDMARKS.keySet().forEach(nameKo -> assertThat(rowsNamed(nameKo))
				.as("%s 라는 이름의 장소가 하나도 없다 — 마이그레이션이 안 돌았다는 뜻이다", nameKo)
				.isNotEmpty());
	}

	@Test
	@DisplayName("이 마이그레이션이 넣은 행은 출처와 좌표를 갖는다")
	void rowsInsertedByThisMigrationAreTraceable() {
		List<Map<String, Object>> inserted = this.jdbcTemplate.queryForList("""
				SELECT name_ko, source_id, lat, lng, dataset_version
				FROM place
				WHERE dataset_version = 'KAKAO_LOCAL_2026-09-15'
				""");

		assertThat(inserted)
				.as("네 곳 중 하나도 못 넣었다면 SQL 이 안 돈 것이다 — 이름이 이미 잡혀 있어 "
						+ "건너뛰는 경우가 있어도 전부 건너뛰지는 않는다")
				.isNotEmpty();

		assertThat(inserted).allSatisfy(row -> {
			assertThat(row.get("source_id")).as("출처 ID").isNotNull();
			// 좌표가 없으면 지도에도 못 찍고 일정 구간도 못 잰다 — 이 칸이 이 적재의 목적이다.
			assertThat(row.get("lat")).as("%s 의 위도", row.get("name_ko")).isNotNull();
			assertThat(row.get("lng")).as("%s 의 경도", row.get("name_ko")).isNotNull();
		});

		assertThat(inserted).extracting(row -> row.get("source_id"))
				.as("넣은 행의 출처 ID 는 전부 이 마이그레이션이 적은 넷 중 하나다")
				.isSubsetOf(CORE_LANDMARKS.values().toArray());
	}

	@Test
	@DisplayName("같은 마이그레이션을 두 번 돌려도 같은 장소가 두 벌 생기지 않는다")
	void reapplyingTheMigrationDoesNotDuplicatePlaces() {
		Map<String, Integer> before = countsByName();

		this.jdbcTemplate.execute(curatedLandmarkMigrationSql());

		assertThat(countsByName())
				.as("가드(WHERE NOT EXISTS)가 중복을 막아야 한다 — 바다·자연 장소를 넣는 뒤 "
						+ "마이그레이션도 같은 두 곳을 넣으므로, 이 성질이 깨지면 그쪽에서 중복이 생긴다")
				.isEqualTo(before);
	}

	@Test
	@DisplayName("감천문화마을 정확 검색은 유사 상호가 아니라 대표 명소를 첫 결과로 돌려준다")
	void exactGamcheonSearchReturnsTheLandmarkFirst() {
		PlaceSummaryResponse first = this.placeSearchService.search("감천문화마을", null, 8, null)
				.items().get(0);

		assertThat(first.nameKo()).isEqualTo("감천문화마을");
		assertThat(first.placeId()).isNotNull();
		assertThat(first.lat()).isCloseTo(35.09740872250286, org.assertj.core.data.Offset.offset(0.000001));
		assertThat(first.lng()).isCloseTo(129.01056080474402, org.assertj.core.data.Offset.offset(0.000001));
	}

	private List<Map<String, Object>> rowsNamed(String nameKo) {
		return this.jdbcTemplate.queryForList("""
				SELECT place_id, name_ko, lat, lng, source_type
				FROM place
				WHERE LOWER(REPLACE(name_ko, ' ', '')) = LOWER(REPLACE(?, ' ', ''))
				""", nameKo);
	}

	private Map<String, Integer> countsByName() {
		return CORE_LANDMARKS.keySet().stream().collect(
				java.util.stream.Collectors.toMap(nameKo -> nameKo, nameKo -> rowsNamed(nameKo).size()));
	}
}
