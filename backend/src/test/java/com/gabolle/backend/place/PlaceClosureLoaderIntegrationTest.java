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
 * 폐업 여부를 장소에 적는다. 진짜 DB 를 쓰는 이유는 이 적재가 이미 있는 장소를 찾아 고치는
 * 일이라서다 — 상가업소번호로 만든 아이디가 실제로 그 장소를 가리키지 않으면 아무것도 안
 * 고치고 조용히 성공한다.
 */
class PlaceClosureLoaderIntegrationTest extends PlacePostgresIntegrationTest {

	private static final String DATASET = "sbiz-test-202606";

	@Autowired
	private SbizPlaceLoader sbizLoader;

	@Autowired
	private PlaceClosureLoader closureLoader;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/** 이 시험이 쓰는 가게. 상가업소번호에서 나오는 아이디가 정해져 있어 지울 것도 정해진다. */
	private static final List<String> STORE_IDS = List.of("STORE-A", "STORE-B");

	/**
	 * 내가 넣은 것만 지운다. 여러 시험이 같은 진짜 DB 를 쓰고 다른 시험이 만든 일정이 장소를
	 * 가리키므로, 표를 통째로 비우면 {@code fk_itinerary_item_place} 에 걸린다. 그렇다고 남의
	 * 일정을 지우면 고장을 옆 시험으로 옮기는 것이다.
	 *
	 * <p>이 파일의 단언은 {@code STORE-A}·{@code STORE-B} 두 곳만 보므로 표가 비어 있을 필요가
	 * 없다.
	 */
	@BeforeEach
	void seedPlaces() {
		for (String storeId : STORE_IDS) {
			UUID placeId = SbizPlaceLoader.placeIdOf(storeId);
			this.jdbcTemplate.update("DELETE FROM place_feature WHERE place_id = ?", placeId);
			this.jdbcTemplate.update("DELETE FROM place WHERE place_id = ?", placeId);
		}
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

	/** 이음은 이름과 자리로 맞추는 것이라 틀릴 수 있다. 되돌리는 쪽이 없으면 영영 사라진다. */
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
	 * 장소 적재를 안 돌렸으면 붙을 자리가 없다. 실패하지 않고 그 수를 세며, 부르는 쪽이 그 수를
	 * 보고 순서가 뒤집혔는지 판단한다.
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
