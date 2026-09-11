package com.gabolle.backend.story.presentation.dto;

import java.util.List;

/**
 * 기록 한 건 — 피드 항목과 상세 조회가 같은 모양을 쓴다. 앱이 둘을 한 컴포넌트로 그린다.
 *
 * @param mine 요청자가 작성자인가. 수정·삭제 버튼을 켤지 정한다
 * @param published 공개 시각이 지났나. 작성자에게만 false 가 보일 수 있다(남에게는 아예 안 보인다)
 */
public record StoryResponse(
		String id,
		Author author,
		String body,
		String region,
		PlaceRef place,
		String tripId,
		List<Image> images,
		String visibility,
		String publishAt,
		String createdAt,
		String updatedAt,
		boolean mine,
		boolean published) {

	public record Author(String id, String displayName) {
	}

	/**
	 * 기록이 가리키는 장소.
	 *
	 * <p>🔴 {@code lat}·{@code lng} 는 S15P21E201-829 에서 더했다. 화면이 사진을 지도에 찍으려면
	 * 좌표가 필요한데, 그전에는 장소마다 {@code GET /api/v1/places/&#123;id&#125;} 를 한 번씩 더
	 * 불러야 했다(사진 여러 장이 같은 장소면 같은 장소를 반복해서). 좌표가 없는 장소도 있으므로
	 * 두 값은 {@code null} 일 수 있다 — 화면은 그때 마커를 찍지 않는다.
	 */
	public record PlaceRef(String id, String name, Double lat, Double lng) {
	}

	public record Image(String url, int position) {
	}
}
