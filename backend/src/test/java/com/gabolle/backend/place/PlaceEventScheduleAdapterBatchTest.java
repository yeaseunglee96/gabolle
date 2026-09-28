package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.itinerary.application.port.PlaceEventSchedule;
import com.gabolle.backend.place.adapter.PlaceEventScheduleAdapter;
import com.gabolle.backend.place.domain.PlaceEventPeriod;
import com.gabolle.backend.place.repository.PlaceEventPeriodRepository;

/**
 * 추천 후보 여럿을 한 번에 묻는 길이 하나씩 묻는 길과 같은 답을 내는가. 추천은 한 번에 묻고 손으로 더하기는
 * 하나씩 묻는다 — 둘이 다르면 같은 축제가 한쪽에선 열리고 한쪽에선 닫힌다.
 */
class PlaceEventScheduleAdapterBatchTest {

	private static final LocalDate FROM = LocalDate.of(2026, 9, 10);

	private static final LocalDate TO = LocalDate.of(2026, 9, 12);

	@Test
	@DisplayName("🔴 한 번에 물어도 하나씩 물은 것과 같다 — 끝난 축제 · 둘째 날만 여는 축제 · 기간 없는 곳")
	void batchAnswersMatchSingleAnswers() {
		UUID ended = UUID.randomUUID();
		UUID secondDayOnly = UUID.randomUUID();
		UUID noPeriod = UUID.randomUUID();
		PlaceEventPeriod countdown = PlaceEventPeriod.of(ended, "카운트다운", LocalDate.of(2025, 12, 31),
				LocalDate.of(2026, 1, 1), "TOURAPI", "1", OffsetDateTime.now());
		PlaceEventPeriod oneDay = PlaceEventPeriod.of(secondDayOnly, "하루 축제", LocalDate.of(2026, 9, 11),
				LocalDate.of(2026, 9, 11), "TOURAPI", "2", OffsetDateTime.now());
		PlaceEventPeriodRepository repository = mock(PlaceEventPeriodRepository.class);
		when(repository.findByPlaceIdIn(anyCollection())).thenReturn(List.of(countdown, oneDay));
		when(repository.findByPlaceIdOrderByStartDateAsc(ended)).thenReturn(List.of(countdown));
		when(repository.findByPlaceIdOrderByStartDateAsc(secondDayOnly)).thenReturn(List.of(oneDay));
		when(repository.findByPlaceIdOrderByStartDateAsc(noPeriod)).thenReturn(List.of());
		PlaceEventScheduleAdapter adapter = new PlaceEventScheduleAdapter(repository);
		List<String> ids = List.of(ended.toString(), secondDayOnly.toString(), noPeriod.toString(), "not-a-uuid");

		Map<String, PlaceEventSchedule> batch = adapter.schedulesWithin(ids, FROM, TO);

		for (String id : ids) {
			assertThat(batch.get(id)).as(id).isEqualTo(adapter.scheduleWithin(id, FROM, TO));
		}
		assertThat(batch.get(ended.toString())).isEqualTo(PlaceEventSchedule.openOn(List.of()));
		assertThat(batch.get(secondDayOnly.toString())).isEqualTo(PlaceEventSchedule.openOn(List.of(FROM.plusDays(1))));
		assertThat(batch.get(noPeriod.toString())).isEqualTo(PlaceEventSchedule.unscheduled());
	}
}
