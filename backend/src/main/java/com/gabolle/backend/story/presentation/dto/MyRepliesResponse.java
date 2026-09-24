package com.gabolle.backend.story.presentation.dto;

import java.util.List;

/**
 * 내 댓글 목록 한 쪽 (S15P21E201-1600). 원글이 지워지거나 가려진 댓글도 들어 있다.
 *
 * @param nextCursor 다음 쪽이 없으면 {@code null}. 다른 피드와 같은 커서다
 */
public record MyRepliesResponse(List<Item> items, String nextCursor) {

	/**
	 * @param reply 내 댓글 그대로 — 피드·상세와 같은 모양
	 * @param parent 바로 위 글(댓글의 댓글이면 그 댓글)
	 */
	public record Item(StoryResponse reply, Parent parent) {
	}

	/**
	 * 바로 위 글의 지금 상태. 본문과 작성자는 내가 지금 그 글을 볼 수 있을 때({@code VISIBLE})만 싣는다 — 가려진 글을
	 * 이 목록을 통해 엿보면 안 된다. 두 칸은 값이 없어도 키는 빠지지 않는다({@code null}).
	 *
	 * @param state {@code VISIBLE} · {@code DELETED}(지워짐) · {@code HIDDEN}(신고로 가려졌거나, 나만 보기·팔로워
	 *     공개로 바뀌었거나, 작성자가 나를 차단해서 지금은 내가 못 봄)
	 * @param bodyPreview 본문 앞 60자. HTML 인코딩돼 있다({@link StoryResponse#body} 와 같다)
	 * @param authorName 작성자 표시 이름. 탈퇴했으면 「탈퇴한 사용자」 — {@link StoryResponse.Author} 와 같다
	 */
	public record Parent(String id, String state, String bodyPreview, String authorName) {
	}
}
