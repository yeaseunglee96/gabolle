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
		boolean published,

		/**
		 * 🔴 S15P21E201-1183 — 댓글이면 부모 글의 id, 원글이면 {@code null}. 맨 뒤에 더한 칸이다.
		 *
		 * <p><b>댓글도 이 응답 모양을 그대로 쓴다.</b> 별도 타입을 만들지 않는 것이 시안의 요구다 —
		 * *「댓글 = 같은 StoryDto 형태 + parentId. 별도 Comment 타입 만들지 말 것」*. 화면이 원글과
		 * 댓글을 같은 부품으로 그리므로 응답도 같은 모양이어야 한다.
		 *
		 * <p>🔴 <b>댓글의 댓글도 이 칸 하나로 이어진다.</b> 깊이 제한은 없다 — 몇 단까지 보여줄지는
		 * 화면이 정한다.
		 */
		String parentId,

		/**
		 * 🔴 S15P21E201-1183 — <b>직접</b> 달린 댓글 수. 손자 이하는 안 센다.
		 *
		 * <p>없으면 <b>0</b> 이고 {@code null} 이 아니다.
		 *
		 * <p>손자까지 세면 댓글 하나를 지울 때 조상을 전부 거슬러 올라가며 내려야 한다. 직접 달린
		 * 것만 세면 고치는 자리가 깊이와 무관하게 언제나 한 칸이다. 화면은 트위터처럼 「답글 3개」를
		 * 그 댓글 밑에 붙이면 되므로 손해가 없다.
		 */
		int replyCount) {

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
