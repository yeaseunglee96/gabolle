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
 * 위키데이터로 장소의 일본어·중국어 이름을 더 채우는 마이그레이션(S15P21E201-1948)을 본다.
 * {@link PlaceLocalNamesMigrationTest} 와 같은 이유로 장소를 먼저 넣고 그 SQL 을 다시 돌린다.
 */
class PlaceWikidataNamesMigrationTest extends PlacePostgresIntegrationTest {

	// 위키데이터 금정산(Q482647, 35.2831·129.0556)에서 약 700m — 산은 가운데 좌표라 우리 좌표(들머리)와 멀다
	private static final UUID GEUMJEONGSAN = UUID.fromString("0b7f1e1e-1948-4000-8000-000000000001");

	// 관광공사 번체 이름이 이미 있는 광안리해수욕장 — 덮지 않고, 빈 일본어만 채운다
	private static final UUID GWANGALLI = UUID.fromString("0b7f1e1e-1948-4000-8000-000000000002");

	// 같은 이름이지만 5km 떨어진 곳 — 잇지 않는다
	private static final UUID FAR_GEUMJEONGSAN = UUID.fromString("0b7f1e1e-1948-4000-8000-000000000003");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlaceRepository placeRepository;

	@BeforeEach
	void seedPlacesAndReapply() {
		this.jdbcTemplate.update("DELETE FROM place WHERE place_id IN (?, ?, ?)", GEUMJEONGSAN, GWANGALLI, FAR_GEUMJEONGSAN);
		insert(GEUMJEONGSAN, "금정산", 35.2768, 129.0560);
		insert(GWANGALLI, "광안리해수욕장", 35.1532, 129.1186);
		insert(FAR_GEUMJEONGSAN, "금정산", 35.2300, 129.0560);
		this.jdbcTemplate.update("UPDATE place SET name_zh_hant = '廣安里海水浴場' WHERE place_id = ?", GWANGALLI);
		this.jdbcTemplate.execute(migrationSql());
	}

	private void insert(UUID id, String nameKo, double lat, double lng) {
		this.jdbcTemplate.update("INSERT INTO place (place_id, name_ko, lat, lng, created_at) VALUES (?, ?, ?, ?, now())",
				id, nameKo, lat, lng);
	}

	private String migrationSql() {
		try {
			Resource[] found = new PathMatchingResourcePatternResolver()
					.getResources("classpath*:db/migration/V*__place_local_names_wikidata.sql");
			assertThat(found).as("위키데이터 이름 마이그레이션 파일이 정확히 하나 있어야 한다").hasSize(1);
			return found[0].getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("위키데이터 이름 마이그레이션 파일을 읽지 못했다", ex);
		}
	}

	private Map<String, String> localNames(UUID id) {
		Place place = this.placeRepository.findById(id).orElseThrow();
		return place.localNames();
	}

	@Test
	@DisplayName("🔴 관광공사에 없던 금정산 — 1km 안이면 위키데이터 이름이 붙는다")
	void fillsMissingNames() {
		assertThat(localNames(GEUMJEONGSAN)).containsEntry("ja", "金井山");
	}

	@Test
	@DisplayName("🔴 관광공사 번역은 덮지 않는다 — 빈 칸만 채운다")
	void keepsKtoName() {
		Map<String, String> names = localNames(GWANGALLI);
		assertThat(names).containsEntry("zh-Hant", "廣安里海水浴場");
		assertThat(names).containsKey("ja");
	}

	@Test
	@DisplayName("이름이 같아도 멀면 잇지 않는다")
	void farPlaceStaysEmpty() {
		assertThat(localNames(FAR_GEUMJEONGSAN)).isEmpty();
	}
}
