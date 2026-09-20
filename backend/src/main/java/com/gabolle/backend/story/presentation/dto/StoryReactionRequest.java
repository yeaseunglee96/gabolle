package com.gabolle.backend.story.presentation.dto;

import com.gabolle.backend.story.domain.ReactionType;

import jakarta.validation.constraints.NotNull;

/**
 * {@code PUT /api/v1/stories/{storyId}/reaction} 의 본문. 화면의 하트가 곧 {@code LIKE} 라 하트 칸을 따로
 * 두지 않는다 — 두면 둘이 어긋난 상태가 생긴다.
 *
 * <p>종류가 빠지면 400 이다. 비워 보내는 것을 취소로 받지 않는다 — 취소는 {@code DELETE} 다.
 */
public record StoryReactionRequest(@NotNull ReactionType reaction) {
}
