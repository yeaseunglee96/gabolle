package com.gabolle.backend.story.application;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.story.domain.StorySave;
import com.gabolle.backend.story.repository.StoryRepository;
import com.gabolle.backend.story.repository.StorySaveRepository;

/**
 * 기록(글) 저장(북마크). 좋아요·싫어요와 별도 표라, 한 글에 반응과 저장을 동시에 가질 수 있다.
 *
 * <p>자기 글도 저장할 수 있다. 반응은 인기순 조작을 막으려 자기 글을 금지하지만, 저장은 순위에
 * 반영되지 않는 개인 북마크다.
 */
@Service
@Profile({ "db", "dev" })
public class StorySaveService {

	/** 한 번에 돌려주는 최대 개수 — {@code SavedPlaceService.MAX_ITEMS}와 같은 상한. */
	public static final int MAX_ITEMS = 500;

	private final StoryRepository stories;

	private final StorySaveRepository saves;

	private final StoryVisibilityPolicy visibilityPolicy;

	private final BlockService blockService;

	private final Clock clock;

	public StorySaveService(StoryRepository stories, StorySaveRepository saves,
			StoryVisibilityPolicy visibilityPolicy, BlockService blockService, Clock clock) {
		this.stories = stories;
		this.saves = saves;
		this.visibilityPolicy = visibilityPolicy;
		this.blockService = blockService;
		this.clock = clock;
	}

	/**
	 * 내가 저장한 것. 상한까지만 돌려주고, 더 있으면 그 사실을 함께 알린다 — {@code
	 * SavedPlaceService#list}와 같은 이유.
	 */
	@Transactional(readOnly = true)
	public Page list(UUID userId) {
		List<StorySave> found = this.saves.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, MAX_ITEMS + 1));
		boolean hasMore = found.size() > MAX_ITEMS;
		return new Page(hasMore ? found.subList(0, MAX_ITEMS) : found, hasMore);
	}

	/**
	 * 저장한다. 몇 번을 보내도 같다.
	 *
	 * @throws StoryService.StoryNotFoundException 없는 글이거나 볼 수 없는 글이다 — 반응과 같은
	 *     이유로 「없다」와 「못 본다」를 같은 예외로 낸다
	 * @throws com.gabolle.backend.story.domain.UserBlock.BlockedByUserException 글쓴이가 나를 차단했다
	 */
	@Transactional
	public void save(UUID userId, UUID storyId) {
		requireSavable(userId, storyId);
		this.saves.insertIfAbsent(UUID.randomUUID(), userId, storyId, OffsetDateTime.now(this.clock));
	}

	/**
	 * 저장을 취소한다. 안 저장했던 것을 취소해도 성공이다 — {@code SavedPlaceService#remove}와
	 * 같은 이유. 볼 수 있는지는 여기서 안 따진다 — 내가 남긴 내 행을 지우는 것이다.
	 */
	@Transactional
	public void remove(UUID userId, UUID storyId) {
		this.saves.deleteByUserIdAndStoryId(userId, storyId);
	}

	private void requireSavable(UUID userId, UUID storyId) {
		Instant now = this.clock.instant();
		var story = this.stories.findVisibleById(storyId)
				.orElseThrow(() -> new StoryService.StoryNotFoundException(storyId));
		if (!this.visibilityPolicy.canView(story, userId, now)) {
			throw new StoryService.StoryNotFoundException(storyId);
		}
		this.blockService.requireNotBlockedBy(story.getAuthorUserId(), userId);
	}

	/**
	 * 잘라 온 목록과, 잘렸는지 여부.
	 *
	 * @param hasMore 상한에 걸려 더 있는데 안 보냈다
	 */
	public record Page(List<StorySave> items, boolean hasMore) {
	}
}
