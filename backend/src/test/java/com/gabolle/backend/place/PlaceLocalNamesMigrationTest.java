package com.gabolle.backend.place;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 장소의 일본어·중국어 이름을 넣는 마이그레이션(S15P21E201-1859)을 본다.
 *
 * <p>{@link KtoMuslimFriendlyRestaurantsMigrationTest} 와 같은 이유로 검사 직전에 그 SQL 을 다시 돌린다 — 통합 시험들이 한 DB 를
 * 같이 쓰고 그중 하나가 {@code TRUNCATE place CASCADE} 를 돌려서, 시험 차례에 장소가 없을 수 있다. 그래서 이 시험이 장소를
 * 먼저 넣고 마이그레이션을 돌린다.
 */
class PlaceLocalNamesMigrationTest extends PlacePostgresIntegrationTest {

	// 관광공사 좌표(국제시장 35.1016422, 129.0285793)에서 약 100m 떨어진 우리 좌표 — 입구·중심으로 갈리는 흔한 차이.
	private static final UUID GUKJE = UUID.fromString("0b7f1e1e-1859-4000-8000-000000000001");

	// 같은 이름이지만 2km 떨어진 곳 — 이름만 같다고 잇지 않는다.
	private static final UUID FAR_GUKJE = UUID.fromString("0b7f1e1e-1859-4000-8000-000000000002");

	// 관광공사 이름에 괄호·공백이 섞인 곳.
	private static final UUID YONGGUNGSA = UUID.fromString("0b7f1e1e-1859-4000-8000-000000000003");

	// 사람이 이미 고친 일본어 이름 — 덮지 않는다.
	private static final UUID HAND_FIXED = UUID.fromString("0b7f1e1e-1859-4000-8000-000000000004");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlaceRepository placeRepository;

	@BeforeEach
	void seedPlacesAndReapply() {
		this.jdbcTemplate.update("DELETE FROM place WHERE place_id IN (?, ?, ?, ?)", GUKJE, FAR_GUKJE, YONGGUNGSA, HAND_FIXED);
		insert(GUKJE, "국제시장", 35.1022, 129.0293);
		insert(FAR_GUKJE, "국제시장", 35.1196, 129.0285);
		// 관광공사 「해동 용궁사(부산)」 — 괄호 속 말과 공백을 빼면 「해동용궁사」다.
		insert(YONGGUNGSA, "해동용궁사", 35.1880, 129.2230);
		insert(HAND_FIXED, "부산타워", 35.1010, 129.0325);
		this.jdbcTemplate.update("UPDATE place SET name_ja = '手で直した名前' WHERE place_id = ?", HAND_FIXED);
		this.jdbcTemplate.execute(migrationSql());
	}

	private void insert(UUID id, String nameKo, double lat, double lng) {
		this.jdbcTemplate.update("INSERT INTO place (place_id, name_ko, lat, lng, created_at) VALUES (?, ?, ?, ?, now())",
				id, nameKo, lat, lng);
	}

	private String migrationSql() {
		try {
			Resource[] found = new PathMatchingResourcePatternResolver()
					.getResources("classpath*:db/migration/V*__place_local_names.sql");
			assertThat(found).as("장소 다국어 이름 마이그레이션 파일이 정확히 하나 있어야 한다").hasSize(1);
			return found[0].getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("장소 다국어 이름 마이그레이션 파일을 읽지 못했다", ex);
		}
	}

	private Map<String, String> localNames(UUID id) {
		Place place = this.placeRepository.findById(id).orElseThrow();
		return place.localNames();
	}

	@Test
	@DisplayName("🔴 이름이 같고 500m 안이면 관광공사 이름 셋이 붙는다")
	void matchesByNameAndDistance() {
		assertThat(localNames(GUKJE)).containsExactly(
				Map.entry("ja", "国際市場"), Map.entry("zh-Hans", "国际市场"), Map.entry("zh-Hant", "國際市場"));
	}

	@Test
	@DisplayName("🔴 이름이 같아도 멀면 잇지 않는다")
	void farPlaceStaysEmpty() {
		assertThat(localNames(FAR_GUKJE)).isEmpty();
	}

	@Test
	@DisplayName("괄호 속 말·공백을 빼고 비교한다 — 「해동 용궁사(부산)」 = 「해동용궁사」")
	void ignoresParenthesesAndSpaces() {
		assertThat(localNames(YONGGUNGSA)).containsKeys("ja", "zh-Hans", "zh-Hant");
	}

	@Test
	@DisplayName("이미 있는 이름은 덮지 않는다")
	void keepsHandFixedName() {
		assertThat(localNames(HAND_FIXED)).containsEntry("ja", "手で直した名前");
	}
}
