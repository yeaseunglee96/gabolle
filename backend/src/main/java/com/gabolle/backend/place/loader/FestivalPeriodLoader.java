package com.gabolle.backend.place.loader;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.domain.PlaceEventPeriod;
import com.gabolle.backend.place.repository.PlaceEventPeriodRepository;
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * 축제 회차를 이미 적재된 장소에 붙인다 — S15P21E201-863.
 *
 * <h2>무엇이 비어 있었나</h2>
 * 축제 조회({@code GET /api/v1/festivals})도 그 아래 겹침 판정도 2026-09-07 부터 있었다. 그런데
 * {@code place_event_period} 에 <b>행을 넣는 프로그램이 없어서</b> 응답이 늘 비었고, 화면은 그때마다
 * 예시 일정으로 떨어졌다. 사용자에게는 "축제 기능이 가짜" 로 보인다.
 *
 * <h2>장소가 먼저다</h2>
 * 관광공사 장소 적재가 만든 장소에만 붙인다({@link TourApiPlaceLoader#placeIdOf} 로 같은 id 를
 * 계산한다). 장소가 없으면 넘기고 그 수를 따로 센다 — 그 숫자가 크면 <b>적재 순서를 틀린 것</b>이지
 * 자료가 나쁜 것이 아니다. 접근성·영업시간 적재가 같은 자리에서 같은 판단을 한다.
 *
 * <h2>🔴 같은 회차를 두 번 넣어도 늘지 않는다</h2>
 * 식별자를 {@code (장소, 시작일, 종료일)} 에서 계산하므로({@link PlaceEventPeriod#of}) 다시 돌려도
 * 같은 행을 가리킨다. 표의 유일 제약도 같은 세 값이라 둘이 어긋나지 않는다. 완료 기준의 "두 번
 * 돌려도 회차가 안 는다" 가 이것으로 지켜진다.
 *
 * <p>이미 있는 회차는 <b>덮어쓰지 않고 넘긴다.</b> 기간이 바뀌었다면 그것은 새 회차가 아니라
 * 정정인데, 정정을 자동으로 받아들이면 사람이 손으로 고친 값도 다음 적재가 되돌린다. 지금은
 * 그런 손질이 없지만 그 문을 미리 열어 둘 이유도 없다.
 */
@Component
@Profile({ "db", "dev" })
public class FestivalPeriodLoader {

	/** 출처 표기. 장소 적재와 같은 값을 쓴다 — 같은 API 에서 왔다. */
	public static final String SOURCE_TYPE = TourApiPlaceLoader.SOURCE_TYPE;

	private final PlaceRepository placeRepository;

	private final PlaceEventPeriodRepository eventPeriodRepository;

	public FestivalPeriodLoader(PlaceRepository placeRepository, PlaceEventPeriodRepository eventPeriodRepository) {
		this.placeRepository = placeRepository;
		this.eventPeriodRepository = eventPeriodRepository;
	}

	@Transactional
	public Result saveChunk(List<FestivalPeriodRow> rows, OffsetDateTime collectedAt) {
		Set<UUID> knownPlaces = new HashSet<>();
		this.placeRepository
				.findAllById(rows.stream().map((row) -> TourApiPlaceLoader.placeIdOf(row.contentId())).toList())
				.forEach((place) -> knownPlaces.add(place.getPlaceId()));

		List<PlaceEventPeriod> candidates = new ArrayList<>();
		int noPlace = 0;
		for (FestivalPeriodRow row : rows) {
			UUID placeId = TourApiPlaceLoader.placeIdOf(row.contentId());
			if (!knownPlaces.contains(placeId)) {
				noPlace++;
				continue;
			}
			candidates.add(PlaceEventPeriod.of(placeId, row.title(), row.startDate(), row.endDate(),
					SOURCE_TYPE, row.contentId(), collectedAt));
		}

		Set<UUID> existing = new HashSet<>();
		this.eventPeriodRepository.findAllById(candidates.stream().map(PlaceEventPeriod::getPlaceEventPeriodId).toList())
				.forEach((period) -> existing.add(period.getPlaceEventPeriodId()));

		List<PlaceEventPeriod> toInsert = new ArrayList<>();
		int alreadyThere = 0;
		for (PlaceEventPeriod candidate : candidates) {
			if (existing.contains(candidate.getPlaceEventPeriodId())) {
				alreadyThere++;
				continue;
			}
			toInsert.add(candidate);
		}
		if (!toInsert.isEmpty()) {
			this.eventPeriodRepository.saveAll(toInsert);
		}
		return new Result(toInsert.size(), noPlace, alreadyThere);
	}

	/**
	 * @param inserted     새로 넣은 회차
	 * @param noPlace      붙일 장소가 없어 넘긴 줄. 장소 적재를 먼저 안 돌린 경우다
	 * @param alreadyThere 이미 있어 넘긴 회차
	 */
	public record Result(int inserted, int noPlace, int alreadyThere) {

		public Result plus(Result other) {
			return new Result(this.inserted + other.inserted, this.noPlace + other.noPlace,
					this.alreadyThere + other.alreadyThere);
		}

		@Override
		public String toString() {
			return "넣은 회차 %d · 장소가 없어 넘긴 %d · 이미 있어 넘긴 %d".formatted(this.inserted, this.noPlace,
					this.alreadyThere);
		}
	}
}
