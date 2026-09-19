package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.loader.PlaceClosureLoader;
import com.gabolle.backend.place.loader.PlaceClosureRow;
import com.gabolle.backend.place.loader.SbizPlaceLoader;
import com.gabolle.backend.place.loader.SbizRow;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

/**
 * 폐업 여부를 장소에 적는다 — S15P21E201-1341.
 *
 * <h2>🔴 왜 진짜 DB 인가</h2>
 * 이 적재가 하는 일은 <b>이미 있는 장소를 찾아 고치는 것</b>이다. 가짜 저장소로는 「상가업소번호로
 * 만든 아이디가 실제로 그 장소를 가리키는가」가 검증되지 않는다 — 그게 틀리면 아무것도 안 고치고
 * <b>조용히 성공</b>한다.
 *
 * <p>실측(2026-09-19): 이어진 36,458곳 중 <b>3,530곳(9.7%)이 이미 폐업</b>이었다.
 */
class PlaceClosureLoaderIntegrationTest extends PlacePostgresIntegrationTest {

	private static final String DATASET = "sbiz-test-202606";

	@Autowired
	private SbizPlaceLoader sbizLoader;

	@Autowired
	private PlaceClosureLoader closureLoader;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void seedPlaces() {
		this.jdbcTemplate.update("DELETE FROM place_feature");
		this.jdbcTemplate.update("DELETE FROM place");
		this.sbizLoader.saveChunk(List.of(
				new SbizRow("STORE-A", "가게가", null, "한식", "부산 해운대구 1", 35.16, 129.16),
				new SbizRow("STORE-B", "가게나", null, "한식", "부산 해운대구 2", 35.17, 129.17)),
				DATASET, OffsetDateTime.now());
	}

	private LocalDate closedOn(String storeId) {
		UUID placeId = SbizPlaceLoader.placeIdOf(storeId);
		return this.jdbcTemplate.queryForObject(
				"SELECT closed_on FROM place WHERE place_id = ?", LocalDate.class, placeId);
	}

	@Test
	@DisplayName("🔴 티켓 완료 기준 — 폐업한 가게에 날짜가 적힌다")
	void marksClosedPlaces() {
		PlaceClosureLoader.Result result = this.closureLoader.saveChunk(List.of(
				new PlaceClosureRow("STORE-A", LocalDate.of(2026, 3, 31)),
				new PlaceClosureRow("STORE-B", null)));

		assertThat(closedOn("STORE-A")).isEqualTo(LocalDate.of(2026, 3, 31));
		assertThat(closedOn("STORE-B")).isNull();
		assertThat(result.marked()).isEqualTo(1);
	}

	/**
	 * 🔴 이음은 <b>이름과 자리</b>로 맞추는 것이라 틀릴 수 있다. 되돌리는 쪽이 없으면 잘못
	 * 이어진 가게 하나가 <b>영영 추천에서 사라진다.</b>
	 */
	@Test
	@DisplayName("🔴 다시 열었으면 닫힘을 지운다 — 한번 적은 폐업이 영영 안 지워지면 안 된다")
	void clearsClosureWhenTheShopIsOpenAgain() {
		this.closureLoader.saveChunk(List.of(new PlaceClosureRow("STORE-A", LocalDate.of(2026, 3, 31))));

		PlaceClosureLoader.Result result = this.closureLoader.saveChunk(
				List.of(new PlaceClosureRow("STORE-A", null)));

		assertThat(closedOn("STORE-A")).isNull();
		assertThat(result.cleared()).isEqualTo(1);
	}

	@Test
	@DisplayName("같은 것을 두 번 넣어도 안 바뀐다 — 두 번째는 「그대로」로 센다")
	void reloadingTheSameValueChangesNothing() {
		List<PlaceClosureRow> rows = List.of(new PlaceClosureRow("STORE-A", LocalDate.of(2026, 3, 31)));
		this.closureLoader.saveChunk(rows);

		PlaceClosureLoader.Result second = this.closureLoader.saveChunk(rows);

		assertThat(second.marked()).isZero();
		assertThat(second.unchanged()).isEqualTo(1);
		assertThat(closedOn("STORE-A")).isEqualTo(LocalDate.of(2026, 3, 31));
	}

	/**
	 * 🔴 장소 적재를 안 돌렸으면 붙을 자리가 없다. 여기서 <b>실패하지 않는다</b> — 조용히 넘어가고
	 * 그 수를 센다. 부르는 쪽이 그 수를 보고 순서가 뒤집혔는지 판단한다.
	 */
	@Test
	@DisplayName("🔴 붙일 장소가 없으면 조용히 넘기고 그 수를 센다")
	void countsRowsWithNoPlace() {
		PlaceClosureLoader.Result result = this.closureLoader.saveChunk(
				List.of(new PlaceClosureRow("없는가게", LocalDate.of(2026, 3, 31))));

		assertThat(result.noPlace()).isEqualTo(1);
		assertThat(result.marked()).isZero();
	}
}
