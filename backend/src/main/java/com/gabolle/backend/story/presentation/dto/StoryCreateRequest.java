package com.gabolle.backend.story.presentation.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.gabolle.backend.place.api.PlaceSnapshotRequest;
import com.gabolle.backend.story.domain.Story;
import com.gabolle.backend.story.domain.StoryVisibility;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 기록 작성 본문 — {@code POST /api/v1/stories}.
 *
 * <p>좌표 칸이 없다. 앱이 {@code lat}·{@code lng} 를 실어 보내도 여기 없는 칸은 버려진다 — 위치는
 * {@code region} 하나이고, 없으면 연결한 장소의 주소에서 앞 두 마디를 딴다.
 *
 * @param imageUrls 먼저 {@code POST /api/v1/uploads/story-image} 로 올려 받은 주소. 최대 3장. 올린 사람만 붙일 수 있다
 * @param publishAt 공개 시각. 없으면 여행 종료 다음 날 0시(여행 시간대), 여행도 없으면 지금
 * @param visibility 없으면 PUBLIC
 */
public record StoryCreateRequest(
		@NotBlank @Size(max = Story.MAX_BODY_LENGTH) String body,
		@Size(max = Story.MAX_IMAGES) List<String> imageUrls,
		UUID placeId,
		UUID tripId,
		@Size(max = 100) String region,
		StoryVisibility visibility,
		Instant publishAt,

		/**
		 * 있으면 댓글, 없으면 원글이다. 댓글을 만드는 경로를 따로 두지 않고 이 칸 하나로 가른다.
		 *
		 * <p>댓글일 때는 {@code body} 와 {@code imageUrls} 만 읽는다 — {@code visibility}·{@code publishAt} 은
		 * 서버가 정하고({@code PUBLIC} · 지금) {@code tripId}·{@code placeId}·{@code region} 은 안 쓴다. 무시한다고
		 * 400 을 내지는 않는다.
		 *
		 * <p>부모가 댓글이어도 된다 — 댓글의 댓글이고 깊이 제한은 없다.
		 */
		UUID parentStoryId,

		/**
		 * 붙일 동영상의 주소. 먼저 {@code POST /api/v1/uploads/story-video} 로 올려 받은 값이다.
		 * 한 기록에 하나이고({@code uq_story_video_story}), 내가 올린 것만 붙일 수 있다 —
		 * 주소를 알아도 남의 것은 400 이다.
		 */
		String videoUrl,

		/**
		 * 그 동영상의 썸네일 주소. 사진 창구({@code POST /api/v1/uploads/story-image})로 올려
		 * 받은 값이다. 없어도 되고, 그러면 동영상만 있고 썸네일이 없는 정상 상태가 된다.
		 *
		 * <p>{@code story_image} 에 들어가지 않으므로 사진 3장과 자리를 다투지 않는다.
		 * {@code videoUrl} 없이 이것만 보내면 400 이다.
		 */
		String thumbnailUrl,

		/**
		 * 우리 표에 없는 장소를 골랐을 때 그 자리에서 보내는 스냅샷 — S15P21E201-1426.
		 *
		 * <p>글 작성 화면은 우리 DB 장소와 카카오 검색 결과를 섞어 보여주는데, 카카오 결과에는
		 * {@code placeId} 가 없어 지금까지 장소가 안 실렸다. 이 칸이 있으면 서버가
		 * {@code (source, externalId)} 로 찾거나 만들어 그 id 를 {@code story.place_id} 에 넣는다.
		 *
		 * <p>🔴 {@code placeId} 가 있으면 <b>이 칸은 안 본다.</b> 우리 표의 장소를 고른 것이
		 * 확실한데 스냅샷을 또 보고 upsert 하면, 앱이 실수로 다른 장소의 스냅샷을 실었을 때
		 * 어느 쪽이 맞는지 서버가 정하게 된다. 둘 다 없으면 지금처럼 장소 없는 기록이다.
		 */
		@Valid PlaceSnapshotRequest place) {



	/**
	 * {@code parentStoryId} 를 안 적은 자바 호출자를 위한 통로 — 원글로 본다. JSON 역직렬화는 이 생성자를
	 * 쓰지 않는다(Jackson 은 칸 이름으로 맞추므로 그 자리가 그냥 {@code null} 이다).
	 */
	public StoryCreateRequest(String body, List<String> imageUrls, UUID placeId, UUID tripId, String region,
			StoryVisibility visibility, Instant publishAt) {
		this(body, imageUrls, placeId, tripId, region, visibility, publishAt, null, null, null, null);
	}

	/**
	 * 동영상 칸 없이 {@code parentStoryId} 까지만 적는 자바 호출자를 위한 통로. JSON
	 * 역직렬화는 이 생성자를 쓰지 않는다 — 없는 칸은 그냥 {@code null} 이다.
	 */
	public StoryCreateRequest(String body, List<String> imageUrls, UUID placeId, UUID tripId, String region,
			StoryVisibility visibility, Instant publishAt, UUID parentStoryId) {
		this(body, imageUrls, placeId, tripId, region, visibility, publishAt, parentStoryId, null, null, null);
	}

	public List<String> imageUrlsOrEmpty() {
		return imageUrls == null ? List.of() : imageUrls;
	}

	public StoryVisibility visibilityOrDefault() {
		return visibility == null ? StoryVisibility.PUBLIC : visibility;
	}
}
