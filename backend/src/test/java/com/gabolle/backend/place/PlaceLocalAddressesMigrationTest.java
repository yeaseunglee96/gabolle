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
 * 장소 주소의 일본어·중국어를 넣는 마이그레이션(S15P21E201-1876)을 본다.
 *
 * <p>{@link PlaceLocalNamesMigrationTest} 와 같은 이유로 장소를 먼저 넣고 그 SQL 을 다시 돌린다 — 통합 시험들이 한 DB 를
 * 같이 쓰고 그중 하나가 {@code TRUNCATE place CASCADE} 를 돌려서, 시험 차례에 장소가 없을 수 있다.
 */
class PlaceLocalAddressesMigrationTest extends PlacePostgresIntegrationTest {

	// 관광공사 좌표(국제시장 35.1016422, 129.0285793)에서 약 100m — 이름·거리·구 모두 맞는다.
	private static final UUID GUKJE = UUID.fromString("0b7f1e1e-1876-4000-8000-000000000001");

	// 이름·거리는 맞지만 우리 주소의 구가 다르다 — 구 경계 근처의 다른 장소일 수 있어 잇지 않는다.
	private static final UUID OTHER_GU = UUID.fromString("0b7f1e1e-1876-4000-8000-000000000002");

	// 관광공사는 서구(송도 구름산책로)인데 우리 주소는 강서구 — 「서구」가 「강서구」 안에 걸리면 안 된다.
	private static final UUID GANGSEO_TRAP = UUID.fromString("0b7f1e1e-1876-4000-8000-000000000003");

	// 사람이 이미 고친 일본어 주소 — 덮지 않는다.
	private static final UUID HAND_FIXED = UUID.fromString("0b7f1e1e-1876-4000-8000-000000000004");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlaceRepository placeRepository;

	@BeforeEach
	void seedPlacesAndReapply() {
		this.jdbcTemplate.update("DELETE FROM place WHERE place_id IN (?, ?, ?, ?)", GUKJE, OTHER_GU, GANGSEO_TRAP, HAND_FIXED);
		insert(GUKJE, "국제시장", "부산광역시 중구 신창동4가", 35.1022, 129.0293);
		insert(OTHER_GU, "국제시장", "부산광역시 서구 충무대로 1", 35.1010, 129.0280);
		insert(GANGSEO_TRAP, "송도 구름산책로", "부산광역시 강서구 가상로 1", 35.0755, 129.0226);
		insert(HAND_FIXED, "용두산공원", "부산광역시 중구 용두산길 37-55", 35.1005, 129.0327);
		this.jdbcTemplate.update("UPDATE place SET address_ja = '手で直した住所' WHERE place_id = ?", HAND_FIXED);
		this.jdbcTemplate.execute(migrationSql());
	}

	private void insert(UUID id, String nameKo, String address, double lat, double lng) {
		this.jdbcTemplate.update(
				"INSERT INTO place (place_id, name_ko, address, lat, lng, created_at) VALUES (?, ?, ?, ?, ?, now())",
				id, nameKo, address, lat, lng);
	}

	private String migrationSql() {
		try {
			Resource[] found = new PathMatchingResourcePatternResolver()
					.getResources("classpath*:db/migration/V*__place_local_addresses.sql");
			assertThat(found).as("장소 다국어 주소 마이그레이션 파일이 정확히 하나 있어야 한다").hasSize(1);
			return found[0].getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("장소 다국어 주소 마이그레이션 파일을 읽지 못했다", ex);
		}
	}

	private Map<String, String> localAddresses(UUID id) {
		Place place = this.placeRepository.findById(id).orElseThrow();
		return place.localAddresses();
	}

	@Test
	@DisplayName("🔴 이름·거리·구가 맞으면 관광공사 주소 셋이 붙는다 — 시·구 이름은 언어마다 한 표기로")
	void matchesByNameDistanceAndGu() {
		assertThat(localAddresses(GUKJE)).containsExactly(
				Map.entry("ja", "釜山広域市 中区 シンチャンロ4ガ一帯"),
				Map.entry("zh-Hans", "釜山广域市中区新昌洞4街一带"),
				Map.entry("zh-Hant", "釜山廣域市中區新昌路4街一帶"));
	}

	@Test
	@DisplayName("🔴 구가 다르면 잇지 않는다 — 이름과 거리만 맞는 다른 장소에 남의 주소가 붙는다")
	void otherGuStaysEmpty() {
		assertThat(localAddresses(OTHER_GU)).isEmpty();
	}

	@Test
	@DisplayName("🔴 「서구」는 「강서구」에 걸리지 않는다")
	void seoguDoesNotMatchGangseogu() {
		assertThat(localAddresses(GANGSEO_TRAP)).isEmpty();
	}

	@Test
	@DisplayName("이미 있는 주소는 덮지 않는다")
	void keepsHandFixedAddress() {
		assertThat(localAddresses(HAND_FIXED)).containsEntry("ja", "手で直した住所").containsKey("zh-Hans");
	}
}
