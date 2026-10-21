package com.gabolle.backend.place.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.SavedPlace;
import com.gabolle.backend.place.service.SavedPlaceService;

/**
 * 저장한 장소 하나.
 *
 * <p>장소의 이름·사진·좌표·종류를 함께 싣는다(S15P21E201-1971). 전에는 「화면이 상세를 따로 부르니
 * 복사하지 않는다」였는데, 그러면 후보가 30곳일 때 화면이 31번 부른다. 복사해 저장하는 것이 아니라
 * 응답 때마다 {@code place} 행에서 읽어 싣는 것이라 장소 이름이 바뀌어도 한쪽만 낡지 않는다.
 *
 * <p>앞의 두 칸({@code placeId}·{@code savedAt})의 이름·자리는 그대로다 — 이미 배포된 앱이 읽는다.
 * 새 칸은 뒤에 붙이고, 장소 행이 사라진 저장이면 키째 빠진다(모르는 값을 지어내지 않는다).
 * 이름 칸 규칙은 장소 상세({@code PlaceDetailResponse})와 같다 — {@code nameKo}·{@code nameEn} 은 둘 다,
 * 일본어·중국어는 {@code localNames}({@code ja}·{@code zh-Hans}·{@code zh-Hant}) 에 있는 것만.
 *
 * @param savedAt 언제 눌렀나. 저장 탭이 최근 순으로 보여 주는 근거다
 * @param photoSource 사진 옆에 그대로 보여 줄 출처 문구. {@code photoUrl} 과 짝이다
 */
public record SavedPlaceResponse(
		UUID placeId,
		OffsetDateTime savedAt,
		@JsonInclude(JsonInclude.Include.NON_NULL) String nameKo,
		@JsonInclude(JsonInclude.Include.NON_NULL) String nameEn,
		@JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> localNames,
		@JsonInclude(JsonInclude.Include.NON_NULL) String category,
		@JsonInclude(JsonInclude.Include.NON_NULL) Double lat,
		@JsonInclude(JsonInclude.Include.NON_NULL) Double lng,
		@JsonInclude(JsonInclude.Include.NON_NULL) String photoUrl,
		@JsonInclude(JsonInclude.Include.NON_NULL) String photoSource) {

	public static SavedPlaceResponse of(SavedPlace saved) {
		return of(saved, null);
	}

	/** {@code place} 가 {@code null} 이면(행이 사라진 저장) 번호·시각만 나간다. */
	public static SavedPlaceResponse of(SavedPlace saved, Place place) {
		if (place == null) {
			return new SavedPlaceResponse(saved.getPlaceId(), saved.getCreatedAt(),
					null, null, null, null, null, null, null, null);
		}
		return new SavedPlaceResponse(saved.getPlaceId(), saved.getCreatedAt(),
				place.getNameKo(), place.getNameEn(), place.localNames(), place.getCategory(),
				place.getLat(), place.getLng(), place.getPhotoUrl(), place.getPhotoSource());
	}

	/**
	 * 내가 저장한 것.
	 *
	 * <p>모양은 {@code FestivalResponse}·{@code ItineraryVersionsResponse} 와 같게 뒀다
	 * ({@code items}·{@code count}·{@code hasMore}) — 목록 응답마다 다른 모양을 만들면 화면이
	 * 경로마다 다르게 읽어야 한다.
	 *
	 * @param hasMore 상한에 걸려 더 있는데 안 보냈다. 이 칸이 없으면 부르는 쪽이 「잘린 것」과
	 *     「이게 전부인 것」을 구분할 수 없다
	 */
	public record Page(List<SavedPlaceResponse> items, int count, boolean hasMore) {

		public static Page of(SavedPlaceService.Page page) {
			List<SavedPlaceResponse> items = page.items().stream()
					.map(saved -> SavedPlaceResponse.of(saved, page.places().get(saved.getPlaceId())))
					.toList();
			return new Page(items, items.size(), page.hasMore());
		}
	}
}
