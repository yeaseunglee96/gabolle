package com.gabolle.backend.story.application;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.event.application.EventIngestService;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.story.domain.ReactionType;
import com.gabolle.backend.story.domain.Story;
import com.gabolle.backend.story.repository.StoryReactionRepository;
import com.gabolle.backend.story.repository.StoryRepository;

/**
 * 기록(글)에 좋아요·싫어요를 단다. 글에는 「나중에 보려고 저장」이 없고 화면의 하트가 곧 좋아요라
 * 종류가 둘뿐이고 표도 하나다.
 *
 * <p>볼 수 있는 글에만 단다. {@code findVisibleById} 는 지워졌거나 신고로 감춰진 글만 걸러 내고
 * 공개 범위는 {@link StoryVisibilityPolicy} 가 본다. 둘을 함께 걸지 않으면 남의 나만 보기 글 번호를
 * 아는 사람이 반응을 눌러 보고 200 이냐 404 냐로 그 글의 존재를 알아낼 수 있다. 그래서 볼 수 없을 때
 * 「없다」와 같은 예외를 낸다 — 다르게 내면 그 차이 자체가 신호다.
 *
 * <p>자기 글에는 못 단다. 기준은 「만든 사람」이 아니라 「함께 쓰는 사람」이다 — 작성자만 막으면
 * 둘이 서로를 초대해 놓고 서로의 글에 누르는 것과 구분이 안 된다.
 *
 * <p>이벤트는 (글, 사람, 종류)당 하나다. 그래서 잠금이 둘이다 — {@code upsert} 의 반환값이 같은 값
 * 재전송을 막고, {@code markLikeRecorded} 가 껐다 켰다를 막는다. 뒤의 것이 없으면 취소가 행을 지워서
 * 다음 좋아요가 언제나 「처음」이 되고, 껐다 켠 횟수만큼 이벤트가 쌓인다.
 */
@Service
@Profile({ "db", "dev" })
public class StoryReactionService {

	private final StoryRepository stories;

	private final StoryReactionRepository reactions;

	private final StoryVisibilityPolicy visibilityPolicy;

	private final BlockService blockService;

	private final EventIngestService events;

	private final Clock clock;

	public StoryReactionService(StoryRepository stories, StoryReactionRepository reactions,
			StoryVisibilityPolicy visibilityPolicy, BlockService blockService, EventIngestService events,
			Clock clock) {
		this.stories = stories;
		this.reactions = reactions;
		this.visibilityPolicy = visibilityPolicy;
		this.blockService = blockService;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * 반응을 단다. 이미 같은 값이면 아무것도 안 바뀐다.
	 *
	 * @throws StoryService.StoryNotFoundException 없는 글이거나 볼 수 없는 글이다. 둘을 같은 예외로 낸다
	 * @throws com.gabolle.backend.story.domain.UserBlock.BlockedByUserException 글쓴이가 나를 차단했다 —
	 *     403. 한 글을 지목해 여는 경로라 조용히 빼지 않고 알린다
	 * @throws OwnReactionNotAllowedException 내가 함께 쓰는 글이다
	 */
	@Transactional
	public void react(UUID userId, UUID storyId, ReactionType reaction) {
		requireReactable(userId, storyId);

		int changed = this.reactions.upsert(storyId, userId, reaction.name(), OffsetDateTime.now(this.clock));
		// 안 바뀌었으면(0) 여기서 끝난다. 앱의 재시도는 사건이 아니다.
		if (changed != 1) {
			return;
		}

		// 표가 바뀌었다고 이벤트를 남기는 것이 아니다. 「이 사람이 이 글에 이 종류를 처음
		// 남기는가」를 DB 에 한 번 더 묻는다. 이것이 없으면 하트를 껐다 켜는 것만으로 이벤트가
		// 쌓이고, 개인화가 그것을 행동 이력으로 읽는다.
		int firstTime = (reaction == ReactionType.LIKE)
				? this.reactions.markLikeRecorded(storyId, userId)
				: this.reactions.markDislikeRecorded(storyId, userId);
		if (firstTime == 1) {
			record(userId, storyId, reaction);
		}
	}

	/**
	 * 반응을 취소한다.
	 *
	 * <p>안 눌렀던 것을 취소해도 성공이다. 이미 취소된 뒤에 재시도가 도착하는 일이 흔하고, 그때
	 * 404 를 내면 화면은 「취소됐는데 못 취소했다고 한다」를 그린다.
	 *
	 * <p>볼 수 있는지는 여기서 안 따진다. 내가 남긴 내 행을 지우는 것이라, 글이 그 사이 나만 보기로
	 * 바뀌었거나 글쓴이가 나를 차단했어도 취소는 되어야 한다. 결과가 언제나 성공이라 존재가 새지도 않는다.
	 *
	 * <p>취소는 이벤트를 안 남긴다. 취소까지 행동 신호로 남기면 눌렀다 취소한 사람이 안 누른 사람보다
	 * 신호가 많아진다. 현재 상태는 표가 들고 있다.
	 *
	 * <p>행을 지우지 않고 종류만 비운다. 행이 사라지면 다시 누르는 것이 처음 누른 것과 구분되지 않아
	 * 이벤트가 또 나간다.
	 */
	@Transactional
	public void remove(UUID userId, UUID storyId) {
		this.reactions.clearReaction(storyId, userId, OffsetDateTime.now(this.clock));
	}

	private void requireReactable(UUID userId, UUID storyId) {
		Instant now = this.clock.instant();
		Story story = this.stories.findVisibleById(storyId)
				.orElseThrow(() -> new StoryService.StoryNotFoundException(storyId));
		if (!this.visibilityPolicy.canView(story, userId, now)) {
			throw new StoryService.StoryNotFoundException(storyId);
		}
		this.blockService.requireNotBlockedBy(story.getAuthorUserId(), userId);
		if (this.visibilityPolicy.isParticipant(story, userId)) {
			throw new OwnReactionNotAllowedException(storyId);
		}
	}

	private void record(UUID userId, UUID storyId, ReactionType reaction) {
		EventType type = (reaction == ReactionType.LIKE) ? EventType.STORY_LIKE : EventType.STORY_DISLIKE;
		// 개인화를 끈 사람은 여기서 안 걸러도 된다 — recordFromServer 안의 collectsBehaviorOf 가
		// 이미 한다. 두 곳에 두면 어긋난다.
		this.events.recordFromServer(UUID.randomUUID(), type, 1, userId, null, null,
				Map.of("storyId", storyId.toString()));
	}

	/** 내가 함께 쓰는 글에 반응하려 했다. */
	public static class OwnReactionNotAllowedException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		private final UUID storyId;

		public OwnReactionNotAllowedException(UUID storyId) {
			super("내가 쓴 기록에는 반응할 수 없습니다: " + storyId);
			this.storyId = storyId;
		}

		public UUID storyId() {
			return this.storyId;
		}
	}
}
