package com.gabolle.backend.place;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.domain.CurationStatus;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회사 이름 명소를 「숨김」으로 — S15P21E201-1636.
 *
 * <p>시험 DB 에는 운영의 그 6곳이 없어 Flyway 가 돈 것만으로는 0건을 바꾼다. 목록의 번호 하나로 장소를 심고 <b>같은 파일의
 * SQL 을 읽어</b> 다시 돌린다 — 파일이 제약을 지우고 다시 거므로 두 번 돌려도 된다.
 */
class HiddenPlaceMigrationTest extends PlacePostgresIntegrationTest {

	private static final Path MIGRATION =
			Path.of("src/main/resources/db/migration/V20260925070000__hide_company_named_sights.sql");

	/** 목록에 있는 번호 — 운영의 「주식회사뷰티홀릭」. */
	private static final UUID BEAUTY_HOLIC = UUID.fromString("c3489c06-4f6e-3dd0-89a3-06f3c7c4dfc3");

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlaceRepository placeRepository;

	private final UUID other = UUID.randomUUID();

	@AfterEach
	void cleanUp() {
		for (UUID id : new UUID[] { BEAUTY_HOLIC, this.other }) {
			this.jdbc.update("DELETE FROM place_feature WHERE place_id = ?", id);
			this.jdbc.update("DELETE FROM place WHERE place_id = ?", id);
		}
	}

	@Test
	@DisplayName("🔴 목록의 곳은 「숨김」이 되어 이름 검색에서 빠지고, 번호로는 그대로 열린다")
	void listedPlaceIsHiddenButStillOpensById() throws Exception {
		place(BEAUTY_HOLIC, "숨김시험 주식회사뷰티홀릭");
		place(this.other, "숨김시험 광안리 카페");

		this.jdbc.execute(Files.readString(MIGRATION));

		Place hidden = this.placeRepository.findById(BEAUTY_HOLIC).orElseThrow();
		assertThat(hidden.getCurationStatus()).isEqualTo(CurationStatus.HIDDEN);
		assertThat(this.placeRepository.searchByName("%숨김시험%", Limit.of(10)))
				.extracting(Place::getPlaceId)
				.containsExactly(this.other);
	}

	@Test
	@DisplayName("되돌리기는 한 줄 — CURATED 로 돌리면 다시 찾힌다")
	void revertingIsOneUpdate() throws Exception {
		place(BEAUTY_HOLIC, "숨김시험 주식회사뷰티홀릭");
		this.jdbc.execute(Files.readString(MIGRATION));

		this.jdbc.update("UPDATE place SET curation_status = 'CURATED' WHERE place_id = ? AND curation_status = 'HIDDEN'",
				BEAUTY_HOLIC);

		assertThat(this.placeRepository.searchByName("%숨김시험%", Limit.of(10)))
				.extracting(Place::getPlaceId)
				.containsExactly(BEAUTY_HOLIC);
	}

	private void place(UUID id, String name) {
		this.jdbc.update("""
				INSERT INTO place (place_id, name_ko, category, lat, lng, source_type, source_id, created_at)
				VALUES (?, ?, 'CULTURE_TEMPLE', 35.153, 129.118, 'TEST', ?, now())
				""", id, name, "t-1636-" + id);
	}
}
