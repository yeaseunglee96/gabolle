package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.loader.AccessibilityLoader;
import com.gabolle.backend.place.loader.BarrierFreeRow;
import com.gabolle.backend.place.loader.FacilityAccessibilityLoader;
import com.gabolle.backend.place.loader.FacilityAccessibilityRow;
import com.gabolle.backend.place.loader.SbizPlaceLoader;
import com.gabolle.backend.place.loader.SbizRow;
import com.gabolle.backend.place.loader.TourApiPlaceLoader;
import com.gabolle.backend.place.loader.TourApiPlaceRow;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

/**
 * 실태조사 접근성 표식이 진짜 DB 에 실제로 붙는가 — S15P21E201-1365.
 *
 * <h2>🔴 왜 진짜 DB 인가</h2>
 * 이 적재가 하는 일은 <b>이미 있는 장소를 찾아 표식을 붙이는 것</b>이다. 장소 id 를 이름
 * UUID 로 <b>계산해서</b> 찾기 때문에, 그 계산이 틀리면 아무 장소도 못 찾는다. 그리고 그때
 * 프로그램은 <b>실패하지 않는다</b> — {@code 붙일 장소가 없어 넘긴 95} 를 찍고 정상 종료한다.
 * 사람 눈에는 "아직 장소 적재를 안 돌렸나" 로 보이고, 실제로는 <b>영원히 한 곳도 안 붙는다.</b>
 *
 * <p>가짜 저장소를 쓰는 단위 테스트로는 그게 안 잡힌다. 가짜는 우리가 준 id 를 그대로
 * 돌려주기 때문에 <b>계산이 틀려도 통과한다.</b> {@code PlaceClosureLoaderIntegrationTest}
 * 가 같은 이유로 진짜 DB 를 쓴다.
 *
 * <h2>이 시험이 넣고 빼는 것</h2>
 * 일회용 PostgreSQL 컨테이너에 <b>가짜 장소 둘</b>을 넣고 시작한다. 운영 자료는 한 줄도
 * 안 쓴다. 뒷정리는 <b>내가 넣은 둘만</b> 지운다 — 표를 통째로 비우면 옆 시험이 만든
 * 일정이 그 장소를 가리키고 있어 외래키에 걸린다.
 */
class FacilityAccessibilityLoaderIntegrationTest extends PlacePostgresIntegrationTest {

	private static final String DATASET = "bf-facility-test-202609";

	/** 관광공사 쪽 가짜 장소. */
	private static final String TOUR_ID = "FAC-TOUR-A";

	/** 상가 쪽 가짜 장소. 상가는 실태조사에서 이름이 맞은 것 대부분이 이쪽이다. */
	private static final String STORE_ID = "FAC-STORE-A";

	@Autowired
	private TourApiPlaceLoader tourLoader;

	@Autowired
	private SbizPlaceLoader sbizLoader;

	@Autowired
	private AccessibilityLoader barrierFreeLoader;

	@Autowired
	private FacilityAccessibilityLoader loader;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void seedPlaces() {
		for (UUID placeId : List.of(TourApiPlaceLoader.placeIdOf(TOUR_ID), SbizPlaceLoader.placeIdOf(STORE_ID))) {
			this.jdbcTemplate.update("DELETE FROM place_feature WHERE place_id = ?", placeId);
			this.jdbcTemplate.update("DELETE FROM place WHERE place_id = ?", placeId);
		}
		this.tourLoader.saveChunk(List.of(new TourApiPlaceRow(TOUR_ID, "12", "A02", "A02060100",
				"시험 전시관", "부산 해운대구 1", 35.16, 129.16, null, null)), DATASET, OffsetDateTime.now());
		this.sbizLoader.saveChunk(List.of(new SbizRow(STORE_ID, "시험 식당", null, "한식",
				"부산 해운대구 2", 35.17, 129.17)), DATASET, OffsetDateTime.now());
	}

	private static FacilityAccessibilityRow tourRow(String evalRaw) {
		return new FacilityAccessibilityRow(FacilityAccessibilityRow.TOURAPI, TOUR_ID,
				"W-TOUR-1", evalRaw, List.of("WHEELCHAIR"));
	}

	private static FacilityAccessibilityRow storeRow(String evalRaw) {
		return new FacilityAccessibilityRow(FacilityAccessibilityRow.SBIZ, STORE_ID,
				"W-STORE-1", evalRaw, List.of("WHEELCHAIR"));
	}

	private List<Map<String, Object>> tagsOf(UUID placeId) {
		return this.jdbcTemplate.queryForList(
				"SELECT feature_key, value, evidence_status, source_type, source_id "
						+ "FROM place_feature WHERE place_id = ? AND feature_type = 'ACCESSIBILITY_TAG'",
				placeId);
	}

