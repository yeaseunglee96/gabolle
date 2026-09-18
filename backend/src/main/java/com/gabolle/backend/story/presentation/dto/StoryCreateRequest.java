package com.gabolle.backend.story.presentation.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.gabolle.backend.story.domain.Story;
import com.gabolle.backend.story.domain.StoryVisibility;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 기록 작성 본문 — {@code POST /api/v1/stories}. S15P21E201-207.
 *
 * <p>🔴 좌표 칸이 없다. 앱이 {@code lat}·{@code lng} 를 실어 보내도 여기 없는 칸은 Jackson 이 버린다
 * (알 수 없는 속성 무시) — 서버에 좌표가 닿는 자리가 아예 없다. 위치는 {@code region}(지역 문자열)
 * 하나다. 없으면 연결한 장소의 주소에서 앞 두 마디를 딴다.
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
		 * 🔴 S15P21E201-1183 — 이 값이 있으면 <b>댓글</b>이다. 없으면 원글이다.
		 *
		 * <p>댓글을 만드는 경로를 따로 두지 않고 이 칸 하나로 가른다 — 시안이 요구한 대로
		 * 댓글이 원글과 <b>같은 것</b>이기 때문이다.
		 *
		 * <p>🔴 <b>댓글일 때는 위의 칸 대부분을 안 읽는다.</b> {@code visibility}·{@code publishAt}
		 * 은 댓글에 개념이 없어 서버가 정하고({@code PUBLIC} · 지금), {@code tripId}·
		 * {@code placeId}·{@code region} 도 안 쓴다. 읽는 것은 {@code body} 와 {@code imageUrls}
		 * 뿐이다.
		 *
		 * <p>무시한다고 400 을 내지는 않는다. 화면이 같은 요청 모양을 쓰는 것이 이 설계의
		 * 목적이라, 원글용 칸이 딸려 오는 것은 정상이다.
		 *
		 * <p>부모가 댓글이어도 된다 — <b>댓글의 댓글</b>이고 깊이 제한은 없다.
		 */
		UUID parentStoryId,

		/**
		 * 🔴 S15P21E201-1282 — 붙일 동영상의 주소. 먼저
		 * {@code POST /api/v1/uploads/story-video} 로 올려 받은 그 값이다.
		 *
		 * <p><b>한 기록에 하나</b>다(표의 {@code uq_story_video_story} 가 강제한다). 안 보내면
		 * 동영상 없는 기록이고, 그게 대부분이다.
		 *
		 * <p>🔴 <b>내가 올린 것만 붙일 수 있다.</b> 주소를 알아도 남의 것은 400 이다 —
		 * 사진이 같은 규칙을 먼저 썼다({@code resolveImages}).
		 */
		String videoUrl,

		/**
		 * 🔴 S15P21E201-1282 — 그 동영상의 썸네일 주소. 사진 창구
		 * ({@code POST /api/v1/uploads/story-image})로 올려 받은 값이다.
		 *
		 * <p>🔴 <b>없어도 된다.</b> 앱이 썸네일을 못 만들면 안 보내면 되고, 그러면 「동영상은
		 * 있고 썸네일만 없다」가 된다 — 정상 상태다(S15P21E201-1279).
		 *
		 * <p>🔴 <b>사진 3장과 자리를 다투지 않는다.</b> 썸네일은 {@code story_image} 에 안 들어가고
		 * 동영상에 딸려 간다. 그래서 동영상을 넣어도 {@code imageUrls} 는 3장 그대로 쓸 수 있다.
		 *
		 * <p>{@code videoUrl} 없이 이것만 보내면 400 이다 — 붙일 동영상이 없는 썸네일은 뜻이 없다.
		 */
		String thumbnailUrl) {

	/**
	 * 🔴 {@code parentStoryId} 를 안 적은 기존 호출자를 위한 것이다 — <b>원글</b>로 본다
	 * (S15P21E201-1183).
	 *
	 * <p>{@code PlaceFeatureNdjsonReader.Fact} 가 같은 이유로 같은 모양을 쓴다. 칸을 하나 더
	 * 붙이면서 부르는 자리를 전부 고치면, 그 diff 안에서 <b>정말 바뀐 곳</b>이 안 보인다.
	 *
	 * <p>JSON 역직렬화는 이 생성자를 안 쓴다 — Jackson 은 칸 이름으로 맞추므로 {@code parentStoryId}
	 * 가 없는 본문이 오면 그 자리가 그냥 {@code null} 이다. 이것은 자바 호출자만을 위한 통로다.
	 */
	public StoryCreateRequest(String body, List<String> imageUrls, UUID placeId, UUID tripId, String region,
			StoryVisibility visibility, Instant publishAt) {
		this(body, imageUrls, placeId, tripId, region, visibility, publishAt, null, null, null);
	}

	/**
	 * 🔴 S15P21E201-1282 — 동영상 칸 둘을 더하면서 {@code parentStoryId} 까지 받던 자리를 위해 둔다.
	 * -1183 이 같은 이유로 같은 모양을 먼저 썼다 — 칸을 하나 붙이면서 부르는 자리를 전부 고치면
	 * 그 diff 안에서 <b>정말 바뀐 곳</b>이 안 보인다.
	 *
	 * <p>JSON 역직렬화는 이 생성자를 안 쓴다. Jackson 은 칸 이름으로 맞추므로 동영상 칸이 없는
	 * 본문이 오면 그 자리가 그냥 {@code null} 이다 — 지금까지의 앱이 그대로 돈다.
	 */
	public StoryCreateRequest(String body, List<String> imageUrls, UUID placeId, UUID tripId, String region,
			StoryVisibility visibility, Instant publishAt, UUID parentStoryId) {
		this(body, imageUrls, placeId, tripId, region, visibility, publishAt, parentStoryId, null, null);
	}

	public List<String> imageUrlsOrEmpty() {
		return imageUrls == null ? List.of() : imageUrls;
	}

	public StoryVisibility visibilityOrDefault() {
		return visibility == null ? StoryVisibility.PUBLIC : visibility;
	}
}
