package com.gabolle.backend.place.adapter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.itinerary.application.port.PlaceEventSchedule;
import com.gabolle.backend.itinerary.application.port.PlaceEventSchedulePort;
import com.gabolle.backend.place.domain.PlaceEventPeriod;
import com.gabolle.backend.place.repository.PlaceEventPeriodRepository;

/**
 * {@link PlaceEventSchedulePort} 의 장소 쪽 구현 — S15P21E201-467.
 *
 * <p>인터페이스는 쓰는 쪽(일정)이 정의하고 구현은 여기에 둔다. {@code place_event_period} 를
 * 읽는 코드가 장소 밖으로 새지 않는 자리가 이 클래스다 — 일정이 이 표를 직접 읽으면 표 구조를
 * 고칠 때 일정까지 함께 고쳐야 한다.
 *
 * <p>🔴 <b>겹침 판정을 여기서 다시 쓰지 않는다.</b> 날짜 하나가 회차에 들어가는지는
 * {@link PlaceEventPeriod#overlaps} 에 이미 있고, 그 메서드에 하루짜리 구간
 * ({@code overlaps(day, day)})을 물어 재사용한다. 같은 판정식을 두 곳에 적으면 한쪽만 고쳐지는
 * 날이 오는데, 그 날 아무 오류도 나지 않고 축제 날짜만 조용히 어긋난다.
 *
 * <p>{@code @Profile({"db","dev"})} 는 이 패키지의 다른 빈과 같다 — 저장소가 그 프로필에서만
 * 뜬다. 이 빈이 없는 컨텍스트에서 일정 편집 서비스가 어떻게 되는지는
 * {@code ItineraryEditService} 클래스 주석에 적어 뒀다.
 */
@Component
@Profile({ "db", "dev" })
public class PlaceEventScheduleAdapter implements PlaceEventSchedulePort {

	private final PlaceEventPeriodRepository eventPeriods;

	public PlaceEventScheduleAdapter(PlaceEventPeriodRepository eventPeriods) {
		this.eventPeriods = eventPeriods;
	}

	@Override
	public PlaceEventSchedule scheduleWithin(String placeId, LocalDate from, LocalDate to) {
		UUID id = parseOrNull(placeId);
		if (id == null) {
			// 식별자 모양이 아니면 기간을 찾을 방법이 없다. 여기서 예외를 던지면 "장소가
			// 없다" 와 "축제가 안 열린다" 가 섞인 응답이 나가므로, 기간 없는 장소와 같이
			// 다루고 판정은 뒤의 외래키가 하게 둔다 — 이 경로의 기존 거동과 같다.
			return PlaceEventSchedule.unscheduled();
		}

		List<PlaceEventPeriod> periods = this.eventPeriods.findByPlaceIdOrderByStartDateAsc(id);
		if (periods.isEmpty()) {
			return PlaceEventSchedule.unscheduled();
		}

		// 여행은 길어도 며칠이라 하루씩 훑는다. 회차가 겹쳐 있어도 날짜 목록에는 한 번만
		// 들어간다 — 화면은 "고를 수 있는 날" 만 알면 된다.
		List<LocalDate> openDates = new ArrayList<>();
		for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
			LocalDate probe = day;
			boolean open = periods.stream().anyMatch((period) -> period.overlaps(probe, probe));
			if (open) {
				openDates.add(probe);
			}
		}
		return PlaceEventSchedule.openOn(openDates);
	}

	private static UUID parseOrNull(String placeId) {
		if (placeId == null || placeId.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(placeId.trim());
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}
}
