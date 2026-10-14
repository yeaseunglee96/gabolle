package com.gabolle.backend.facility.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gabolle.backend.facility.domain.FacilityKind;
import com.gabolle.backend.facility.loader.FacilityReader.Reason;
import com.gabolle.backend.facility.loader.SubwayStations.Station;

/**
 * 근처 편의시설 파일 넷을 읽고 다듬는 규칙 (S15P21E201-2052). 표본은 <b>실제 파일에서 문제 줄을 그대로 뽑은 것</b>이다
 * ({@code src/test/resources/facility/*-sample.ndjson}) — 요금이 정수 최댓값인 줄, 시간이 {@code 00:00~00:00} 인 줄, 좌표가 6km 어긋난
 * 물품보관함, 주소가 「연제구」뿐인 화장실. 모르는 값을 0 이나 빈 글자로 지어내지 않는지가 핵심이다.
 */
class FacilityReaderTest {

	private static final Path SAMPLES = Path.of("src/test/resources/facility");

	private final SubwayStations stations = SubwayStations.fromClasspath();

	@TempDir
	Path tmp;

	// ── 주차장 ──────────────────────────────────────────────────────────────

	private FacilityReader.Result parking() {
		return FacilityReader.readParking(SAMPLES.resolve("parking-sample.ndjson"), this.stations);
	}

