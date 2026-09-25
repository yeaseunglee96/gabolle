package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.gabolle.backend.place.service.OpeningHoursFilterPort;
import com.gabolle.backend.place.service.PlaceTimeFactFilterPort;
import com.gabolle.backend.place.service.PlaceTimeTablePort;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

/**
 * 영업표가 시각마다 묻던 두 문과 모든 (장소, 날, 15분)에서 같은 답을 내는가 — 격자 검사 (S15P21E201-1663).
 *
 * <p>🔴 왜 일정 비교만으로 모자라나. 조립기는 걸리는 곳을 피하므로 브레이크타임·라스트오더 경고는 완성된 일정에 거의
 * 안 남는다. 일정이 같다는 것은 「그 일정에 쓰인 답이 같다」까지만 말한다. 그래서 답 셋을 격자 전부에서 따로 견준다.
 *
 * <p>장소는 값 모양마다 하나씩이다 — 답 셋은 저마다 제 갈래의 행 하나에만 달려 있어, 갈래끼리 조합을 늘려도 새로 보는
 * 것이 없다. 시각마다 묻던 쪽은 물을 때마다 DB 를 읽으므로(이 격자에서 2만 번) 한 트랜잭션에 묶어 시간을 줄인다.
 */
class PlaceFeatureTimeTableIntegrationTest extends PlacePostgresIntegrationTest {

	/** 영업시간 값 모양. {@code null} 은 행이 없는 것, {@link #NULL_VALUE} 는 행은 있는데 값이 빈 것이다. */
	private static final String NULL_VALUE = "(null)";

	private static final String[] OPENING = {
			"{\"status\": \"ALWAYS_OPEN\", \"closedDays\": [], \"notes\": [], \"raw\": {}}",
			byDay("[[\"11:00\", \"15:00\"]]", "[[\"11:00\", \"15:00\"]]"),
			byDay("[[\"10:00\", \"18:00\"]]", "[]"),
			byDay("[[\"17:00\", \"23:00\"]]", "[[\"17:00\", \"23:00\"]]"),
			byDay("[[\"22:00\", \"02:00\"]]", "[[\"22:00\", \"02:00\"]]"),
			"{\"status\": \"PARSED\", \"byDay\": \"깨진 값\"}",
			NULL_VALUE,
			null };

	private static final String[] BREAK_TIME = {
			"{\"start\": \"15:00\", \"end\": \"17:00\"}",
			"{\"start\": \"23:00\", \"end\": \"01:00\"}",
			NULL_VALUE,
			null };

	private static final String[] LAST_ORDER = {
			"{\"time\": \"20:00\"}",
			"{\"time\": \"00:30\"}",
			null };

	/** 값이 하나도 없는 장소까지 — 모든 모양이 적어도 한 번 나온다. */
	private static final int PLACES = OPENING.length + 1;

	@Autowired
	private OpeningHoursFilterPort openingHours;

	@Autowired
	private PlaceTimeFactFilterPort timeFact;

	@Autowired
	private PlaceTimeTablePort timeTables;

	@Autowired
	private TransactionTemplate transactions;

	@Autowired
	private JdbcTemplate jdbc;

	private final List<UUID> places = new ArrayList<>();

	@BeforeEach
	void seed() {
		for (int i = 0; i < PLACES; i++) {
			UUID placeId = new UUID(0x1663_0000L, i);
			this.jdbc.update("INSERT INTO place (place_id, name_ko, category, lat, lng, created_at) "
					+ "VALUES (?, ?, 'FOOD', 35.1, 129.0, now())", placeId, "영업표격자 " + i);
			this.places.add(placeId);
			if (i == PLACES - 1) {
				continue;
			}
			feature(placeId, "OPENING_HOURS", OPENING[i % OPENING.length]);
			feature(placeId, "BREAK_TIME", BREAK_TIME[i % BREAK_TIME.length]);
			feature(placeId, "LAST_ORDER_TIME", LAST_ORDER[i % LAST_ORDER.length]);
		}
	}

	@AfterEach
	void cleanUp() {
		for (UUID placeId : this.places) {
			this.jdbc.update("DELETE FROM place_feature WHERE place_id = ?", placeId);
			this.jdbc.update("DELETE FROM place WHERE place_id = ?", placeId);
		}
	}

