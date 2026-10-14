package com.gabolle.backend.facility.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 주차장 예상요금 (S15P21E201-2052) — 경계값: 무료 시간, 기본 시간의 끝, 올림, 하루 최대, 모르는 칸.
 * 요금표는 실제 파일의 대표 한 벌(기본 30분 300원 · 10분마다 100원 · 하루 최대 2,400원)이다.
 */
class ParkingFeeTest {

	private static ParkingFee typical() {
		return new ParkingFee(30, 300, 10, 100, 2400, 0);
	}

	@Test
	@DisplayName("기본 시간까지는 기본요금이다 — 30분은 300원, 1분을 넘기면 한 단위(10분)가 더 붙는다")
	void baseMinutesBoundary() {
		assertThat(typical().estimate(1)).isEqualTo(300);
		assertThat(typical().estimate(30)).isEqualTo(300);
		assertThat(typical().estimate(31)).isEqualTo(400);
		assertThat(typical().estimate(40)).isEqualTo(400);
		assertThat(typical().estimate(41)).isEqualTo(500);
	}

	@Test
	@DisplayName("남는 시간은 한 단위로 올린다 — 1시간은 300 + 3단위, 2시간은 300 + 9단위")
	void remainderRoundsUp() {
		assertThat(typical().estimate(60)).isEqualTo(600);
		assertThat(typical().estimate(120)).isEqualTo(1200);
	}

	@Test
	@DisplayName("🔴 하루 최대로 자른다 — 4시간은 정확히 2,400원이고 5시간도 2,400원이다")
	void dayMaxCapsTheTotal() {
		assertThat(typical().estimate(240)).isEqualTo(2400);
		assertThat(typical().estimate(300)).isEqualTo(2400);
		// 하루 최대가 없으면 자르지 않는다
		assertThat(new ParkingFee(30, 300, 10, 100, null, 0).estimate(300)).isEqualTo(3000);
	}

	@Test
	@DisplayName("무료 시간은 먼저 뺀다 — 처음 10분은 무료, 그 뒤부터 센다")
	void freeMinutesAreSubtractedFirst() {
		ParkingFee fee = new ParkingFee(30, 300, 10, 100, 2400, 10);

		assertThat(fee.estimate(5)).isZero();
		assertThat(fee.estimate(10)).isZero();
		assertThat(fee.estimate(11)).isEqualTo(300);
		assertThat(fee.estimate(40)).isEqualTo(300);
		assertThat(fee.estimate(50)).isEqualTo(400);
	}

	@Test
	@DisplayName("0분 이하는 0원이다. 하루보다 긴 체류는 하루로 센다")
	void zeroAndVeryLongStays() {
		assertThat(typical().estimate(0)).isZero();
		assertThat(typical().estimate(-5)).isZero();
		assertThat(typical().estimate(100_000)).isEqualTo(typical().estimate(ParkingFee.MAX_STAY_MINUTES));
	}

	@Test
	@DisplayName("🔴 추가요금을 모르면 기본 시간을 넘는 체류는 모른다(null) — 기본 시간 안이면 안다")
	void unknownAddWonMeansUnknownBeyondBaseMinutes() {
		ParkingFee baseOnly = new ParkingFee(30, 300, 10, null, 2400, 0);

		assertThat(baseOnly.estimate(30)).isEqualTo(300);
		assertThat(baseOnly.estimate(31)).isNull();
		assertThat(baseOnly.estimate(120)).isNull();
		// 추가 단위 시간을 몰라도 같다
		assertThat(new ParkingFee(30, 300, null, 100, null, 0).estimate(60)).isNull();
	}

	@Test
	@DisplayName("기본요금이나 기본 시간을 모르면 어떤 체류도 모른다")
	void unknownBaseIsAlwaysUnknown() {
		assertThat(new ParkingFee(30, null, 10, 100, 2400, 0).estimate(10)).isNull();
		assertThat(new ParkingFee(null, 300, 10, 100, 2400, 0).estimate(10)).isNull();
	}

