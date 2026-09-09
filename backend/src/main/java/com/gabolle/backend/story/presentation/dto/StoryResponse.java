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

	public record PlaceRef(String id, String name) {
	}

	public record Image(String url, int position) {
	}
}
