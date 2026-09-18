package com.gabolle.backend.story.domain;

/**
 * 기록(글)에 달 수 있는 반응.
 *
 * <p>🔴 <b>하트가 곧 {@link #LIKE} 다.</b> 장소 쪽은 감정({@code PLACE_LIKE}·
 * {@code PLACE_DISLIKE})과 저장({@code saved_place} 하트)이 따로지만, 글에는
 * 「나중에 보려고 저장」이라는 개념이 없고 화면의 하트 아이콘이 곧 좋아요다.
 * 그래서 종류가 둘뿐이다.
 *
 * <p>나중에 「글 저장」이 생기면 그때 별도 표로 간다 — 여기에 {@code SAVE} 를 끼우면
 * 「좋아요를 눌렀다가 저장으로 바꾸면 좋아요가 사라지는」 이상한 동작이 된다.
 * 한 사람이 한 글에 하나만 갖는 칸이기 때문이다.
 */
public enum ReactionType {

	/** 화면의 하트. */
	LIKE,

	DISLIKE
}
