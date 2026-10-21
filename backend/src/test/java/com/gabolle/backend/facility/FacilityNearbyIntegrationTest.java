package com.gabolle.backend.facility;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.facility.api.FacilityDetail;
import com.gabolle.backend.facility.api.FacilityNearbyResponse;
import com.gabolle.backend.facility.domain.Facility;
import com.gabolle.backend.facility.domain.FacilityKind;
import com.gabolle.backend.facility.domain.ParkingFee;
import com.gabolle.backend.facility.loader.FacilityLoader;
import com.gabolle.backend.facility.loader.FacilityRow;
import com.gabolle.backend.facility.repository.FacilityRepository;
import com.gabolle.backend.facility.service.FacilityNearbyService;
import com.gabolle.backend.facility.support.FacilityPostgresIntegrationTest;
import com.gabolle.backend.place.service.GeoDistance;

/**
 * 근처 조회가 진짜 PostgreSQL 에서 도는지 (S15P21E201-2052) — 경계상자 질의, 종류 가르기, JSONB 에서 돌아온 값으로 만든 응답.
 * 규칙(정렬·필터·요금)은 {@code FacilityNearbyServiceTest} 가 시계를 못 박고 본다. 여기서는 시계를 건드리지 않으므로 <b>시각에 따라
 * 달라지는 칸(영업 중)은 단정하지 않는다.</b>
 *
 * <p>시험 행은 부산이 아닌 먼 좌표(위도 33.2·경도 126.5, 제주 남쪽 바다)에 놓는다 — 같은 DB 에 다른 시험이나 실제 자료가 있어도 반경에
 * 안 걸린다. DB 제약은 좌표 범위만 보고 부산 상자는 적재기(읽기 단계)가 따로 검사한다.
 */
class FacilityNearbyIntegrationTest extends FacilityPostgresIntegrationTest {

	private static final String ID = "ZZ2052-N-";

	private static final double LAT = 33.2000;

	private static final double LNG = 126.5000;

	private static final double METERS_PER_DEGREE = 111_194.9;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private FacilityRepository repository;

	@Autowired
	private FacilityLoader loader;

	@Autowired
	private FacilityNearbyService service;

	@BeforeEach
	void cleanBefore() {
		deleteWhatThisTestWrote();
	}

	@AfterEach
	void cleanAfter() {
		deleteWhatThisTestWrote();
	}

	private void deleteWhatThisTestWrote() {
		this.jdbcTemplate.update("DELETE FROM facility WHERE source_id LIKE ?", ID + "%");
	}

