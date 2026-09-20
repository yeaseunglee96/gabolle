package com.gabolle.backend.story.domain;

/**
 * 기록(글)에 달 수 있는 반응.
 *
 * <p>화면의 하트가 곧 {@link #LIKE} 다. 글에는 「나중에 보려고 저장」이라는 개념이 없어 종류가 둘뿐이다.
 *
 * <p>여기에 {@code SAVE} 를 끼우지 않는다. 한 사람이 한 글에 하나만 갖는 칸이라, 끼우면 좋아요를
 * 눌렀다가 저장으로 바꾸면 좋아요가 사라진다. 글 저장은 별도 표로 간다.
 */
public enum ReactionType {

	/** 화면의 하트. */
	LIKE,

	DISLIKE
}
