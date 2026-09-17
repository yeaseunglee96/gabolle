package com.gabolle.backend.story.presentation.dto;

import com.gabolle.backend.story.domain.ReactionType;

import jakarta.validation.constraints.NotNull;

/**
 * {@code PUT /api/v1/stories/{storyId}/reaction} 의 본문 — {@code {"reaction":"LIKE"}}.
 *
 * <p>🔴 <b>「하트」라는 이름의 칸은 없다.</b> 화면의 하트가 곧 {@code LIKE} 라, 칸을 따로 두면
 * 하트와 좋아요를 <b>따로 누를 수 있는 것처럼</b> 보이고 그 둘이 어긋난 상태가 생긴다.
 *
 * <p>🔴 종류가 빠졌으면 400 이다({@code @NotNull}). 비워 보내는 것을 「취소」로 받지 않는다 —
 * 취소는 {@code DELETE} 다. 한 가지 일을 두 가지 방법으로 할 수 있게 두면 앱마다 다른 쪽을
 * 쓰고, 둘 중 하나만 고쳐진 채로 남는다.
 */
public record StoryReactionRequest(@NotNull ReactionType reaction) {
}