	@Test
	@DisplayName("🔴 티켓 완료 기준 — 두 출처 모두 표식이 실제로 그 장소에 붙는다")
	void tagsActuallyLandOnBothKindsOfPlace() {
		FacilityAccessibilityLoader.Result result = this.loader.saveChunk(
				List.of(tourRow("주출입구(문), 주출입구 접근로"), storeRow("주출입구 높이차이 제거")),
				DATASET, OffsetDateTime.now());

		assertThat(result.inserted()).isEqualTo(2);
		assertThat(result.noPlace())
				.as("장소 id 계산이 틀리면 여기가 2가 되고 적재는 조용히 성공한다")
				.isZero();
		assertThat(tagsOf(TourApiPlaceLoader.placeIdOf(TOUR_ID))).hasSize(1);
		assertThat(tagsOf(SbizPlaceLoader.placeIdOf(STORE_ID))).hasSize(1);
	}

	@Test
	@DisplayName("저장된 칸이 약속대로다 — 출처는 BF_FACILITY, 열쇠는 시설 고유번호, 등급은 VERIFIED")
	void storedColumnsMatchTheContract() {
		this.loader.saveChunk(List.of(storeRow("주출입구 접근로")), DATASET, OffsetDateTime.now());

		Map<String, Object> tag = tagsOf(SbizPlaceLoader.placeIdOf(STORE_ID)).get(0);

		assertThat(tag.get("feature_key")).isEqualTo("WHEELCHAIR");
		// value 는 JSONB 칸이라 드라이버가 문자열이 아니라 PGobject 로 돌려준다.
		assertThat(String.valueOf(tag.get("value"))).isEqualTo("true");
		assertThat(tag.get("evidence_status"))
				.as("DB 가 접근성에 ESTIMATED 를 거부한다 — VERIFIED 말고는 저장되지 않는다")
				.isEqualTo("VERIFIED");
		assertThat(tag.get("source_type")).isEqualTo("BF_FACILITY");
		assertThat(tag.get("source_id"))
				.as("표시가 틀렸다는 제보가 오면 이 번호로 조사 기록을 되짚는다")
				.isEqualTo("W-STORE-1");
	}

	@Test
	@DisplayName("🔴 관광공사 자료로 이미 붙어 있으면 안 덮는다 — 실측 13곳 중 11곳이 이 경우다")
	void doesNotOverwriteWhatTheTourDataAlreadySaid() {
		this.barrierFreeLoader.saveChunk(List.of(new BarrierFreeRow(TOUR_ID, List.of("WHEELCHAIR"))),
				"tourapi-test", OffsetDateTime.now());

		FacilityAccessibilityLoader.Result result = this.loader.saveChunk(
				List.of(tourRow("주출입구 접근로")), DATASET, OffsetDateTime.now());

		assertThat(result.inserted()).isZero();
		assertThat(result.alreadyThere()).isEqualTo(1);

		List<Map<String, Object>> tags = tagsOf(TourApiPlaceLoader.placeIdOf(TOUR_ID));
		assertThat(tags).as("같은 표식이 두 벌 생기면 안 된다").hasSize(1);
		assertThat(tags.get(0).get("source_type"))
				.as("먼저 들어간 값이 이긴다 — 관광공사 것이 남아야 한다")
				.isEqualTo("TOURAPI");
	}

	@Test
	@DisplayName("두 번 돌려도 안 바뀐다 — 두 번째는 「이미 있어 넘김」으로 센다")
	void reloadingIsSafe() {
		List<FacilityAccessibilityRow> rows = List.of(storeRow("주출입구 접근로"));
		this.loader.saveChunk(rows, DATASET, OffsetDateTime.now());

		FacilityAccessibilityLoader.Result second = this.loader.saveChunk(rows, DATASET, OffsetDateTime.now());

		assertThat(second.inserted()).isZero();
		assertThat(second.alreadyThere()).isEqualTo(1);
		assertThat(tagsOf(SbizPlaceLoader.placeIdOf(STORE_ID))).hasSize(1);
	}

	@Test
	@DisplayName("장소가 없는 줄은 실패가 아니라 「붙일 장소 없음」으로 센다 — 그 수가 유일한 단서다")
	void missingPlaceIsCountedNotThrown() {
		FacilityAccessibilityRow orphan = new FacilityAccessibilityRow(
				FacilityAccessibilityRow.SBIZ, "FAC-STORE-NONE", "W-NONE", "주출입구 접근로",
				List.of("WHEELCHAIR"));

		FacilityAccessibilityLoader.Result result = this.loader.saveChunk(
				List.of(orphan, storeRow("주출입구 접근로")), DATASET, OffsetDateTime.now());

		assertThat(result.inserted()).isEqualTo(1);
		assertThat(result.noPlace()).isEqualTo(1);
	}
}