	private static FacilityRow row(FacilityReader.Result result, String sourceId) {
		return result.rows().stream().filter(each -> each.sourceId().equals(sourceId)).findFirst()
				.orElseThrow(() -> new AssertionError("행이 없다: " + sourceId));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(Object value) {
		return (Map<String, Object>) value;
	}

	@Test
	@DisplayName("주차장 표본 열여덟 줄이 전부 들어간다 — 건너뛰는 줄은 없다")
	void parkingSampleReadsEveryLine() {
		FacilityReader.Result result = parking();

		assertThat(result.kind()).isEqualTo(FacilityKind.PARKING);
		assertThat(result.linesRead()).isEqualTo(18);
		assertThat(result.rows()).hasSize(18);
		assertThat(result.skipped()).isEmpty();
	}

	@Test
	@DisplayName("요금을 아는 유료 주차장은 요금표가 그대로 들어간다 — 주소의 겹공백은 한 칸으로")
	void paidLotKeepsItsFeeTable() {
		FacilityRow lot = row(parking(), "10000-11191-00072-00-1");

		assertThat(lot.name()).isEqualTo("주례오거리공영주차장");
		assertThat(lot.address()).isEqualTo("부산광역시 사상구 가야대로255번길 1");
		assertThat(lot.detail()).containsEntry("operator", "PUBLIC").containsEntry("free", false)
				.containsEntry("spaces", 93);
		assertThat(map(lot.detail().get("fee"))).containsEntry("baseMinutes", 30).containsEntry("baseWon", 300)
				.containsEntry("addMinutes", 10).containsEntry("addWon", 100).containsEntry("dayMaxWon", 2400)
				.containsEntry("freeMinutes", 0);
		assertThat(map(map(lot.detail().get("hours")).get("Monday"))).containsEntry("open", "08:00")
				.containsEntry("close", "20:00");
	}

	@Test
	@DisplayName("🔴 추가요금이 0원인 줄은 그 칸만 비운다(기본요금까지는 안다) — 일요일 00:00~00:00 은 «모른다»라 칸이 null 로 남는다")
	void zeroAddWonAndUnknownSunday() {
		FacilityReader.Result result = parking();
		FacilityRow lot = row(result, "10000-11191-00003-00-1");

		assertThat(lot.address()).isEqualTo("부산광역시 사하구 신평동 664");
		Map<String, Object> fee = map(lot.detail().get("fee"));
		assertThat(fee).containsEntry("baseWon", 300).containsEntry("dayMaxWon", 2400).containsEntry("addWon", null);
		Map<String, Object> hours = map(lot.detail().get("hours"));
		assertThat(map(hours.get("Saturday"))).containsEntry("open", "08:00").containsEntry("close", "15:00");
		// 키는 있는데 값이 null — 쉬는 날(키 없음)이 아니라 모르는 날이다
		assertThat(hours).containsKey("Sunday").containsEntry("Sunday", null);
		assertThat(hours).containsEntry("Holiday", null);
		assertThat(result.nulled()).containsKey("fee.ADD_WON_ZERO");
	}

	@Test
	@DisplayName("🔴 feeKnown=true 인데 기본요금이 정수 최댓값이거나 4만 원이면 요금표를 비운다 — 입력의 표시를 믿지 않고 값도 본다")
	void implausibleFeeIsDroppedEvenWhenFeeKnownIsTrue() {
		FacilityReader.Result result = parking();

		for (String sourceId : List.of("10000-11191-00305-00-1", "10000-11191-00308-00-1", "10000-11191-00880-00-1",
				"10000-11191-00881-00-1")) {
			assertThat(row(result, sourceId).detail()).as(sourceId).containsEntry("free", false).containsEntry("fee", null);
		}
		assertThat(result.nulled()).containsEntry("fee.BASE_WON_IMPLAUSIBLE", 4).containsEntry("fee.요금표를 못 씀", 4);
	}

	@Test
	@DisplayName("🔴 모든 날이 00:00~00:00 인 줄은 시간을 모른다 — hours 가 통째로 null (하루 종일 연다고도 쉰다고도 안 적는다)")
	void midnightToMidnightEveryDayLeavesHoursUnknown() {
		FacilityRow lot = row(parking(), "10000-11191-00305-00-1");

		assertThat(lot.detail()).containsEntry("hours", null);
	}

	@Test
	@DisplayName("무료 주차장은 요금표를 두지 않고 free 로만 말한다 — 09:00~00:00 은 24:00 에 닫는 것")
	void freeLotHasNoFeeTable() {
		FacilityRow lot = row(parking(), "10000-11191-00167-00-1");

		assertThat(lot.detail()).containsEntry("free", true).containsEntry("fee", null);
		assertThat(map(map(lot.detail().get("hours")).get("Monday"))).containsEntry("open", "09:00")
				.containsEntry("close", "24:00");
	}

	@Test
	@DisplayName("요금도 시간도 모르는 역 앞 주차장(feeKnown=false, 시작·끝이 null)은 둘 다 null 이다")
	void unknownFeeAndHours() {
		FacilityReader.Result result = parking();
		FacilityRow lot = row(result, "46204-99999-00004-00-1");

		assertThat(lot.detail()).containsEntry("free", false).containsEntry("fee", null).containsEntry("hours", null)
				.containsEntry("spaces", 394);
		assertThat(result.nulled()).containsKey("fee.요금 모름");
	}

	@Test
	@DisplayName("자정을 넘기는 시간은 다음 날 시각으로 저장한다 — 10:00~02:00 은 26:00, 21:00~07:00 은 31:00, 00:00~24:00 은 그대로")
	void overnightWindowsAreStoredPastMidnight() {
		FacilityReader.Result result = parking();

		assertThat(map(map(row(result, "48280-11191-00001-00-1").detail().get("hours")).get("Monday")))
				.containsEntry("open", "10:00").containsEntry("close", "26:00");
		FacilityRow night = row(result, "48752-11191-00005-00-1");
		assertThat(night.detail()).containsEntry("free", true);
		assertThat(map(map(night.detail().get("hours")).get("Monday"))).containsEntry("open", "21:00")
				.containsEntry("close", "31:00");
		assertThat(map(map(row(result, "10000-11191-00971-00-1").detail().get("hours")).get("Monday")))
				.containsEntry("open", "00:00").containsEntry("close", "24:00");
		// 00:00~23:00 은 글자 그대로다 — 24시간이라고 지레짐작하지 않는다
		assertThat(map(map(row(result, "10000-11191-00691-00-1").detail().get("hours")).get("Monday")))
				.containsEntry("open", "00:00").containsEntry("close", "23:00");
	}

	@Test
	@DisplayName("민영 주차장도 읽는다 — 기본요금 1,000원에 추가요금이 0원이면 추가요금만 비운다")
	void privateLot() {
		FacilityRow lot = row(parking(), "48045-22291-00001-00-1");

		assertThat(lot.detail()).containsEntry("operator", "PRIVATE");
		assertThat(map(lot.detail().get("fee"))).containsEntry("baseWon", 1000).containsEntry("addWon", null)
				.containsEntry("dayMaxWon", 10000);
	}

	@Test
	@DisplayName("주소가 「-」뿐이면 주소가 없다(null), 번지 자리에 「-」만 있으면 그 끝을 뗀다")
	void dashAddresses() {
		FacilityReader.Result result = parking();

		assertThat(row(result, "46717-11291-00004-00-1").address()).isNull();
		assertThat(row(result, "10000-11191-00881-00-1").address()).isEqualTo("부산광역시 남구 대연동");
	}

	@Test
	@DisplayName("🔴 지하철 환승주차장은 위치를 역 자료의 좌표로 둔다 — 모라역은 3.7km, 남천역은 2.1km 어긋나 있었다")
	void metroParkingIsPlacedAtTheStation() {
		FacilityReader.Result result = parking();

		for (Map.Entry<String, String> each : Map.of("METRO-230-0", "230", "METRO-126-0", "126", "METRO-211-0", "211")
				.entrySet()) {
			FacilityRow lot = row(result, each.getKey());
			Station station = this.stations.byStopId(each.getValue()).orElseThrow();
			assertThat(lot.lat()).as(each.getKey()).isEqualTo(station.lat());
			assertThat(lot.lng()).as(each.getKey()).isEqualTo(station.lng());
		}
		assertThat(result.positionFixed()).isEqualTo(3);
		assertThat(result.farMoved()).isEqualTo(2);
		assertThat(result.maxMovedMeters()).isBetween(3_700, 3_730);
	}

	@Test
	@DisplayName("환승주차장은 요금·시간을 모르고 메모가 있다 — 주소 자리의 「2번 출구」는 위치 설명이라 주소에서 뺀다")
	void metroParkingKeepsNoteAndDropsLocationAddress() {
		FacilityReader.Result result = parking();
		FacilityRow mora = row(result, "METRO-230-0");

		assertThat(mora.name()).isEqualTo("모라역 환승주차장");
		assertThat(mora.address()).isNull();
		assertThat(mora.detail()).containsEntry("note", "2번 출구 · 부산시설공단 · T.860-7701")
				.containsEntry("fee", null).containsEntry("hours", null).containsEntry("free", false)
				.containsEntry("spaces", 125);
		assertThat(row(result, "METRO-211-0").address()).isNull();
		assertThat(row(result, "METRO-211-0").detail()).containsEntry("note", "해변시장 · 수영구청 · T.610-4552");
		// 일반 주차장은 메모가 없다
		assertThat(row(result, "10000-11191-00072-00-1").detail()).containsEntry("note", null);
	}

	// ── 약국 ────────────────────────────────────────────────────────────────

	private FacilityReader.Result pharmacy() {
		return FacilityReader.readPharmacy(SAMPLES.resolve("pharmacy-sample.ndjson"));
	}

	@Test
	@DisplayName("약국 표본 열한 줄이 전부 들어간다")
	void pharmacySampleReadsEveryLine() {
		FacilityReader.Result result = pharmacy();

		assertThat(result.kind()).isEqualTo(FacilityKind.PHARMACY);
		assertThat(result.rows()).hasSize(11);
		assertThat(result.skipped()).isEmpty();
	}

	@Test
	@DisplayName("약국은 쉬는 날의 칸을 적지 않는다 — 월~금만 있으면 토·일·공휴일 키가 없다(쉰다)")
	void pharmacyWeekdaysOnly() {
		FacilityRow row = row(pharmacy(), "C1200619");

		assertThat(row.name()).isEqualTo("123약국");
		assertThat(row.detail()).containsEntry("tel", "051-241-0123").containsEntry("directions", "1호선 토성역 8번출구")
				.containsEntry("note", null);
		assertThat(map(row.detail().get("hours"))).containsOnlyKeys("Monday", "Tuesday", "Wednesday", "Thursday",
				"Friday");
	}

	@Test
	@DisplayName("일요일·공휴일에 여는 약국은 그 칸이 있다")
	void pharmacyOpenOnSundayAndHoliday() {
		Map<String, Object> hours = map(row(pharmacy(), "C1200900").detail().get("hours"));

		assertThat(map(hours.get("Sunday"))).containsEntry("open", "15:00").containsEntry("close", "20:00");
		assertThat(map(hours.get("Holiday"))).containsEntry("open", "15:00").containsEntry("close", "20:00");
	}

	@Test
	@DisplayName("🔴 자정을 넘기는 약국 시간은 25:00 으로 저장한다 — 글자 그대로 다음 날 새벽 1시")
	void pharmacyPastMidnight() {
		FacilityRow row = row(pharmacy(), "C1202436");

		assertThat(map(map(row.detail().get("hours")).get("Sunday"))).containsEntry("open", "22:00")
				.containsEntry("close", "25:00");
	}

	@Test
	@DisplayName("시간 칸이 아예 비어 있는 약국(13곳)은 hours 가 null — 닫았다고 하지 않고 모른다")
	void pharmacyWithoutHours() {
		FacilityReader.Result result = pharmacy();

		assertThat(row(result, "C1203057").detail()).containsEntry("hours", null);
		assertThat(result.nulled()).containsKey("hours.시간대가 하나도 없음");
	}

	@Test
	@DisplayName("🔴 끝이 시작보다 빠른 토요일 시간(19:00~14:30)은 틀린 값이라 그날만 «모른다» — 나머지 요일은 남는다")
	void reversedSaturdayIsUnknown() {
		FacilityReader.Result result = pharmacy();
		Map<String, Object> hours = map(row(result, "C1200681").detail().get("hours"));

		assertThat(hours).containsKey("Monday").containsEntry("Saturday", null);
		assertThat(result.nulled()).containsEntry("hours.REVERSED", 1);
	}

	@Test
	@DisplayName("🔴 1분짜리 시간대(00:00~00:01)는 찌꺼기다 — 하나뿐이었으니 hours 통째로 null")
	void oneMinuteWindowIsDropped() {
		FacilityReader.Result result = pharmacy();

		assertThat(row(result, "C1202740").detail()).containsEntry("hours", null);
		assertThat(result.nulled()).containsEntry("hours.TOO_SHORT", 1);
	}

	@Test
	@DisplayName("메모의 글자 코드(&#xD; &lt; &gt;)와 줄바꿈을 풀어 한 줄로, 「.」뿐인 메모는 비운다")
	void notesAreCleaned() {
		FacilityReader.Result result = pharmacy();

		assertThat((String) row(result, "C1202532").detail().get("note"))
				.isEqualTo("매주 일요일과 명절 당일은 휴무입니다. 공휴일이 평일이면 근무하지만, 공휴일이 일요일일 경우 휴업하오니 참고바랍니다.");
		String lateNote = (String) row(result, "C1202436").detail().get("note");
		assertThat(lateNote).contains("<4.11(토), 4.24(금) 휴무>").doesNotContain("&lt;").doesNotContain("\n");
		assertThat(row(result, "C1203103").detail()).containsEntry("note", null);
	}

	@Test
	@DisplayName("전화번호는 원문 그대로 둔다(점으로 적힌 것도) — 걸 때 숫자만 남기는 것은 화면의 일이다. 찾아가는 길 끝의 공백은 뗀다")
	void telAndDirectionsStayAsWritten() {
		FacilityReader.Result result = pharmacy();

		assertThat(row(result, "C1202490").detail()).containsEntry("tel", "051.611.1898");
		assertThat(row(result, "C1202304").detail()).containsEntry("directions", "수영역 7번 출구 앞");
	}

	// ── 공중화장실 ──────────────────────────────────────────────────────────

	private FacilityReader.Result toilet() {
		return FacilityReader.readToilet(SAMPLES.resolve("toilet-sample.ndjson"));
	}

	@Test
	@DisplayName("🔴 주소가 「부산광역시 연제구」뿐인 화장실은 좌표가 구 중심점이라 넣지 않는다 — 열네 줄 가운데 열세 곳이 들어간다")
	void districtOnlyAddressIsSkipped() {
		FacilityReader.Result result = toilet();

		assertThat(result.linesRead()).isEqualTo(14);
		assertThat(result.rows()).hasSize(13);
		assertThat(result.skipped()).hasSize(1);
		assertThat(result.skippedCount(Reason.COARSE_ADDRESS)).isEqualTo(1);
		FacilityReader.Skipped skipped = result.skipped().get(0);
		assertThat(skipped.sourceId()).isEqualTo("202433700000100351");
		assertThat(skipped.name()).isEqualTo("이사벨고등학교");
		assertThat(result.rows()).noneMatch(each -> each.sourceId().equals("202433700000100351"));
	}

	@Test
	@DisplayName("분류말 「정시」는 떼고 시간만 남긴다 — 「정시 10:00-20:00」→「10:00-20:00」, 「정시 정시」는 내용이 없다")
	void openHoursTextIsCleaned() {
		FacilityReader.Result result = toilet();

		assertThat(row(result, "202433400000100010").detail()).containsEntry("openHours", "10:00-20:00")
				.containsEntry("alwaysOpen", false).containsEntry("type", "OPEN").containsEntry("accessible", true)
				.containsEntry("diaper", false).containsEntry("diaperPlace", null).containsEntry("tel", "051-220-4836");
		assertThat(row(result, "202633600000100001").detail()).containsEntry("openHours", "09:00~18:00");
		assertThat(row(result, "202433600000100150").detail()).containsEntry("openHours", null)
				.containsEntry("alwaysOpen", false);
		assertThat(result.nulled()).containsEntry("openHours.내용 없음", 1);
		// 「정시 정시(09:00∼18:00)」 — 괄호를 벗기고 다른 물결표(∼)를 ~ 로 맞춘다
		assertThat(row(result, "202434000000100004").detail()).containsEntry("openHours", "09:00~18:00");
		// 「불규칙 …」은 번역하지 않고 그대로 둔다
		assertThat(row(result, "202332700000100143").detail()).containsEntry("openHours", "불규칙 공연시");
		assertThat(row(result, "202433000000100266").detail()).containsEntry("openHours", "불규칙 09:00~18:00");
	}

	@Test
	@DisplayName("늘 여는 곳(상시·24시간·00:00~24:00)은 alwaysOpen 으로 표시한다 — 화면이 언어별 글로 바꿔 보여 줄 수 있게")
	void alwaysOpenIsFlagged() {
		FacilityReader.Result result = toilet();

		assertThat(row(result, "202532600000100352").detail()).containsEntry("openHours", "상시")
				.containsEntry("alwaysOpen", true);
		assertThat(row(result, "202432900000101309").detail()).containsEntry("openHours", "24시간")
				.containsEntry("alwaysOpen", true);
		assertThat(row(result, "202433000000100219").detail()).containsEntry("openHours", "00:00~24:00")
				.containsEntry("alwaysOpen", true);
		assertThat(row(result, "202633600000100009").detail()).containsEntry("alwaysOpen", true);
	}

	@Test
	@DisplayName("기저귀 교환대 위치는 코드로 바꾼다 — 여자 WOMEN · 남자 MEN · 둘 다 BOTH(적힌 순서와 상관없이)")
	void diaperPlaceCodes() {
		FacilityReader.Result result = toilet();

		assertThat(row(result, "202532600000100352").detail()).containsEntry("diaper", true)
				.containsEntry("diaperPlace", "WOMEN");
		assertThat(row(result, "202633400000100021").detail()).containsEntry("diaperPlace", "BOTH");
		assertThat(row(result, "202633600000100001").detail()).containsEntry("diaperPlace", "BOTH");
		assertThat(row(result, "202433000000100266").detail()).containsEntry("diaperPlace", "MEN");
	}

	@Test
	@DisplayName("교환대가 없다면서 위치가 적힌 줄은 서로 안 맞으니 위치를 비운다 — 교환대 없음이 우선이다")
	void diaperPlaceContradictionIsDropped() {
		FacilityReader.Result result = toilet();

		assertThat(row(result, "202433000000100219").detail()).containsEntry("diaper", false)
				.containsEntry("diaperPlace", null);
		assertThat(result.nulled()).containsEntry("diaperPlace.교환대 없음과 모순", 1);
	}

	@Test
	@DisplayName("화장실 종류는 코드로 — 개방 OPEN · 공중 PUBLIC · 간이 SIMPLE · 이동 MOBILE")
	void toiletTypes() {
		FacilityReader.Result result = toilet();

		assertThat(row(result, "202532600000100335").detail()).containsEntry("type", "SIMPLE");
		assertThat(row(result, "202633600000100009").detail()).containsEntry("type", "MOBILE");
		assertThat(row(result, "202633400000100021").detail()).containsEntry("type", "PUBLIC");
		assertThat(row(result, "202633600000100001").detail()).containsEntry("type", "OPEN");
		// 전화번호가 없으면 null
		assertThat(row(result, "202633100000100005").detail()).containsEntry("tel", null);
	}

	// ── 물품보관함 ──────────────────────────────────────────────────────────

	private FacilityReader.Result locker() {
		return FacilityReader.readLocker(SAMPLES.resolve("locker-sample.ndjson"), this.stations);
	}

	@Test
	@DisplayName("🔴 물품보관함의 위치는 전부 역 자료의 좌표로 바뀐다 — 호선이 다른 서면 둘은 다른 역이고, 벡스코·국제금융센터도 찾는다")
	void lockerPositionsComeFromTheStationTable() {
		FacilityReader.Result result = locker();

		assertThat(result.rows()).hasSize(9);
		assertThat(result.skipped()).isEmpty();
		Map<String, String> stopIds = Map.of("BTC-1-다대포해수욕장-1", "95", "BTC-1-하단-3", "102", "BTC-1-서면-14", "119",
				"BTC-2-서면-40", "219", "BTC-2-벡스코-29", "205", "BTC-2-국제금융센터-38", "217", "BTC-2-남양산-49", "242",
				"BTC-3-수영-51", "301", "BTC-4-동래-66", "402");
		stopIds.forEach((sourceId, stopId) -> {
			Station station = this.stations.byStopId(stopId).orElseThrow();
			FacilityRow row = row(result, sourceId);
			assertThat(row.lat()).as(sourceId).isEqualTo(station.lat());
			assertThat(row.lng()).as(sourceId).isEqualTo(station.lng());
		});
		assertThat(result.positionFixed()).isEqualTo(9);
		// 다대포해수욕장은 파일 좌표가 6km 어긋나 있었다
		assertThat(result.maxMovedMeters()).isBetween(6_000, 6_100);
		assertThat(result.farMoved()).isGreaterThanOrEqualTo(4);
	}

	@Test
	@DisplayName("물품보관함 한 곳은 역 이름 + 칸 수 + 요금 글 + 위치 글이다 — 이름은 「역」을 붙이고 주소는 비운다")
	void lockerFields() {
		FacilityRow row = row(locker(), "BTC-1-다대포해수욕장-1");

		assertThat(row.name()).isEqualTo("다대포해수욕장역");
		assertThat(row.address()).isNull();
		assertThat(row.detail()).containsEntry("station", "다대포해수욕장").containsEntry("stationEn", "Dadaepo Beach")
				.containsEntry("line", "1").containsEntry("location", "(B1) 대합실 고객센터 근처")
				.containsEntry("small", 3).containsEntry("medium", 8).containsEntry("large", 0)
				.containsEntry("xlarge", 7).containsEntry("operator", "위드락커");
		assertThat((String) row.detail().get("feeText")).startsWith("특대: 6,000원");
	}

	@Test
	@DisplayName("끝에 \\r 이 붙은 운영사 이름과 겹공백이 든 위치 글은 다듬는다")
	void lockerTextsAreTrimmed() {
		FacilityRow row = row(locker(), "BTC-2-남양산-49");

		assertThat(row.detail()).containsEntry("operator", "㈜테크앤롤").containsEntry("location", "(2F) 3번 출입구 방향");
	}

	@Test
	@DisplayName("🔴 역 자료에서 역을 못 찾으면 그 줄은 넣지 않는다 — 파일의 좌표는 믿을 수 없다")
	void unknownStationIsSkipped() throws IOException {
		Path file = write("locker.ndjson", """
				{"sourceId":"BTC-1-없는역-1","station":"없는역","stationEn":"Nowhere","line":"1","location":"(B1)","lat":35.1,"lng":129.0,"small":1,"medium":1,"large":1,"xlarge":1,"feeText":"x","operator":"y"}
				""");

		FacilityReader.Result result = FacilityReader.readLocker(file, this.stations);

		assertThat(result.rows()).isEmpty();
		assertThat(result.skippedCount(Reason.STATION_NOT_FOUND)).isEqualTo(1);
	}

	@Test
	@DisplayName("역 자료 없이는 물품보관함을 읽지 않는다")
	void lockerNeedsTheStationTable() {
		assertThatThrownBy(() -> FacilityReader.readLocker(SAMPLES.resolve("locker-sample.ndjson"), null))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("역 자료");
	}

	// ── 틀린 파일은 멈춘다 ──────────────────────────────────────────────────

	private static final String VALID_PARKING = """
			{"sourceId":"T1","name":"시험주차장","address":"부산광역시 중구 중앙동 1","lat":35.1,"lng":129.0,"operator":"PUBLIC","free":false,"feeKnown":false,"spaces":10,"fee":null,"hours":null}""";

	private Path write(String name, String content) throws IOException {
		return Files.writeString(this.tmp.resolve(name), content, StandardCharsets.UTF_8);
	}

	@Test
	@DisplayName("🔴 모르는 칸이 있으면 줄 번호와 함께 멈춘다 — 오타 난 칸을 조용히 버리지 않는다")
	void unknownFieldStops() throws IOException {
		Path file = write("p.ndjson", VALID_PARKING + "\n" + VALID_PARKING.replace("\"T1\"", "\"T2\"")
				.replace("\"hours\":null", "\"hours\":null,\"realtime\":false"));

		assertThatThrownBy(() -> FacilityReader.readParking(file, this.stations))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2번째 줄")
				.hasMessageContaining("모르는 칸이 있다: realtime");
	}

	@Test
	@DisplayName("같은 sourceId 가 또 나오면 멈춘다 — 종류 안에서 번호 하나는 한 곳이다")
	void duplicateSourceIdStops() throws IOException {
		Path file = write("p.ndjson", VALID_PARKING + "\n" + VALID_PARKING);

		assertThatThrownBy(() -> FacilityReader.readParking(file, this.stations))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2번째 줄")
				.hasMessageContaining("같은 sourceId");
	}

	@Test
	@DisplayName("좌표가 부산 밖이면 멈춘다 — 위도와 경도를 뒤바꾼 줄을 여기서 잡는다")
	void coordinatesOutsideBusanStop() throws IOException {
		Path file = write("p.ndjson", VALID_PARKING.replace("\"lat\":35.1,\"lng\":129.0", "\"lat\":129.0,\"lng\":35.1"));

		assertThatThrownBy(() -> FacilityReader.readParking(file, this.stations))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("부산 밖");
	}

	@Test
	@DisplayName("JSON 이 깨졌거나 칸의 종류가 틀리면 줄 번호와 함께 멈춘다")
	void malformedLinesStop() throws IOException {
		Path broken = write("broken.ndjson", "{not json");
		assertThatThrownBy(() -> FacilityReader.readParking(broken, this.stations))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1번째 줄");

		Path wrongType = write("wrong.ndjson", VALID_PARKING.replace("\"free\":false", "\"free\":\"false\""));
		assertThatThrownBy(() -> FacilityReader.readParking(wrongType, this.stations))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("free 는 true 나 false");

		Path wrongOperator = write("op.ndjson", VALID_PARKING.replace("\"PUBLIC\"", "\"CITY\""));
		assertThatThrownBy(() -> FacilityReader.readParking(wrongOperator, this.stations))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("operator");

		Path noFeeKnown = write("nofk.ndjson", VALID_PARKING.replace("\"feeKnown\":false,", ""));
		assertThatThrownBy(() -> FacilityReader.readParking(noFeeKnown, this.stations))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("feeKnown");

		Path notObject = write("array.ndjson", "[1,2,3]");
		assertThatThrownBy(() -> FacilityReader.readParking(notObject, this.stations))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JSON 객체가 아니다");
	}

	@Test
	@DisplayName("시각 모양이 깨졌거나 모르는 요일 칸이 있으면 멈춘다")
	void brokenHoursStop() throws IOException {
		String withHours = VALID_PARKING.replace("\"hours\":null",
				"\"hours\":{\"Monday\":{\"open\":\"9시\",\"close\":\"18:00\"}}");
		assertThatThrownBy(() -> FacilityReader.readParking(write("h1.ndjson", withHours), this.stations))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HH:MM");

		String withDay = VALID_PARKING.replace("\"hours\":null",
				"\"hours\":{\"Funday\":{\"open\":\"09:00\",\"close\":\"18:00\"}}");
		assertThatThrownBy(() -> FacilityReader.readParking(write("h2.ndjson", withDay), this.stations))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("모르는 요일");
	}

	@Test
	@DisplayName("모르는 화장실 종류나 기저귀 교환대 위치가 오면 멈춘다 — 새 낱말을 지레짐작하지 않는다")
	void unknownToiletWordsStop() throws IOException {
		String line = "{\"sourceId\":\"T1\",\"name\":\"시험\",\"address\":\"부산광역시 중구 1\",\"lat\":35.1,\"lng\":129.0,"
				+ "\"kind\":\"공중화장실\",\"openHours\":\"상시\",\"accessible\":false,\"diaper\":true,"
				+ "\"diaperPlace\":\"여자화장실\",\"emergencyBell\":false,\"cctv\":false,\"tel\":null}";

		assertThatThrownBy(() -> FacilityReader.readToilet(write("t1.ndjson", line.replace("공중화장실", "공원화장실"))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("모르는 화장실 종류");
		assertThatThrownBy(() -> FacilityReader.readToilet(write("t2.ndjson", line.replace("\"여자화장실\"", "\"가족화장실\""))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("diaperPlace");
	}

	@Test
	@DisplayName("빈 줄은 건너뛰고 읽은 줄 수에 안 센다. 첫머리의 BOM 은 무시한다")
	void blankLinesAndBomAreTolerated() throws IOException {
		Path file = write("p.ndjson", "﻿" + VALID_PARKING + "\n\n   \n" + VALID_PARKING.replace("\"T1\"", "\"T2\"") + "\n");

		FacilityReader.Result result = FacilityReader.readParking(file, this.stations);

		assertThat(result.linesRead()).isEqualTo(2);
		assertThat(result.rows()).hasSize(2);
	}

	@Test
	@DisplayName("환승주차장의 역 번호와 이름이 안 맞으면 파일의 좌표를 그대로 두고 센다 — 억지로 맞추지 않는다")
	void metroLotWithMismatchedStationKeepsFileCoordinates() throws IOException {
		String line = "{\"sourceId\":\"METRO-102-0\",\"name\":\"전혀다른역 환승주차장\",\"address\":\"1번 출구\",\"lat\":35.2,\"lng\":129.1,"
				+ "\"operator\":\"PUBLIC\",\"free\":false,\"feeKnown\":false,\"spaces\":10,\"fee\":null,\"hours\":null,"
				+ "\"note\":\"1번 출구 · 시험\"}";

		FacilityReader.Result result = FacilityReader.readParking(write("m.ndjson", line), this.stations);

		FacilityRow lot = result.rows().get(0);
		assertThat(lot.lat()).isEqualTo(35.2);
		assertThat(lot.lng()).isEqualTo(129.1);
		assertThat(result.positionFixed()).isZero();
		assertThat(result.nulled()).containsKey("position.역을 못 찾아 파일 좌표 그대로");
	}

	// ── 글 다듬기 ───────────────────────────────────────────────────────────

	@Test
	@DisplayName("글자 코드를 풀고 공백을 줄인다 — 모르는 코드는 그대로 둔다")
	void textCleaning() {
		assertThat(FacilityText.clean("  a&amp;b &#65;&#x42; &unknown;  c\r\nd ")).isEqualTo("a&b AB &unknown; c d");
		assertThat(FacilityText.clean("   ")).isNull();
		assertThat(FacilityText.clean(null)).isNull();
		assertThat(FacilityText.cleanNote("--", 100)).isNull();
		assertThat(FacilityText.cleanNote("가 나", 100)).isEqualTo("가 나");
		assertThat(FacilityText.cleanNote("가나다라마", 3)).isEqualTo("가나다");
		assertThat(FacilityText.cleanAddress("-", 100)).isNull();
		assertThat(FacilityText.cleanAddress("부산광역시 남구 대연동  -", 100)).isEqualTo("부산광역시 남구 대연동");
		assertThat(FacilityText.cleanAddress("부산광역시 서구 암남동 193-4", 100)).isEqualTo("부산광역시 서구 암남동 193-4");
	}
}
