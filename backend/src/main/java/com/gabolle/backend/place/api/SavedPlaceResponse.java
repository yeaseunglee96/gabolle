package com.gabolle.backend.place.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.gabolle.backend.place.domain.SavedPlace;
import com.gabolle.backend.place.service.SavedPlaceService;

/**
 * 저장한 장소 하나 — S15P21E201-1013.
 *
 * <p>🔴 <b>장소의 이름·사진을 여기 싣지 않는다.</b> 화면은 이미 장소 번호로 상세를 따로
 * 부르고 있고(기기 저장 때부터 그랬다), 여기에 장소 내용을 복사해 넣으면 <b>같은 값이 두
 * 곳에 생겨</b> 장소 이름이 바뀌었을 때 한쪽만 낡는다.
 *
 * @param savedAt 언제 눌렀나. 저장 탭이 최근 순으로 보여 주는 근거다
 */
public record SavedPlaceResponse(UUID placeId, OffsetDateTime savedAt) {

	public static SavedPlaceResponse of(SavedPlace saved) {
		return new SavedPlaceResponse(saved.getPlaceId(), saved.getCreatedAt());
	}

	/**
	 * 내가 저장한 것.
	 *
	 * <p>모양은 {@code FestivalResponse}·{@code ItineraryVersionsResponse} 와 같게 뒀다
	 * ({@code items}·{@code count}·{@code hasMore}) — 목록 응답마다 다른 모양을 만들면 화면이
	 * 경로마다 다르게 읽어야 한다.
	 *
	 * @param hasMore 상한에 걸려 <b>더 있는데 안 보냈다</b> (S15P21E201-1037). 이 칸이
	 *     없으면 부르는 쪽이 「잘린 것」과 「이게 전부인 것」을 구분할 수 없고, 사용자에게는
	 *     누른 적 있는 하트가 사라진 것으로 보인다
	 */
	public record Page(List<SavedPlaceResponse> items, int count, boolean hasMore) {

		public static Page of(SavedPlaceService.Page page) {
			List<SavedPlaceResponse> items = page.items().stream().map(SavedPlaceResponse::of).toList();
			return new Page(items, items.size(), page.hasMore());
		}
	}
}