	@Test
	@DisplayName("🔴 장소×8일×15분 격자 전부에서 영업시간·브레이크타임·라스트오더 답이 시각마다 묻던 문과 같다")
	void everyPlaceDayAndQuarterHourAgrees() {
		Map<String, Integer> answers = new TreeMap<>();
		List<String> mismatches = new ArrayList<>();
		this.transactions.executeWithoutResult((status) -> {
			for (UUID placeId : this.places) {
				PlaceTimeTablePort.PlaceTimeTable table = this.timeTables.tableOf(placeId);
				for (OffsetDateTime at = OffsetDateTime.of(2026, 10, 1, 0, 0, 0, 0, ZoneOffset.ofHours(9));
						at.isBefore(OffsetDateTime.of(2026, 10, 9, 0, 0, 0, 0, ZoneOffset.ofHours(9)));
						at = at.plusMinutes(15)) {
					compare("영업시간", placeId, at, this.openingHours.openAt(placeId, at), table.openAt(at), answers,
							mismatches);
					compare("브레이크타임", placeId, at, this.timeFact.breakTimeAt(placeId, at), table.breakTimeAt(at),
							answers, mismatches);
					compare("라스트오더", placeId, at, this.timeFact.lastOrderAt(placeId, at), table.lastOrderAt(at),
							answers, mismatches);
				}
			}
		});

		assertThat(mismatches).isEmpty();
		// 격자가 세 갈래 답을 갈래마다 다 밟았나 — 한 갈래만 나오면 견준 것이 없는 것과 같다.
		for (String check : List.of("영업시간", "브레이크타임", "라스트오더")) {
			for (OpeningHoursFilterPort.Answer answer : OpeningHoursFilterPort.Answer.values()) {
				assertThat(answers).as("%s 이 %s 로 답한 칸", check, answer).containsKey(check + " " + answer);
			}
		}
	}

	@Test
	@DisplayName("장소나 시각이 비어 있으면 두 문처럼 「모른다」")
	void missingPlaceOrTimeIsNotCollected() {
		OffsetDateTime noon = OffsetDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneOffset.ofHours(9));
		PlaceTimeTablePort.PlaceTimeTable none = this.timeTables.tableOf(null);
		assertThat(List.of(none.openAt(noon), none.breakTimeAt(noon), none.lastOrderAt(noon)))
				.containsOnly(OpeningHoursFilterPort.Answer.NOT_COLLECTED);

		PlaceTimeTablePort.PlaceTimeTable first = this.timeTables.tableOf(this.places.get(0));
		assertThat(List.of(first.openAt(null), first.breakTimeAt(null), first.lastOrderAt(null)))
				.containsOnly(OpeningHoursFilterPort.Answer.NOT_COLLECTED);
	}

	private static void compare(String check, UUID placeId, OffsetDateTime at, OpeningHoursFilterPort.Answer before,
			OpeningHoursFilterPort.Answer after, Map<String, Integer> answers, List<String> mismatches) {
		answers.merge(check + " " + before, 1, Integer::sum);
		if (before != after) {
			mismatches.add(check + " " + placeId.getLeastSignificantBits() + " " + at + " 전 " + before + " 후 " + after);
		}
	}

	private void feature(UUID placeId, String type, String value) {
		if (value == null) {
			return;
		}
		this.jdbc.update("""
				INSERT INTO place_feature (place_feature_id, place_id, feature_type, value, evidence_status,
				                           source_type, created_at)
				VALUES (?, ?, ?, ?::jsonb, 'ESTIMATED', 'TEST', now())
				""", UUID.randomUUID(), placeId, type, NULL_VALUE.equals(value) ? null : value);
	}

	private static String byDay(String weekday, String saturday) {
		return "{\"status\": \"PARSED\", \"closedDays\": [], \"notes\": [], \"raw\": {}, \"byDay\": {"
				+ "\"mon\": " + weekday + ", \"tue\": " + weekday + ", \"wed\": " + weekday + ", \"thu\": " + weekday
				+ ", \"fri\": " + weekday + ", \"sat\": " + saturday + ", \"sun\": " + weekday + "}}";
	}
}
