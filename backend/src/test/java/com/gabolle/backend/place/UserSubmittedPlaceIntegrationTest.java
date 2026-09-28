package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.domain.CurationStatus;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.service.UserSubmittedPlaceService;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

/**
 * 사용자 출처 장소가 <b>진짜 PostgreSQL 에서</b> 어떻게 다뤄지는가 — S15P21E201-1426.
 *
 * <p>단위 시험은 저장소를 흉내 내므로 여기서 보는 것 셋을 못 본다. 셋 다 DB 쪽 성질이다.
 *
 * <ul>
 * <li>{@code uq_place_source} 가 같은 원천 기록으로 두 행이 생기는 것을 실제로 막는가</li>
 * <li>{@code curation_status} 기본값이 {@code CURATED} 라 이관 전 행이 추천에 그대로 남는가</li>
 * <li>찾아 주는 조회가 {@code USER_SUBMITTED} 를 <b>빼는데</b>, id 로 읽는 것은 <b>되는가</b> —
 *     이 둘이 어긋나면 자기가 고른 장소가 글에서 사라진다</li>
 * </ul>
 */
class UserSubmittedPlaceIntegrationTest extends PlacePostgresIntegrationTest {

	@Autowired
	private PlaceRepository places;

	@Autowired
	private UserSubmittedPlaceService service;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private PlaceFixture fixture;

	private String externalId;

	@BeforeEach
	void setUp() {
		this.fixture = new PlaceFixture(this.jdbcTemplate);
		this.externalId = "kakao-" + UUID.randomUUID();
	}

	@AfterEach
	void tearDown() {
		this.jdbcTemplate.update("DELETE FROM place WHERE source_type = 'KAKAO_LOCAL' AND source_id = ?",
				this.externalId);
		this.fixture.cleanUp();
	}

	private UserSubmittedPlaceService.Snapshot snapshot(String name) {
		return new UserSubmittedPlaceService.Snapshot("KAKAO_LOCAL", this.externalId, name,
				"부산 해운대구 우동", 35.1585, 129.1598, "SEA_BEACH");
	}

	@Test
	@DisplayName("🔴 같은 (출처, 식별자)로 두 번 부르면 행은 하나다")
	void theSameSourcePairYieldsOneRow() {
		Place first = this.service.findOrCreate(snapshot("사용자가고른곳"));
		Place second = this.service.findOrCreate(snapshot("사용자가고른곳"));

		assertThat(second.getPlaceId()).isEqualTo(first.getPlaceId());
		Long rows = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM place WHERE source_type = 'KAKAO_LOCAL' AND source_id = ?",
				Long.class, this.externalId);
		assertThat(rows).isEqualTo(1L);
	}

	@Test
	@DisplayName("🔴 uq_place_source 가 DB 에서 막는다 — 도메인을 우회해 들어오는 길에서 막는 것은 이것뿐이다")
	void theUniqueIndexBlocksASecondRow() {
		this.service.findOrCreate(snapshot("사용자가고른곳"));

		assertThatThrownBy(() -> this.jdbcTemplate.update("""
				INSERT INTO place (place_id, name_ko, category, address, lat, lng, created_at,
				                   source_type, source_id, collected_at)
				VALUES (?, ?, 'SEA_BEACH', '부산', 35.1, 129.1, now(), 'KAKAO_LOCAL', ?, now())
				""", UUID.randomUUID(), "몰래들어온같은곳", this.externalId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("🔴 사용자 출처는 검색·주변에서 «안 나오지만» id 로는 읽힌다")
	void aUserSubmittedPlaceIsHiddenFromDiscoveryButReadableById() {
		Place submitted = this.service.findOrCreate(snapshot("검색에서안보여야하는곳"));

		assertThat(this.places.searchByName("%검색에서안보여야하는곳%", Limit.of(50)))
				.as("검증 안 된 장소가 검색에 뜨면 사용자는 그것을 우리 장소로 읽는다")
				.noneMatch((p) -> p.getPlaceId().equals(submitted.getPlaceId()));
		assertThat(this.places.findWithinBoundingBox(35.0, 35.3, 129.0, 129.3, Limit.of(500)))
				.as("추천 후보를 뽑는 길이 바로 이것이다")
				.noneMatch((p) -> p.getPlaceId().equals(submitted.getPlaceId()));

		assertThat(this.places.findById(submitted.getPlaceId()))
				.as("여기서까지 걸러 버리면 자기가 고른 장소가 글에서 사라진다")
				.isPresent();
	}

	@Test
	@DisplayName("적재기가 넣은 장소는 CURATED 다 — 기본값이 반대면 이관이 도는 순간 추천이 빈손이 된다")
	void anImportedPlaceDefaultsToCurated() {
		UUID loaded = this.fixture.insertPlace("적재된곳", null, "FOOD", 35.12, 129.05);

		assertThat(this.places.findById(loaded)).get()
				.extracting(Place::getCurationStatus).isEqualTo(CurationStatus.CURATED);
		assertThat(this.places.findWithinBoundingBox(35.0, 35.3, 129.0, 129.3, Limit.of(500)))
				.anyMatch((p) -> p.getPlaceId().equals(loaded));
	}

	@Test
	@DisplayName("🔴 장소를 둘 넣어도 픽스처가 uq_place_source 에 안 걸린다")
	void theFixtureCanInsertTwoPlaces() {
		UUID first = this.fixture.insertPlace("첫곳", null, "FOOD", 35.12, 129.05);
		UUID second = this.fixture.insertPlace("둘째곳", null, "FOOD", 35.13, 129.06);

		assertThat(first).isNotEqualTo(second);
		assertThat(this.places.findById(second)).isPresent();
	}
}