	private static Map<String, Object> mapOf(Object... keyAndValue) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < keyAndValue.length; i += 2) {
			map.put((String) keyAndValue[i], keyAndValue[i + 1]);
		}
		return map;
	}

	private static FacilityRow row(FacilityKind kind, String suffix, String name, double metersNorth, Map<String, Object> detail) {
		return new FacilityRow(kind, ID + suffix, name, "주소 " + name, LAT + metersNorth / METERS_PER_DEGREE, LNG, detail);
	}

	private void load(FacilityRow... rows) {
		Map<FacilityKind, List<FacilityRow>> byKind = new EnumMap<>(FacilityKind.class);
		for (FacilityRow each : rows) {
			byKind.computeIfAbsent(each.kind(), key -> new ArrayList<>()).add(each);
		}
		byKind.values().forEach(list -> this.loader.saveChunk(list, "facility-nearby-test", OffsetDateTime.now()));
	}

	private FacilityNearbyResponse query(String kind, Integer radius, Integer stay, Boolean accessible, Boolean diaper) {
		return this.service.nearby(new FacilityNearbyService.Query(kind, LAT, LNG, radius, null, stay, accessible, diaper));
	}

	private static List<String> names(FacilityNearbyResponse response) {
		return response.items().stream().map(FacilityNearbyResponse.Item::name).toList();
	}

	@Test
	@DisplayName("🔴 경계상자 질의가 반경 안의 한 종류만 가져온다 — 반경 밖, 다른 종류는 안 나오고 가까운 순이다")
	void boundingBoxQueryReturnsOnlyTheKindWithinRadius() {
		Map<String, Object> toiletDetail = mapOf("type", "PUBLIC", "accessible", false, "diaper", false);
		load(row(FacilityKind.TOILET, "t-near", "가까운화장실", 200, toiletDetail),
				row(FacilityKind.TOILET, "t-mid", "중간화장실", 600, toiletDetail),
				row(FacilityKind.TOILET, "t-far", "먼화장실", 1500, toiletDetail),
				row(FacilityKind.LOCKER, "l-near", "가까운보관함", 100, mapOf("station", "시험", "small", 1)));

		assertThat(names(query("TOILET", 700, null, null, null))).containsExactly("가까운화장실", "중간화장실");
		assertThat(names(query("TOILET", 300, null, null, null))).containsExactly("가까운화장실");
		assertThat(names(query("TOILET", 2000, null, null, null))).containsExactly("가까운화장실", "중간화장실", "먼화장실");
		assertThat(names(query("LOCKER", null, null, null, null))).containsExactly("가까운보관함");
		assertThat(query("PARKING", null, null, null, null).items()).isEmpty();
	}

	@Test
	@DisplayName("경계 밖 위도·경도에 있는 행은 상자에서 걸러진다 — 같은 종류라도 먼 곳은 안 가져온다")
	void rowsOutsideTheBoxAreNotFetched() {
		load(row(FacilityKind.TOILET, "t-1", "가까운화장실", 100, mapOf("type", "PUBLIC")));
		GeoDistance.BoundingBox box = GeoDistance.boundingBox(LAT, LNG, 700);

		List<Facility> inBox = this.repository.findInBox(FacilityKind.TOILET, box.minLat(), box.maxLat(), box.minLng(),
				box.maxLng(), Limit.of(100));
		List<Facility> elsewhere = this.repository.findInBox(FacilityKind.TOILET, 35.0, 35.1, 129.0, 129.1, Limit.of(100));

		assertThat(inBox).extracting(Facility::getName).contains("가까운화장실");
		assertThat(elsewhere).extracting(Facility::getName).doesNotContain("가까운화장실");
	}

	@Test
	@DisplayName("상한(Limit)을 걸면 그만큼만 가져온다 — 위도·경도·번호 순이라 어느 행이 남는지 늘 같다")
	void scanLimitCapsTheFetch() {
		Map<String, Object> detail = mapOf("type", "PUBLIC");
		load(row(FacilityKind.TOILET, "a", "가", 100, detail), row(FacilityKind.TOILET, "b", "나", 200, detail),
				row(FacilityKind.TOILET, "c", "다", 300, detail));
		GeoDistance.BoundingBox box = GeoDistance.boundingBox(LAT, LNG, 700);

		List<Facility> capped = this.repository.findInBox(FacilityKind.TOILET, box.minLat(), box.maxLat(), box.minLng(),
				box.maxLng(), Limit.of(2));

		assertThat(capped).extracting(Facility::getName).containsExactly("가", "나");
	}

	@Test
	@DisplayName("🔴 JSONB 에서 돌아온 요금표로 예상요금을 낸다 — 싼 순, 모르는 요금(JSON null)은 맨 뒤에서 null")
	void parkingEstimateFromStoredJson() {
		load(row(FacilityKind.PARKING, "p-free", "무료주차장", 400,
						mapOf("operator", "PUBLIC", "free", true, "spaces", 20, "fee", null, "hours", null, "note", null)),
				row(FacilityKind.PARKING, "p-paid", "유료주차장", 300,
						mapOf("operator", "PUBLIC", "free", false, "spaces", 10, "fee",
								new ParkingFee(30, 300, 10, 100, 2400, 0).toDetail(), "hours", null, "note", null)),
				row(FacilityKind.PARKING, "p-partial", "일부만아는주차장", 100,
						mapOf("operator", "PRIVATE", "free", false, "spaces", 5, "fee",
								new ParkingFee(30, 300, 10, null, 2400, 0).toDetail(), "hours", null, "note", null)),
				row(FacilityKind.PARKING, "p-unknown", "모르는주차장", 50,
						mapOf("operator", "PUBLIC", "free", false, "spaces", 8, "fee", null, "hours", null, "note",
								"2번 출구 · 부산시설공단")));

		FacilityNearbyResponse response = query("PARKING", null, 120, null, null);

		assertThat(names(response)).containsExactly("무료주차장", "유료주차장", "모르는주차장", "일부만아는주차장");
		List<Integer> estimates = response.items().stream()
				.map(item -> ((FacilityDetail.Parking) item.detail()).estimatedWon()).toList();
		assertThat(estimates).containsExactly(0, 1200, null, null);
		FacilityDetail.Parking partial = (FacilityDetail.Parking) response.items().get(3).detail();
		assertThat(partial.fee()).isEqualTo(new ParkingFee(30, 300, 10, null, 2400, 0));
		assertThat(partial.operator()).isEqualTo("PRIVATE");
		FacilityDetail.Parking unknown = (FacilityDetail.Parking) response.items().get(2).detail();
		assertThat(unknown.fee()).isNull();
		assertThat(unknown.note()).isEqualTo("2번 출구 · 부산시설공단");
		// 시간을 모르는 곳(hours null) 은 시계와 무관하게 «모름»이다
		assertThat(unknown.openNow()).isNull();
	}

	@Test
	@DisplayName("화장실 필터가 DB 에서 읽은 불리언으로 돈다 — 장애인용 · 기저귀 교환대")
	void toiletFiltersFromStoredJson() {
		load(row(FacilityKind.TOILET, "t-both", "둘다", 100, mapOf("type", "PUBLIC", "accessible", true, "diaper", true,
						"diaperPlace", "BOTH")),
				row(FacilityKind.TOILET, "t-acc", "장애인만", 200, mapOf("type", "PUBLIC", "accessible", true, "diaper", false)),
				row(FacilityKind.TOILET, "t-none", "없음", 50, mapOf("type", "SIMPLE", "accessible", false, "diaper", false)));

		assertThat(names(query("TOILET", null, null, true, null))).containsExactly("둘다", "장애인만");
		assertThat(names(query("TOILET", null, null, null, true))).containsExactly("둘다");
		FacilityDetail.Toilet both = (FacilityDetail.Toilet) query("TOILET", null, null, true, true).items().get(0).detail();
		assertThat(both.diaperPlace()).isEqualTo("BOTH");
	}

	@Test
	@DisplayName("약국 응답이 저장된 시간표에서 일요일·공휴일·심야 표시를 낸다 — 시계와 무관한 칸만 본다")
	void pharmacyFlagsFromStoredHours() {
		Map<String, Object> hours = new LinkedHashMap<>();
		hours.put("Monday", mapOf("open", "09:00", "close", "25:00"));
		hours.put("Sunday", mapOf("open", "10:00", "close", "20:00"));
		hours.put("Saturday", null);
		load(row(FacilityKind.PHARMACY, "ph-1", "시험약국", 150,
						mapOf("tel", "051-000-0000", "directions", "역 앞", "note", null, "hours", hours)),
				row(FacilityKind.PHARMACY, "ph-2", "시간모르는약국", 100,
						mapOf("tel", null, "directions", null, "note", null, "hours", null)));

		FacilityNearbyResponse response = query("PHARMACY", null, null, null, null);

		FacilityDetail.Pharmacy known = (FacilityDetail.Pharmacy) response.items().stream()
				.filter(item -> item.name().equals("시험약국")).findFirst().orElseThrow().detail();
		assertThat(known.opensSunday()).isTrue();
		assertThat(known.opensHoliday()).isFalse();
		assertThat(known.lateNight()).isTrue();
		assertThat(known.hoursAreReported()).isTrue();
		assertThat(known.tel()).isEqualTo("051-000-0000");
		FacilityDetail.Pharmacy unknown = (FacilityDetail.Pharmacy) response.items().stream()
				.filter(item -> item.name().equals("시간모르는약국")).findFirst().orElseThrow().detail();
		assertThat(unknown.openNow()).isNull();
		assertThat(unknown.opensSunday()).isFalse();
	}

	@Test
	@DisplayName("응답 한 곳의 공통 칸 — id 는 행 번호, 거리는 반올림한 미터, 좌표는 저장된 값")
	void itemCommonFields() {
		load(row(FacilityKind.LOCKER, "l-1", "시험역", 250, mapOf("station", "시험", "line", "1", "small", 3)));

		FacilityNearbyResponse.Item item = query("LOCKER", null, null, null, null).items().get(0);

		assertThat(item.id()).isEqualTo(Facility.idOf(FacilityKind.LOCKER, ID + "l-1").toString());
		assertThat(item.kind()).isEqualTo("LOCKER");
		assertThat(item.address()).isEqualTo("주소 시험역");
		assertThat(item.distanceM()).isBetween(249, 251);
		assertThat(item.lat()).isEqualTo(LAT + 250 / METERS_PER_DEGREE);
		assertThat(item.lng()).isEqualTo(LNG);
	}
}
