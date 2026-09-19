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
 * 폐업 여부를 장소에 적는다 — S15P21E201-1341.
 *
 * <h2>🔴 왜 필요한가</h2>
 * 장소는 소상공인 상가정보에서 들어오고, 그 목록은 <b>그때 영업 중이던</b> 가게다. 그 뒤에 닫아도
 * 우리 표는 그대로다. 실측(2026-09-19)에서 이어진 36,458곳 중 <b>3,530곳(9.7%)이 이미 폐업</b>
 * 이었다 — 음식점만 보면 34,782곳 중 3,056곳(8.8%)이다. <b>열한 곳 중 한 곳</b>이다.
 *
 * <h2>🔴 켜는 것과 끄는 것을 둘 다 한다</h2>
 * 「폐업」이면 날짜를 적고, 「영업」이면 <b>지운다.</b> 지우는 쪽이 없으면 한번 잘못 이어진 가게
 * 하나가 <b>영영 추천에서 사라진다</b> — 이음은 이름과 자리로 맞추는 것이라 틀릴 수 있고,
 * 다음 판에서 풀리면 되돌아와야 한다.
 *
 * <h2>🔴 이 파일에 없는 장소는 안 건드린다</h2>
 * 이음 파일에는 인허가와 이어진 가게만 있다. 해수욕장·전망대는 애초에 음식·주류 인허가가 없어
 * 안 이어진다. 안 이어진 것을 폐업으로 떨어뜨리면 멀쩡한 곳이 통째로 사라진다.
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
	 * @param noPlace 🔴 붙일 장소가 없어 넘긴 줄 — 장소 적재를 안 돌렸으면 여기가 커진다
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
			// 🔴 같은 덩어리에 같은 가게가 두 줄이면 뒤엣것을 쓴다. 이음 파일은 가게마다 한
			//    줄이지만, 그 약속이 깨져도 여기서 터지지 않고 결과가 정해져 있어야 한다.
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
