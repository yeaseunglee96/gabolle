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
 * 축제 회차를 이미 적재된 장소에 붙인다.
 *
 * <p>관광공사 장소 적재가 만든 장소에만 붙인다({@link TourApiPlaceLoader#placeIdOf} 로 같은 id 를
 * 계산한다). 장소가 없으면 넘기고 그 수를 따로 센다 — 그 숫자가 크면 적재 순서를 틀린 것이지
 * 자료가 나쁜 것이 아니다.
 *
 * <p>식별자를 {@code (장소, 시작일, 종료일)} 에서 계산하므로({@link PlaceEventPeriod#of}) 다시
 * 돌려도 같은 행을 가리킨다. 표의 유일 제약도 같은 세 값이다.
 *
 * <p>이미 있는 회차는 덮어쓰지 않고 넘긴다. 기간이 바뀐 것은 새 회차가 아니라 정정인데, 정정을
 * 자동으로 받아들이면 사람이 손으로 고친 값도 다음 적재가 되돌린다.
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

	/** {@code noPlace} 는 붙일 장소가 없어 넘긴 줄이다 — 장소 적재를 먼저 안 돌리면 여기가 쌓인다. */
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
