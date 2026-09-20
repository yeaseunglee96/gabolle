package com.gabolle.backend.place.loader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * 이미 있는 장소에 지하철 출구 안내만 붙인다. 장소 적재기는 이미 있는 장소를 건드리지 않는데,
 * 지하철 출구는 나중에 조사해 채우는 값이라 그 규칙에 묶으면 영영 못 채운다.
 *
 * <p>{@link PlacePhotoLoader} 와 달리 어느 출처의 장소에든 붙는다 — 출구 안내는 상가업소에도
 * 필요해서, {@code namespace} 를 받아 {@link PlaceFeatureLoader#placeIdOf} 로 장소를 찾는다.
 */
@Component
@Profile({ "db", "dev" })
public class SubwayExitLoader {

	private final PlaceRepository placeRepository;

	public SubwayExitLoader(PlaceRepository placeRepository) {
		this.placeRepository = placeRepository;
	}

	@Transactional
	public Result load(List<SubwayExitRow> rows) {
		Map<UUID, Place> places = new HashMap<>();
		this.placeRepository
				.findAllById(rows.stream().map((row) -> PlaceFeatureLoader.placeIdOf(row.namespace(), row.storeId()))
						.toList())
				.forEach((place) -> places.put(place.getPlaceId(), place));

		int attached = 0;
		int noPlace = 0;
		for (SubwayExitRow row : rows) {
			Place place = places.get(PlaceFeatureLoader.placeIdOf(row.namespace(), row.storeId()));
			if (place == null) {
				// 실패시키지 않고 센다 — 장소 적재를 먼저 안 돌렸을 때 숫자로 알려 준다.
				noPlace++;
				continue;
			}
			place.assignSubwayExit(row.subwayExit());
			attached++;
		}
		// save 를 따로 부르지 않는다 — 위에서 읽은 Place 는 영속 상태라 트랜잭션이 끝날 때 나간다.
		return new Result(attached, noPlace);
	}

	public record Result(int attached, int noPlace) {

		public Result plus(Result other) {
			return new Result(this.attached + other.attached, this.noPlace + other.noPlace);
		}

		@Override
		public String toString() {
			return "붙임 %d · 붙일 장소 없어 넘김 %d".formatted(this.attached, this.noPlace);
		}
	}
}