	// ── 이상한 값 다듬기 ────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 정수 최댓값(2147483647)과 4만 원은 요금표를 쓸 수 없다 — 0원으로 읽지 않고 비운다")
	void implausibleBaseWonDropsTheWholeTable() {
		ParkingFee.Normalized sentinel = ParkingFee.normalize(30, 2147483647, 10, 0, null, 0);
		assertThat(sentinel.fee()).isNull();
		assertThat(sentinel.notes()).containsExactly(ParkingFee.NOTE_BASE_WON_IMPLAUSIBLE);

		ParkingFee.Normalized monthly = ParkingFee.normalize(30, 40000, 10, 0, null, 0);
		assertThat(monthly.fee()).isNull();
		assertThat(monthly.notes()).containsExactly(ParkingFee.NOTE_BASE_WON_IMPLAUSIBLE);
	}

	@Test
	@DisplayName("🔴 유료인데 기본요금이 0원이면 값이 빠진 것이다 — 요금표를 비운다(0원 주차장으로 만들지 않는다)")
	void zeroBaseWonIsMissingNotFree() {
		ParkingFee.Normalized zero = ParkingFee.normalize(30, 0, 10, 0, null, 0);

		assertThat(zero.fee()).isNull();
		assertThat(zero.notes()).containsExactly(ParkingFee.NOTE_BASE_WON_ZERO);
	}

	@Test
	@DisplayName("기본요금이 없거나 기본 시간이 이상하면 요금표를 쓸 수 없다")
	void missingBaseDropsTheTable() {
		assertThat(ParkingFee.normalize(30, null, 10, 100, 2400, 0).fee()).isNull();
		assertThat(ParkingFee.normalize(null, 300, 10, 100, 2400, 0).fee()).isNull();
		assertThat(ParkingFee.normalize(0, 300, 10, 100, 2400, 0).fee()).isNull();
		assertThat(ParkingFee.normalize(30, null, 10, 100, 2400, 0).notes()).containsExactly(ParkingFee.NOTE_BASE_MISSING);
	}

	@Test
	@DisplayName("추가요금 0원은 그 칸만 비운다 — 기본요금까지는 아는 것이다")
	void zeroAddWonOnlyDropsThatField() {
		ParkingFee.Normalized baseOnly = ParkingFee.normalize(30, 300, 10, 0, 2400, 0);

		assertThat(baseOnly.fee()).isEqualTo(new ParkingFee(30, 300, 10, null, 2400, 0));
		assertThat(baseOnly.notes()).containsExactly(ParkingFee.NOTE_ADD_WON_ZERO);
		assertThat(baseOnly.fee().estimate(30)).isEqualTo(300);
		assertThat(baseOnly.fee().estimate(60)).isNull();
	}

	@Test
	@DisplayName("하루 최대가 기본요금보다 작으면 말이 안 되니 그 칸만 비운다. 멀쩡한 값은 그대로 둔다")
	void dayMaxBelowBaseIsDropped() {
		ParkingFee.Normalized broken = ParkingFee.normalize(30, 900, 10, 300, 100, 0);
		assertThat(broken.fee().dayMaxWon()).isNull();
		assertThat(broken.notes()).containsExactly(ParkingFee.NOTE_DAY_MAX_IMPLAUSIBLE);

		ParkingFee.Normalized fine = ParkingFee.normalize(30, 900, 10, 300, 8000, 0);
		assertThat(fine.fee()).isEqualTo(new ParkingFee(30, 900, 10, 300, 8000, 0));
		assertThat(fine.notes()).isEmpty();
	}

	// ── 저장 모양 ───────────────────────────────────────────────────────────

	@Test
	@DisplayName("저장 모양으로 내보냈다 읽어도 같다 — 모르는 칸은 null 로 남는다")
	void storedFormRoundTrips() {
		ParkingFee partly = new ParkingFee(30, 300, 10, null, null, 0);

		Map<String, Object> stored = partly.toDetail();
		assertThat(stored).containsEntry("baseWon", 300).containsEntry("addWon", null).containsEntry("dayMaxWon", null);
		assertThat(ParkingFee.fromDetail(stored)).isEqualTo(partly);
	}

	@Test
	@DisplayName("저장된 값이 객체가 아니거나 기본요금이 없으면 요금표가 없다(null)")
	void brokenStoredFeeIsNull() {
		assertThat(ParkingFee.fromDetail(null)).isNull();
		assertThat(ParkingFee.fromDetail("300")).isNull();
		Map<String, Object> noBase = new LinkedHashMap<>();
		noBase.put("baseMinutes", 30);
		assertThat(ParkingFee.fromDetail(noBase)).isNull();
	}
}
