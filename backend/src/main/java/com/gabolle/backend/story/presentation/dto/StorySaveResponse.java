package com.gabolle.backend.story.presentation.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.gabolle.backend.story.application.StorySaveService;
import com.gabolle.backend.story.domain.StorySave;

/**
 * 저장한 기록 하나. 글 내용은 싣지 않는다 — 본문·사진을 여기 복사하면 글이 고쳐지거나 지워졌을 때
 * 한쪽만 낡는다. 화면은 {@code GET /api/v1/stories/{id}} 로 글을 따로 부른다.
 */
public record StorySaveResponse(UUID storyId, OffsetDateTime savedAt) {

	public static StorySaveResponse of(StorySave saved) {
		return new StorySaveResponse(saved.getStoryId(), saved.getCreatedAt());
	}

	/**
	 * 내가 저장한 것. 모양은 {@code SavedPlaceResponse.Page} 와 같게 뒀다 — 목록 응답마다 모양이 다르면
	 * 화면이 경로마다 다르게 읽어야 한다.
	 *
	 * @param hasMore 상한에 걸려 더 있는데 안 보냈다
	 */
	public record Page(List<StorySaveResponse> items, int count, boolean hasMore) {

		public static Page of(StorySaveService.Page page) {
			List<StorySaveResponse> items = page.items().stream().map(StorySaveResponse::of).toList();
			return new Page(items, items.size(), page.hasMore());
		}
	}
}
