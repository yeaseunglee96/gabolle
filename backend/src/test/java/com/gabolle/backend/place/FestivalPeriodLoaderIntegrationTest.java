package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.loader.FestivalPeriodLoader;
import com.gabolle.backend.place.loader.FestivalPeriodRow;
import com.gabolle.backend.place.loader.TourApiPlaceLoader;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

/**
 * 축제 회차를 <b>JPA 로</b> 저장한다 — S15P21E201-1374.
 *
 * <h2>🔴 이 시험이 없어서 적재가 깨진 채로 나갔다</h2>
 *
 * {@code place_event_period} 는 운영에서 <b>0행</b>이었고 {@code GET /api/v1/festivals} 가 어떤
 * 기간으로도 빈 목록이었다(2026-09-21 실측, jinmiri 제보). 질의는 멀쩡했다 — 넣는 쪽이 매번
 * 통째로 굴러떨어지고 있었다.
 *
 * <pre>
 *   ERROR: null value in column "created_at" of relation "place_event_period"
 *          violates not-null constraint
 * </pre>
 *
 * <p>표에는 {@code DEFAULT now()} 가 있는데 Hibernate 가 그 칸을 «명시적으로 null 로» 넣어
 * 기본값을 덮었다.
 *
 * <p><b>왜 아무도 못 잡았나.</b> {@code FestivalIntegrationTest} 는 생 SQL 로 행을 넣어 DB
 * 기본값이 채워졌고, {@code FestivalPeriodReaderTest} 는 파일만 읽는다. <b>JPA 로 저장하는 길을
 * 지나는 시험이 하나도 없었다.</b> 그 길을 여기서 지난다 — 읽기가 아니라 «쓰기»를 잰다.
 */
class FestivalPeriodLoaderIntegrationTest extends PlacePostgresIntegrationTest {

	private static final String CONTENT_ID = "1374000";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private FestivalPeriodLoader loader;

	private PlaceFixture fixture;

	@BeforeEach
	void setUp() {
		this.fixture = new PlaceFixture(this.jdbcTemplate);
	}

	@AfterEach
	void tearDown() {
		// 🔴 이 장소는 PlaceFixture 가 아니라 내가 직접 넣었다(아이디를 적재기와 맞춰야 해서).
		//    fixture.cleanUp 은 자기가 넣은 것만 지우므로 여기서 직접 지운다.
		//    place_event_period 은 place 를 ON DELETE CASCADE 로 참조해 함께 사라진다.
		this.jdbcTemplate.update("DELETE FROM place WHERE place_id = ?", TourApiPlaceLoader.placeIdOf(CONTENT_ID));
		this.fixture.cleanUp();
	}

	/**
	 * 적재기가 붙일 수 있도록 장소를 먼저 심는다.
	 *
	 * <p>아이디를 무작위로 만들 수 없다 — 적재기는 {@code contentid} 에서 «계산해» 나오는
	 * 아이디로만 장소를 찾는다. 그래서 {@link PlaceFixture} 대신 직접 넣는다.
	 */
	private UUID seedPlace() {
		UUID placeId = TourApiPlaceLoader.placeIdOf(CONTENT_ID);
		this.jdbcTemplate.update("""
				INSERT INTO place (place_id, name_ko, category, address, lat, lng,
				                   created_at, source_type, source_id, collected_at, observed_at, dataset_version)
				VALUES (?, ?, ?, ?, ?, ?, now(), ?, ?, now(), now(), 'fixture-1374')
				ON CONFLICT (place_id) DO NOTHING
				""", placeId, "축제터", "ATTRACTION", "부산 해운대구", 35.16, 129.16,
				FestivalPeriodLoader.SOURCE_TYPE, CONTENT_ID);
		return placeId;
	}

	@Test
	@DisplayName("🔴 회차가 실제로 저장된다 — 예전에는 created_at 이 null 이라 통째로 터졌다")
	void savesThroughJpa() {
		UUID placeId = seedPlace();

		FestivalPeriodLoader.Result result = this.loader.saveChunk(
				List.of(new FestivalPeriodRow(CONTENT_ID, "빛축제", LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 22))),
				OffsetDateTime.now());

		assertThat(result.inserted()).isEqualTo(1);
		Integer rows = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM place_event_period WHERE place_id = ?", Integer.class, placeId);
		assertThat(rows).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 created_at 이 비어 있지 않다 — 이 칸 하나가 적재 전체를 막고 있었다")
	void stampsCreatedAt() {
		UUID placeId = seedPlace();

		this.loader.saveChunk(
				List.of(new FestivalPeriodRow(CONTENT_ID, "빛축제", LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 22))),
				OffsetDateTime.now());

		OffsetDateTime createdAt = this.jdbcTemplate.queryForObject(
				"SELECT created_at FROM place_event_period WHERE place_id = ?", OffsetDateTime.class, placeId);
		assertThat(createdAt).isNotNull();
	}

	@Test
	@DisplayName("두 번 넣어도 한 행이다 — 다시 돌려도 안전하다")
	void loadingTwiceKeepsOneRow() {
		UUID placeId = seedPlace();
		List<FestivalPeriodRow> rows = List.of(
				new FestivalPeriodRow(CONTENT_ID, "빛축제", LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 22)));

		this.loader.saveChunk(rows, OffsetDateTime.now());
		FestivalPeriodLoader.Result second = this.loader.saveChunk(rows, OffsetDateTime.now());

		assertThat(second.inserted()).isZero();
		Integer count = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM place_event_period WHERE place_id = ?", Integer.class, placeId);
		assertThat(count).isEqualTo(1);
	}

	@Test
	@DisplayName("붙일 장소가 없으면 조용히 세고 넘어간다 — 적재 순서가 뒤집혔는지 부르는 쪽이 판단한다")
	void countsRowsWithNoPlace() {
		FestivalPeriodLoader.Result result = this.loader.saveChunk(
				List.of(new FestivalPeriodRow("9999999", "없는 축제", LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 22))),
				OffsetDateTime.now());

		assertThat(result.noPlace()).isEqualTo(1);
		assertThat(result.inserted()).isZero();
	}
}
