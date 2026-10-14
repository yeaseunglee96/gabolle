package com.gabolle.backend.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;

import com.gabolle.backend.facility.api.FacilityDetail;
import com.gabolle.backend.facility.api.FacilityNearbyResponse;
import com.gabolle.backend.facility.api.FacilityRequestException;
import com.gabolle.backend.facility.domain.Facility;
import com.gabolle.backend.facility.domain.FacilityKind;
import com.gabolle.backend.facility.domain.ParkingFee;
import com.gabolle.backend.facility.domain.WeeklyHours;
import com.gabolle.backend.facility.domain.WeeklyHours.Window;
import com.gabolle.backend.facility.repository.FacilityRepository;
import com.gabolle.backend.place.service.GeoDistance;

/**
 * 근처 조회의 반경·정렬·필터·응답 칸 (S15P21E201-2052) — DB 없이 저장소를 흉내 내고 시계를 못 박아 돈다. 기준 시각은
 * 2026-10-14(수) 오전 10:30 서울이다. 기준점에서 북쪽으로 {@code meters} 만큼 떨어진 곳에 시설을 놓는다.
 */
class FacilityNearbyServiceTest {

	private static final double BASE_LAT = 35.1000;

	private static final double BASE_LNG = 129.0000;

	/** 위도 1도 ≈ 111,195m (지구 반지름 6,371,008.8m). */
	private static final double METERS_PER_DEGREE = 111_194.9;

	private static final OffsetDateTime COLLECTED = OffsetDateTime.parse("2026-10-14T00:00:00Z");

	private final FacilityRepository repository = mock(FacilityRepository.class);

	private FacilityNearbyService service;

	@BeforeEach
	void setUp() {
		// 2026-10-14T01:30Z = 서울 10:30, 수요일
		Clock clock = Clock.fixed(Instant.parse("2026-10-14T01:30:00Z"), ZoneOffset.UTC);
		this.service = new FacilityNearbyService(this.repository, clock);
	}

	private static double latAt(double metersNorth) {
		return BASE_LAT + metersNorth / METERS_PER_DEGREE;
	}

	private void returns(FacilityKind kind, Facility... found) {
		when(this.repository.findInBox(eq(kind), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(Limit.class)))
				.thenReturn(List.of(found));
	}

	private FacilityNearbyResponse query(String kind, Integer radius, Integer limit, Integer stay, Boolean accessible,
			Boolean diaper) {
		return this.service.nearby(new FacilityNearbyService.Query(kind, BASE_LAT, BASE_LNG, radius, limit, stay,
				accessible, diaper));
	}

	private FacilityNearbyResponse query(String kind) {
		return query(kind, null, null, null, null, null);
	}

	// ── 시설 만들기 ─────────────────────────────────────────────────────────

	private static Facility facility(FacilityKind kind, String sourceId, String name, double metersNorth,
			Map<String, Object> detail) {
		return Facility.create(kind, sourceId, name, "부산광역시 " + name, latAt(metersNorth), BASE_LNG, detail, "test",
				COLLECTED);
	}

