package com.gabolle.backend.story.presentation.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;

/**
 * 서버는 목록의 각 사용자가 그 기록에 붙은 여행의 동행자인지 검사한다 — 이 검사가 없으면 번호(UUID)만
 * 알면 남을 아무 기록에나 끌어들일 수 있다.
 */
public record AddStoryCoauthorsRequest(@NotEmpty List<UUID> userIds) {
}
