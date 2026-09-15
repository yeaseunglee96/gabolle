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
 * 이미 있는 장소에 <b>사진만</b> 붙인다 — S15P21E201-1006.
 *
 * <h2>🔴 왜 따로 있나 — 장소 적재기는 이미 있는 장소를 안 고친다</h2>
 *
 * {@code TourApiPlaceLoader} 는 «이미 있는 장소는 건너뛴다 — 고치지 않는다» 가 규칙이다.
 * 그 규칙은 옳다. 장소 본문은 한 원천이 주인이어야 하고, 적재를 다시 돌릴 때마다 남이 고친
 * 값이 덮이면 안 된다.
 *
 * <p>그런데 <b>사진은 나중에 다른 원천에서 온다.</b> 본 API 의 사진은 대부분 재사용이 막힌
 * 유형이라 비워 두고({@code TourApiPlaceLoader}), 관광사진갤러리처럼 <b>따로 신청해서 받는
 * 원천</b>이 나중에 붙는다. 그것을 장소 적재 규칙에 묶으면 <b>영영 못 채운다</b> — 장소를
 * 지웠다 다시 넣지 않는 한.
 *
 * <p>그래서 사진만 갱신하는 길을 따로 연다. 장소 적재기의 규칙은 그대로 둔다.
 *
 * <h2>🔴 마이그레이션이 아닌 이유</h2>
 *
 * 이 저장소에 선례가 있다 — {@code V20260915030000__tourapi_type1_photos.sql} 이 SQL 로
 * 사진을 채웠다. 그건 <b>그 장소들이 이미 운영에 있을 때</b> 쓴 방법이다.
 *
 * <p>축제 장소는 <b>아직 없다.</b> 마이그레이션은 <b>서버가 뜰 때</b> 도는데 그 시점에 대상
 * 행이 없어서 0 행을 고치고 끝난다. 그 뒤에 장소를 넣어도 사진은 영영 안 들어간다.
 * 적재기는 <b>사람이 순서를 정해 부르는 것</b>이라 그 문제가 없다.
 */
@Component
@Profile({ "db", "dev" })
public class PlacePhotoLoader {

	private final PlaceRepository placeRepository;

	public PlacePhotoLoader(PlaceRepository placeRepository) {
		this.placeRepository = placeRepository;
	}

	/**
	 * @return 붙인 수·붙일 장소가 없어 넘긴 수
	 */
	@Transactional
	public Result load(List<PlacePhotoRow> rows) {
		Map<UUID, Place> places = new HashMap<>();
		this.placeRepository
				.findAllById(rows.stream().map((row) -> TourApiPlaceLoader.placeIdOf(row.contentId())).toList())
				.forEach((place) -> places.put(place.getPlaceId(), place));

		int attached = 0;
		int noPlace = 0;
		for (PlacePhotoRow row : rows) {
			Place place = places.get(TourApiPlaceLoader.placeIdOf(row.contentId()));
			if (place == null) {
				// 🔴 실패시키지 않고 센다. 순서를 뒤집어 돌렸을 때 "장소를 먼저 넣어라" 를
				//    숫자로 알려 주는 것이 이 값의 목적이다 — OpeningHoursLoader 와 같다.
				noPlace++;
				continue;
			}
			place.attachPhoto(row.photoUrl(), row.attribution(), row.subject());
			attached++;
		}
		// 🔴 save 를 따로 부르지 않는다. 위에서 읽은 Place 는 영속 상태라 트랜잭션이 끝날 때
		//    바뀐 값이 그대로 나간다(더티 체킹). 다시 save 하면 같은 일을 두 번 시킨다.
		return new Result(attached, noPlace);
	}

	/**
	 * @param noPlace 붙일 장소가 없어 넘긴 줄. <b>이 수가 크면 장소 적재를 안 돌린 것이다</b> —
	 *     실패가 아니라 순서 문제라, 숫자로 보이게 둔다
	 */
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