	private static Map<String, Object> mapOf(Object... keyAndValue) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < keyAndValue.length; i += 2) {
			map.put((String) keyAndValue[i], keyAndValue[i + 1]);
		}
		return map;
	}

	private static Facility parking(String name, double meters, boolean free, ParkingFee fee, WeeklyHours hours) {
		return facility(FacilityKind.PARKING, name, name, meters,
				mapOf("operator", "PUBLIC", "free", free, "spaces", 10, "fee", fee == null ? null : fee.toDetail(),
						"hours", hours == null ? null : hours.toDetail(), "note", null));
	}

	private static Facility pharmacy(String name, double meters, WeeklyHours hours) {
		return facility(FacilityKind.PHARMACY, name, name, meters, mapOf("tel", "051-000-0000", "directions", "역 앞",
				"note", null, "hours", hours == null ? null : hours.toDetail()));
	}

	private static Facility toilet(String name, double meters, boolean accessible, boolean diaper) {
		return facility(FacilityKind.TOILET, name, name, meters, mapOf("type", "PUBLIC", "openHours", "상시",
				"alwaysOpen", true, "accessible", accessible, "diaper", diaper, "diaperPlace", diaper ? "BOTH" : null,
				"emergencyBell", false, "cctv", false, "tel", null));
	}

	private static Window window(int openHour, int closeHour) {
		return new Window(openHour * 60, closeHour * 60);
	}

	/** 월~일 같은 시간. */
	private static WeeklyHours everyDay(Window window) {
		Map<String, Window> days = new LinkedHashMap<>();
		for (String day : List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")) {
			days.put(day, window);
		}
		return WeeklyHours.of(days);
	}

	private static FacilityDetail.Parking parkingDetail(FacilityNearbyResponse.Item item) {
		return (FacilityDetail.Parking) item.detail();
	}

	private static FacilityDetail.Pharmacy pharmacyDetail(FacilityNearbyResponse.Item item) {
		return (FacilityDetail.Pharmacy) item.detail();
	}

	private static List<String> names(FacilityNearbyResponse response) {
		return response.items().stream().map(FacilityNearbyResponse.Item::name).toList();
	}

	// ── 주차장 ──────────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 주차장은 예상요금이 싼 순이고, 요금을 모르는 곳은 요금을 아는 곳 뒤로 간다 — 무료는 0원이라 맨 앞, 모르는 곳을 0원으로 세지 않는다")
	void parkingIsSortedByEstimatedFeeAndUnknownFeeGoesLast() {
		ParkingFee cheap = new ParkingFee(30, 300, 10, 50, null, 0); // 60분 → 300 + 3×50 = 450
		ParkingFee normal = new ParkingFee(30, 300, 10, 100, 2400, 0); // 60분 → 600
		ParkingFee baseOnly = new ParkingFee(30, 300, 10, null, 2400, 0); // 60분 → 모름
		returns(FacilityKind.PARKING,
				parking("모르는곳", 50, false, null, null),
				parking("기본만아는곳", 60, false, baseOnly, null),
				parking("보통B", 100, false, normal, null),
				parking("보통C", 200, false, normal, null),
				parking("무료", 300, true, null, null),
				parking("싼곳", 500, false, cheap, null));

		FacilityNearbyResponse response = query("PARKING");

		// 무료(0) → 싼곳(450) → 보통B·보통C(600, 같으면 가까운 순) → 모르는 곳은 거리 순(모르는곳 50m, 기본만아는곳 60m)
		assertThat(names(response)).containsExactly("무료", "싼곳", "보통B", "보통C", "모르는곳", "기본만아는곳");
		assertThat(response.items().stream().map(item -> parkingDetail(item).estimatedWon()).toList())
				.containsExactly(0, 450, 600, 600, null, null);
		assertThat(response.total()).isEqualTo(6);
		assertThat(response.stayMinutes()).isEqualTo(60);
		// 모르는 곳의 요금표도 null 이다 — 0원으로 바뀌지 않았다
		assertThat(parkingDetail(response.items().get(4)).fee()).isNull();
		assertThat(parkingDetail(response.items().get(5)).fee()).isEqualTo(baseOnly);
	}

	@Test
	@DisplayName("체류 시간을 바꾸면 예상요금이 달라진다 — 3시간이면 하루 최대로 잘린다")
	void stayMinutesChangesTheEstimate() {
		ParkingFee fee = new ParkingFee(30, 300, 10, 100, 1500, 0);
		returns(FacilityKind.PARKING, parking("가", 100, false, fee, null));

		assertThat(parkingDetail(query("PARKING", null, null, 30, null, null).items().get(0)).estimatedWon()).isEqualTo(300);
		assertThat(parkingDetail(query("PARKING", null, null, 60, null, null).items().get(0)).estimatedWon()).isEqualTo(600);
		FacilityNearbyResponse threeHours = query("PARKING", null, null, 180, null, null);
		assertThat(parkingDetail(threeHours.items().get(0)).estimatedWon()).isEqualTo(1500);
		assertThat(threeHours.stayMinutes()).isEqualTo(180);
	}

	@Test
	@DisplayName("주차장 응답 칸: 공영/민영·구획 수·요금표·영업 중·오늘 시간·닫는 시각")
	void parkingDetailFields() {
		WeeklyHours open = everyDay(window(8, 20));
		returns(FacilityKind.PARKING,
				parking("열린곳", 100, false, new ParkingFee(30, 300, 10, 100, null, 0), open),
				parking("시간모름", 200, true, null, null),
				parking("종일", 300, false, new ParkingFee(30, 300, 10, 100, null, 0),
						WeeklyHours.of(Map.of("Wednesday", new Window(0, 1440)))));

		List<FacilityNearbyResponse.Item> items = query("PARKING").items();
		FacilityDetail.Parking openLot = parkingDetail(items.stream().filter(item -> item.name().equals("열린곳")).findFirst().orElseThrow());
		assertThat(openLot.operator()).isEqualTo("PUBLIC");
		assertThat(openLot.free()).isFalse();
		assertThat(openLot.spaces()).isEqualTo(10);
		assertThat(openLot.openNow()).isTrue();
		assertThat(openLot.todayHours()).isEqualTo(new FacilityDetail.Hours("08:00", "20:00"));
		assertThat(openLot.closesAt()).isEqualTo("20:00");
		assertThat(openLot.allDay()).isFalse();

		FacilityDetail.Parking unknownHours = parkingDetail(items.stream().filter(item -> item.name().equals("시간모름")).findFirst().orElseThrow());
		assertThat(unknownHours.openNow()).isNull();
		assertThat(unknownHours.todayHours()).isNull();
		assertThat(unknownHours.closesAt()).isNull();
		assertThat(unknownHours.free()).isTrue();

		FacilityDetail.Parking allDay = parkingDetail(items.stream().filter(item -> item.name().equals("종일")).findFirst().orElseThrow());
		assertThat(allDay.allDay()).isTrue();
		assertThat(allDay.openNow()).isTrue();
	}

	@Test
	@DisplayName("🔴 무료 주차장은 요금표가 비어 있어도 0원이고, 유료인데 요금표가 비면 null 이다 — 둘을 바꿔 읽지 않는다")
	void freeIsZeroAndUnknownIsNull() {
		returns(FacilityKind.PARKING, parking("무료", 100, true, null, null), parking("유료모름", 100, false, null, null));

		List<FacilityNearbyResponse.Item> items = query("PARKING").items();

		assertThat(parkingDetail(items.get(0)).estimatedWon()).isZero();
		assertThat(items.get(0).name()).isEqualTo("무료");
		assertThat(parkingDetail(items.get(1)).estimatedWon()).isNull();
	}

	// ── 반경·개수 ───────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 반경은 경계상자가 아니라 실제 직선 거리로 거른다 — 상자 모서리의 785m 는 반경 700m 에 안 든다")
	void radiusIsCheckedWithRealDistance() {
		// 북쪽 556m, 동쪽 556m — 경계상자 안이지만 실제 거리는 약 785m
		Facility corner = Facility.create(FacilityKind.TOILET, "corner", "모서리", "주소", BASE_LAT + 0.005,
				BASE_LNG + 0.0061, mapOf("type", "PUBLIC"), "test", COLLECTED);
		assertThat(GeoDistance.meters(BASE_LAT, BASE_LNG, corner.getLat(), corner.getLng())).isGreaterThan(700);
		returns(FacilityKind.TOILET, toilet("가까운", 500, false, false), toilet("먼", 900, false, false), corner);

		FacilityNearbyResponse narrow = query("TOILET", 700, null, null, null, null);
		assertThat(names(narrow)).containsExactly("가까운");
		assertThat(narrow.total()).isEqualTo(1);
		assertThat(narrow.radiusM()).isEqualTo(700);

		// 반경을 넓히면 들어온다
		FacilityNearbyResponse wide = query("TOILET", 1000, null, null, null, null);
		assertThat(names(wide)).containsExactly("가까운", "모서리", "먼");
	}

	@Test
	@DisplayName("개수를 자르면 목록만 줄고 total 은 반경 안의 전체 수다. 거리는 반올림한 미터다")
	void limitTrimsTheListButNotTheTotal() {
		returns(FacilityKind.LOCKER,
				facility(FacilityKind.LOCKER, "a", "가역", 300, mapOf("station", "가", "small", 1)),
				facility(FacilityKind.LOCKER, "b", "나역", 100, mapOf("station", "나", "small", 2)),
				facility(FacilityKind.LOCKER, "c", "다역", 200, mapOf("station", "다", "small", 3)));

		FacilityNearbyResponse response = query("LOCKER", null, 2, null, null, null);

		assertThat(names(response)).containsExactly("나역", "다역");
		assertThat(response.total()).isEqualTo(3);
		assertThat(response.limit()).isEqualTo(2);
		assertThat(response.items().get(0).distanceM()).isBetween(99, 101);
		assertThat(response.items().get(1).distanceM()).isBetween(199, 201);
	}

	@Test
	@DisplayName("값을 안 주면 기본값을 쓴다 — 반경 700m · 20곳 · 체류 60분(주차장만 응답에 싣는다)")
	void defaults() {
		returns(FacilityKind.PARKING);
		returns(FacilityKind.TOILET);

		FacilityNearbyResponse parking = query("PARKING");
		assertThat(parking.radiusM()).isEqualTo(700);
		assertThat(parking.limit()).isEqualTo(20);
		assertThat(parking.stayMinutes()).isEqualTo(60);
		assertThat(parking.items()).isEmpty();
		assertThat(parking.total()).isZero();
		assertThat(query("TOILET").stayMinutes()).isNull();
	}

	@Test
	@DisplayName("종류 이름은 대소문자를 가리지 않고, 응답은 서울 시각(+09:00)으로 판정 기준 시각을 남긴다")
	void kindIsCaseInsensitiveAndResponseCarriesTheSeoulTime() {
		returns(FacilityKind.PARKING);

		FacilityNearbyResponse response = query("parking");

		assertThat(response.kind()).isEqualTo("PARKING");
		assertThat(response.asOf()).isEqualTo("2026-10-14T10:30:00+09:00");
		assertThat(response.lat()).isEqualTo(BASE_LAT);
		assertThat(response.lng()).isEqualTo(BASE_LNG);
	}

	// ── 약국 ────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 약국은 지금 연 곳이 먼저, 그다음 가까운 순이다 — 닫은 곳·시간을 모르는 곳이 더 가까워도 뒤로 간다")
	void pharmacyOpenNowFirstThenDistance() {
		returns(FacilityKind.PHARMACY,
				pharmacy("모르는곳", 50, null),
				pharmacy("닫은곳", 100, everyDay(window(12, 18))),
				pharmacy("열린B", 300, everyDay(window(9, 18))),
				pharmacy("열린A", 200, everyDay(window(8, 24))));

		FacilityNearbyResponse response = query("PHARMACY");

		assertThat(names(response)).containsExactly("열린A", "열린B", "모르는곳", "닫은곳");
		assertThat(response.items().stream().map(item -> pharmacyDetail(item).openNow()).toList())
				.containsExactly(true, true, null, false);
		assertThat(response.stayMinutes()).isNull();
	}

	@Test
	@DisplayName("약국 응답 칸: 오늘 시간·닫는 시각·전화·찾아가는 길, 그리고 늘 true 인 hoursAreReported(방문 전 전화 확인)")
	void pharmacyDetailFields() {
		returns(FacilityKind.PHARMACY, pharmacy("열린곳", 100, everyDay(window(9, 18))),
				pharmacy("닫은곳", 200, everyDay(window(12, 18))), pharmacy("모르는곳", 300, null));

		List<FacilityNearbyResponse.Item> items = query("PHARMACY").items();

		FacilityDetail.Pharmacy open = pharmacyDetail(items.get(0));
		assertThat(open.hoursAreReported()).isTrue();
		assertThat(open.openNow()).isTrue();
		assertThat(open.todayHours()).isEqualTo(new FacilityDetail.Hours("09:00", "18:00"));
		assertThat(open.closesAt()).isEqualTo("18:00");
		assertThat(open.tel()).isEqualTo("051-000-0000");
		assertThat(open.directions()).isEqualTo("역 앞");

		FacilityDetail.Pharmacy closed = pharmacyDetail(items.stream().filter(item -> item.name().equals("닫은곳")).findFirst().orElseThrow());
		assertThat(closed.openNow()).isFalse();
		assertThat(closed.todayHours()).isEqualTo(new FacilityDetail.Hours("12:00", "18:00"));
		assertThat(closed.closesAt()).isNull();

		FacilityDetail.Pharmacy unknown = pharmacyDetail(items.stream().filter(item -> item.name().equals("모르는곳")).findFirst().orElseThrow());
		assertThat(unknown.hoursAreReported()).isTrue();
		assertThat(unknown.openNow()).isNull();
		assertThat(unknown.todayHours()).isNull();
	}

	@Test
	@DisplayName("일요일·공휴일 영업과 심야(밤 11시 이후까지) 표시 — 자정을 넘기는 25:00 은 닫는 시각 01:00 으로 접혀 보인다")
	void pharmacySundayHolidayAndLateNightFlags() {
		Map<String, Window> days = new LinkedHashMap<>();
		days.put("Monday", window(9, 18));
		days.put("Wednesday", new Window(9 * 60, 25 * 60));
		days.put("Sunday", window(10, 20));
		days.put("Holiday", window(10, 20));
		returns(FacilityKind.PHARMACY, pharmacy("심야", 100, WeeklyHours.of(days)),
				pharmacy("평일만", 200, WeeklyHours.of(Map.of("Monday", window(9, 18)))));

		List<FacilityNearbyResponse.Item> items = query("PHARMACY").items();

		FacilityDetail.Pharmacy late = pharmacyDetail(items.get(0));
		assertThat(late.opensSunday()).isTrue();
		assertThat(late.opensHoliday()).isTrue();
		assertThat(late.lateNight()).isTrue();
		// 수요일 10:30 — 09:00~25:00 이라 열려 있고 다음 날 01:00 에 닫는다
		assertThat(late.openNow()).isTrue();
		assertThat(late.closesAt()).isEqualTo("01:00");
		assertThat(late.todayHours()).isEqualTo(new FacilityDetail.Hours("09:00", "01:00"));

		FacilityDetail.Pharmacy weekdayOnly = pharmacyDetail(items.get(1));
		assertThat(weekdayOnly.opensSunday()).isFalse();
		assertThat(weekdayOnly.opensHoliday()).isFalse();
		assertThat(weekdayOnly.lateNight()).isFalse();
		// 수요일 칸이 없다 → 쉬는 날
		assertThat(weekdayOnly.openNow()).isFalse();
		assertThat(weekdayOnly.todayHours()).isNull();
	}

	// ── 공중화장실 ──────────────────────────────────────────────────────────

	@Test
	@DisplayName("화장실은 가까운 순이고 장애인용·기저귀 교환대 필터는 true 일 때만 건다 — 둘을 같이 주면 둘 다 있는 곳")
	void toiletFilters() {
		returns(FacilityKind.TOILET,
				toilet("둘다", 100, true, true),
				toilet("장애인만", 150, true, false),
				toilet("기저귀만", 200, false, true),
				toilet("없음", 50, false, false));

		assertThat(names(query("TOILET"))).containsExactly("없음", "둘다", "장애인만", "기저귀만");
		assertThat(names(query("TOILET", null, null, null, true, null))).containsExactly("둘다", "장애인만");
		assertThat(names(query("TOILET", null, null, null, null, true))).containsExactly("둘다", "기저귀만");
		FacilityNearbyResponse both = query("TOILET", null, null, null, true, true);
		assertThat(names(both)).containsExactly("둘다");
		assertThat(both.total()).isEqualTo(1);
		// false 는 «거르지 않음» 이다 — 「장애인용이 아닌 곳만」이 아니다
		assertThat(names(query("TOILET", null, null, null, false, false))).hasSize(4);
	}

	@Test
	@DisplayName("화장실 응답 칸: 종류·개방시간 글·늘 개방·장애인용·기저귀 교환대와 위치")
	void toiletDetailFields() {
		returns(FacilityKind.TOILET, toilet("둘다", 100, true, true));

		FacilityDetail.Toilet detail = (FacilityDetail.Toilet) query("TOILET").items().get(0).detail();

		assertThat(detail.type()).isEqualTo("PUBLIC");
		assertThat(detail.openHours()).isEqualTo("상시");
		assertThat(detail.alwaysOpen()).isTrue();
		assertThat(detail.accessible()).isTrue();
		assertThat(detail.diaper()).isTrue();
		assertThat(detail.diaperPlace()).isEqualTo("BOTH");
	}

	@Test
	@DisplayName("필터는 화장실에만 걸린다 — 주차장 조회에 accessible 을 줘도 거르지 않는다")
	void filtersOnlyApplyToToilets() {
		returns(FacilityKind.PARKING, parking("가", 100, true, null, null));

		assertThat(query("PARKING", null, null, null, true, true).items()).hasSize(1);
	}

	// ── 물품보관함 ──────────────────────────────────────────────────────────

	@Test
	@DisplayName("물품보관함 응답 칸: 역 이름(한·영)·호선·위치 글·크기별 칸 수·요금 글")
	void lockerDetailFields() {
		returns(FacilityKind.LOCKER, facility(FacilityKind.LOCKER, "BTC-1-하단-3", "하단역", 120,
				mapOf("station", "하단", "stationEn", "Hadan", "line", "1", "location", "(B1) 1번 엘리베이터 옆", "small", 10,
						"medium", 10, "large", 1, "xlarge", 7, "feeText", "소: 2,000원(3시간당)", "operator", "위드락커")));

		FacilityNearbyResponse.Item item = query("LOCKER").items().get(0);
		FacilityDetail.Locker detail = (FacilityDetail.Locker) item.detail();

		assertThat(item.name()).isEqualTo("하단역");
		assertThat(item.kind()).isEqualTo("LOCKER");
		assertThat(detail.station()).isEqualTo("하단");
		assertThat(detail.stationEn()).isEqualTo("Hadan");
		assertThat(detail.line()).isEqualTo("1");
		assertThat(detail.location()).isEqualTo("(B1) 1번 엘리베이터 옆");
		assertThat(detail.small()).isEqualTo(10);
		assertThat(detail.medium()).isEqualTo(10);
		assertThat(detail.large()).isEqualTo(1);
		assertThat(detail.xlarge()).isEqualTo(7);
		assertThat(detail.feeText()).isEqualTo("소: 2,000원(3시간당)");
		assertThat(detail.operator()).isEqualTo("위드락커");
	}

	@Test
	@DisplayName("저장된 칸이 깨져 있어도 조회가 터지지 않는다 — 모르는 값으로 읽는다")
	void brokenStoredDetailDoesNotBreakTheQuery() {
		Map<String, Object> broken = new LinkedHashMap<>();
		broken.put("free", "아니오");
		broken.put("fee", "고장");
		broken.put("hours", List.of(1, 2));
		broken.put("spaces", "많음");
		returns(FacilityKind.PARKING, facility(FacilityKind.PARKING, "x", "고장난곳", 100, broken));

		FacilityDetail.Parking detail = parkingDetail(query("PARKING").items().get(0));

		assertThat(detail.free()).isFalse();
		assertThat(detail.fee()).isNull();
		assertThat(detail.estimatedWon()).isNull();
		assertThat(detail.openNow()).isNull();
		assertThat(detail.spaces()).isNull();
	}

	// ── 요청 검사 ───────────────────────────────────────────────────────────

	private List<String> invalidFields(String kind, double lat, double lng, Integer radius, Integer limit, Integer stay) {
		try {
			this.service.nearby(new FacilityNearbyService.Query(kind, lat, lng, radius, limit, stay, null, null));
		}
		catch (FacilityRequestException exception) {
			return exception.getFields();
		}
		return List.of();
	}

	@Test
	@DisplayName("범위 밖 값은 어느 칸이 문제인지와 함께 거절한다 — 반경 2000m·개수 50·체류 1440분이 상한")
	void invalidRequestsAreRejectedWithTheFieldNames() {
		assertThat(invalidFields("BOGUS", BASE_LAT, BASE_LNG, null, null, null)).containsExactly("kind");
		assertThat(invalidFields(null, BASE_LAT, BASE_LNG, null, null, null)).containsExactly("kind");
		assertThat(invalidFields("PARKING", 91, BASE_LNG, null, null, null)).containsExactly("lat");
		assertThat(invalidFields("PARKING", Double.NaN, BASE_LNG, null, null, null)).containsExactly("lat");
		assertThat(invalidFields("PARKING", BASE_LAT, 181, null, null, null)).containsExactly("lng");
		assertThat(invalidFields("PARKING", BASE_LAT, Double.POSITIVE_INFINITY, null, null, null)).containsExactly("lng");
		assertThat(invalidFields("PARKING", BASE_LAT, BASE_LNG, 0, null, null)).containsExactly("radiusM");
		assertThat(invalidFields("PARKING", BASE_LAT, BASE_LNG, 2001, null, null)).containsExactly("radiusM");
		assertThat(invalidFields("PARKING", BASE_LAT, BASE_LNG, null, 0, null)).containsExactly("limit");
		assertThat(invalidFields("PARKING", BASE_LAT, BASE_LNG, null, 51, null)).containsExactly("limit");
		assertThat(invalidFields("PARKING", BASE_LAT, BASE_LNG, null, null, 0)).containsExactly("stayMinutes");
		assertThat(invalidFields("PARKING", BASE_LAT, BASE_LNG, null, null, 1441)).containsExactly("stayMinutes");
		// 여러 칸이 틀리면 한꺼번에 알린다
		assertThat(invalidFields("BOGUS", 99, BASE_LNG, 5000, 100, 0)).containsExactly("kind", "lat", "radiusM", "limit",
				"stayMinutes");
	}

	@Test
	@DisplayName("상한 값은 받는다 — 반경 2000m · 개수 50 · 체류 1440분 · 반경 1m")
	void boundaryValuesAreAccepted() {
		returns(FacilityKind.PARKING);

		assertThat(invalidFields("PARKING", BASE_LAT, BASE_LNG, 2000, 50, 1440)).isEmpty();
		assertThat(invalidFields("PARKING", BASE_LAT, BASE_LNG, 1, 1, 1)).isEmpty();
		assertThat(invalidFields("PARKING", -90, -180, null, null, null)).isEmpty();
	}

	@Test
	@DisplayName("요청 오류는 저장소를 부르지 않는다")
	void invalidRequestNeverHitsTheDatabase() {
		assertThatThrownBy(() -> query("BOGUS")).isInstanceOf(FacilityRequestException.class);
		org.mockito.Mockito.verifyNoInteractions(this.repository);
	}

	@Test
	@DisplayName("정렬은 늘 같은 순서다 — 거리가 같으면 이름 순")
	void ordersAreDeterministic() {
		List<Facility> same = new ArrayList<>(List.of(toilet("나", 100, false, false), toilet("가", 100, false, false),
				toilet("다", 100, false, false)));
		returns(FacilityKind.TOILET, same.toArray(new Facility[0]));

		assertThat(names(query("TOILET"))).containsExactly("가", "나", "다");
	}
}
