package com.gabolle.backend.place.loader;

import java.util.ArrayList;
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
 * 폐업 여부를 장소에 적는다. 장소는 소상공인 상가정보에서 들어오고 그 목록은 수집 시점에
 * 영업 중이던 가게라, 그 뒤에 닫은 곳이 우리 표에 그대로 남는다.
 *
 * <p>「폐업」이면 날짜를 적고 「영업」이면 지운다. 지우는 쪽이 없으면 한번 잘못 이어진 가게가
 * 영영 추천에서 사라진다 — 이음은 이름과 자리로 맞추므로 틀릴 수 있다.
 *
 * <p>이음 파일에 없는 장소는 건드리지 않는다. 해수욕장·전망대는 애초에 음식·주류 인허가가
 * 없어 이어지지 않는다.
 */
@Component
@Profile({ "db", "dev" })
public class PlaceClosureLoader {

	private final PlaceRepository placeRepository;

	public PlaceClosureLoader(PlaceRepository placeRepository) {
		this.placeRepository = placeRepository;
	}

	/**
	 * @param marked 닫았다고 적은 장소 수
	 * @param cleared 닫힘을 지운 장소 수 — 다시 열었거나, 잘못 이어졌던 것이 풀렸다
	 * @param unchanged 이미 같은 값이라 안 건드린 장소 수
	 * @param noPlace 붙일 장소가 없어 넘긴 줄 — 장소 적재를 안 돌렸으면 여기가 커진다
	 */
	public record Result(int marked, int cleared, int unchanged, int noPlace) {

		public Result plus(Result other) {
			return new Result(this.marked + other.marked, this.cleared + other.cleared,
					this.unchanged + other.unchanged, this.noPlace + other.noPlace);
		}

		@Override
		public String toString() {
			return "닫음 " + this.marked + " · 되돌림 " + this.cleared + " · 그대로 " + this.unchanged
					+ " · 장소없음 " + this.noPlace;
		}
	}

	@Transactional
	public Result saveChunk(List<PlaceClosureRow> rows) {
		Map<UUID, PlaceClosureRow> byPlaceId = new HashMap<>();
		for (PlaceClosureRow row : rows) {
			// 같은 덩어리에 같은 가게가 두 줄이면 뒤엣것을 쓴다.
			byPlaceId.put(SbizPlaceLoader.placeIdOf(row.storeId()), row);
		}

		int marked = 0;
		int cleared = 0;
		int unchanged = 0;
		List<Place> dirty = new ArrayList<>();
		int found = 0;
		for (Place place : this.placeRepository.findAllById(byPlaceId.keySet())) {
			found++;
			PlaceClosureRow row = byPlaceId.get(place.getPlaceId());
			if (java.util.Objects.equals(place.getClosedOn(), row.closedOn())) {
				unchanged++;
				continue;
			}
			place.recordClosedOn(row.closedOn());
			dirty.add(place);
			if (row.closedOn() == null) {
				cleared++;
			}
			else {
				marked++;
			}
		}
		this.placeRepository.saveAll(dirty);
		return new Result(marked, cleared, unchanged, byPlaceId.size() - found);
	}
}
