package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.itinerary.application.ItineraryOpeningHoursChecker;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.place.service.OpeningHoursFilterPort;

/**
 * 일정 영업시간 판정 — S15P21E201-858.
 *
 * <p>DB 없이 돈다. 문(포트)을 흉내 내고 판정 결과만 본다 — 이 노트북에는 PostgreSQL 이 없어
 * 통합 검사가 CI 에서 처음 돌고, 여러 날을 합치는 규칙은 그때까지 기다릴 것이 아니다.
 *
 * <p>날짜는 2026-09-07(월)부터 사흘이다.
 */
class ItineraryOpeningHoursCheckerTest {

	private static final String PLACE_OPEN = UUID.randomUUID().toString();

	private static final String PLACE_CLOSED = UUID.randomUUID().toString();

	private static final String PLACE_UNKNOWN = UUID.randomUUID().toString();

	@Test
	@DisplayName("여러 날의 위반을 전부 올리고 못 한 검사는 이유별로 한 번만 올린다")
	void checkAllMergesViolationsAndDedupesReasons() {
		List<ItineraryItem> items = List.of(
				item("d0-closed", 0, PLACE_CLOSED, "09:00"),
				item("d0-unknown", 0, PLACE_UNKNOWN, "11:00"),
				item("d1-closed", 1, PLACE_CLOSED, "10:00"),
				item("d1-unknown", 1, PLACE_UNKNOWN, "12:00"),
				item("d2-open", 2, PLACE_OPEN, "13:00"));

		ItineraryOpeningHoursChecker.Result result = checker().checkAll(items);

		assertThat(result.violations())
				.extracting(ItineraryOpeningHoursChecker.Violation::itemKey)
				.as("두 날의 위반이 모두 올라온다")
				.containsExactly("d0-closed", "d1-closed");
		assertThat(result.notChecked())
				.extracting(ItineraryOpeningHoursChecker.NotChecked::check,
						ItineraryOpeningHoursChecker.NotChecked::reason)
				.as("이유가 같으면 한 줄이다 — 사흘 모두 모른다고 세 줄을 올리면 화면이 같은 문구를 세 번 보여 준다")
				.containsExactly(tuple("OPENING_HOURS", "NOT_COLLECTED"));
	}

	@Test
	@DisplayName("한 날만 볼 때는 다른 날의 위반이 안 섞인다")
	void checkDayLooksAtThatDayOnly() {
		List<ItineraryItem> items = List.of(
				item("d0-closed", 0, PLACE_CLOSED, "09:00"),
				item("d1-closed", 1, PLACE_CLOSED, "10:00"));

		assertThat(checker().checkDay(items, 1).violations())
				.extracting(ItineraryOpeningHoursChecker.Violation::itemKey)
				.containsExactly("d1-closed");
	}

	@Test
	@DisplayName("전부 여는 시각이면 위반도 못 한 검사도 없다")
	void everythingOpenGivesEmptyResult() {
		ItineraryOpeningHoursChecker.Result result = checker().checkAll(List.of(
				item("d0", 0, PLACE_OPEN, "09:00"),
				item("d1", 1, PLACE_OPEN, "10:00")));

		assertThat(result.violations()).isEmpty();
		assertThat(result.notChecked()).isEmpty();
	}

	@Test
	@DisplayName("방문 시각이 없는 항목과 모르는 장소가 섞이면 이유가 둘 다 올라온다")
	void bothReasonsSurvive() {
		ItineraryOpeningHoursChecker.Result result = checker().checkAll(List.of(
				item("no-time", 0, PLACE_OPEN, null),
				item("unknown", 1, PLACE_UNKNOWN, "10:00")));

		assertThat(result.notChecked())
				.extracting(ItineraryOpeningHoursChecker.NotChecked::reason)
				.containsExactlyInAnyOrder("NO_ITEM_TIME", "NOT_COLLECTED");
	}

	@Test
	@DisplayName("항목이 없으면 빈 결과다 — 날짜를 하나도 안 돌린다")
	void emptyItineraryGivesEmptyResult() {
		ItineraryOpeningHoursChecker.Result result = checker().checkAll(List.of());

		assertThat(result.violations()).isEmpty();
		assertThat(result.notChecked()).isEmpty();
	}

	private static ItineraryOpeningHoursChecker checker() {
		Map<UUID, OpeningHoursFilterPort.Answer> answers = new HashMap<>();
		answers.put(UUID.fromString(PLACE_OPEN), OpeningHoursFilterPort.Answer.OPEN);
		answers.put(UUID.fromString(PLACE_CLOSED), OpeningHoursFilterPort.Answer.CLOSED);
		answers.put(UUID.fromString(PLACE_UNKNOWN), OpeningHoursFilterPort.Answer.NOT_COLLECTED);
		return new ItineraryOpeningHoursChecker(new FixedAnswers(answers));
	}

	/** 장소마다 정해 둔 답을 돌려주는 문. 실제 계약과 같은 세 갈래를 그대로 쓴다. */
	private record FixedAnswers(Map<UUID, OpeningHoursFilterPort.Answer> answers)
			implements OpeningHoursFilterPort {

		@Override
		public Answer openAt(UUID placeId, OffsetDateTime at) {
			return this.answers.getOrDefault(placeId, Answer.NOT_COLLECTED);
		}
	}

	private static ItineraryItem item(String itemKey, int dayIndex, String placeId, String startTime) {
		LocalTime start = (startTime == null) ? null : LocalTime.parse(startTime);
		return new ItineraryItem(UUID.randomUUID().toString(), UUID.randomUUID().toString(), itemKey,
				dayIndex, LocalDate.of(2026, 9, 7).plusDays(dayIndex), dayIndex + 1, placeId,
				start, (start == null) ? null : start.plusHours(1), 60,
				false, null, ItineraryItem.DataStatus.ESTIMATED, List.of(), List.of(), null, null);
	}
}
