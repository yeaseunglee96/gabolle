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
 * 기록(글)에 좋아요·싫어요를 단다.
 *
 * <h2>🔴 하트는 좋아요의 다른 이름이다</h2>
 *
 * 장소 쪽은 감정({@code PLACE_LIKE}·{@code PLACE_DISLIKE})과 저장({@code saved_place} 하트)이
 * 따로지만, 글에는 「나중에 보려고 저장」이 없고 화면의 하트가 곧 좋아요다. 그래서 종류가
 * 둘뿐이고 표도 하나다.
 *
 * <h2>🔴 볼 수 있는 글에만 단다 — 여기가 이 클래스에서 제일 쉽게 새는 자리였다</h2>
 *
 * {@code findVisibleById} 는 <b>지워졌거나 신고로 감춰진 글</b>만 걸러 낸다. 공개 범위(나만
 * 보기·팔로워 전용)는 그 질의가 모른다 — 그 판정은 {@link StoryVisibilityPolicy} 가 한다.
 * 처음 쓸 때 그것을 빼먹었고, 그러면 <b>남의 나만 보기 글 번호를 아는 사람이 반응을 눌러 보고
 * 200 이냐 404 냐로 그 글의 존재를 알아낼 수 있다.</b> {@code StoryReportService} 가 막으려던
 * 것과 같은 샛길이다(S15P21E201-254). 그래서 볼 수 없으면 <b>「없다」와 같은 예외</b>를 낸다 —
 * 다르게 내면 그 차이 자체가 신호다.
 *
 * <h2>🔴 자기 글에는 못 단다 — 「만든 사람」이 아니라 「함께 쓰는 사람」이 기준이다</h2>
 *
 * 인기순이 붙는 순간 자기 글을 올리는 길이 되기 때문이다. 공동 작성자도 막는다 — 작성자만
 * 막으면 <b>둘이 서로를 초대해 놓고 서로의 글에 누르는 것</b>과 구분이 안 되는데, 그건 사실상
 * 같은 사람이 자기 글을 올리는 것이다. 화면에서 막는 것으로는 부족하다 — 막는 쪽이 화면뿐이면
 * 요청을 직접 만들어 보내는 것으로 지나간다.
 *
 * <h2>🔴 같은 값을 두 번 보내면 이벤트를 안 남긴다</h2>
 *
 * 앱의 재시도와 사람이 두 번 마음을 정한 것은 다르다. 그 판정을
 * {@code StoryReactionRepository.upsert} 가 <b>DB 안에서 원자적으로</b> 하고, 여기서는 그
 * 반환값으로만 이벤트를 남긴다 — {@code SavedPlaceService} 가 {@code insertIfAbsent} 의
 * 반환값으로 같은 판단을 하는 것과 같다(S15P21E201-1037).
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
	 * @throws StoryService.StoryNotFoundException 없는 글이거나 볼 수 없는 글이다. 🔴 「없다」와 「못 본다」를
	 *     같은 예외로 낸다 — 다르게 내면 그 차이가 「있는데 너는 못 본다」는 신호가 된다
	 * @throws com.gabolle.backend.story.domain.UserBlock.BlockedByUserException 글쓴이가 나를 차단했다 —
	 *     403. 한 글을 지목해 여는 경로라 조용히 빼지 않고 알린다({@code BlockService.requireNotBlockedBy})
	 * @throws OwnReactionNotAllowedException 내가 함께 쓰는 글이다
	 */
	@Transactional
	public void react(UUID userId, UUID storyId, ReactionType reaction) {
		requireReactable(userId, storyId);

		int changed = this.reactions.upsert(storyId, userId, reaction.name(), OffsetDateTime.now(this.clock));

		// 🔴 안 바뀌었으면(0) 이벤트가 없다. 재시도가 신호를 부풀리면 인기순이 그만큼 틀린다.
		if (changed == 1) {
			record(userId, storyId, reaction);
		}
	}

	/**
	 * 반응을 취소한다.
	 *
	 * <p>🔴 <b>안 눌렀던 것을 취소해도 성공이다.</b> 이미 취소된 뒤에 재시도가 도착하는 일이
	 * 흔하고, 그때 404 를 내면 화면은 「취소됐는데 못 취소했다고 한다」를 그린다.
	 *
	 * <p>🔴 <b>볼 수 있는지는 여기서 안 따진다.</b> 지우는 것은 <b>내가 남긴 내 행</b>을 지우는
	 * 것이라, 글이 그 사이 나만 보기로 바뀌었거나 글쓴이가 나를 차단했어도 취소는 되어야 한다.
	 * 여기서 막으면 <b>한 번 누른 사람이 영영 못 무르는</b> 상태가 만들어진다. 볼 수 없는 글의
	 * 번호로 아무거나 불러 봐도 결과가 언제나 같으므로(성공) 존재가 새지도 않는다.
	 *
	 * <p>🔴 <b>취소는 이벤트를 안 남긴다.</b> 「좋아요를 눌렀다」는 일어난 사건이고, 취소는
	 * 그 사건을 되돌리는 것이 아니라 현재 상태를 바꾸는 것이다. 취소까지 행동 신호로 남기면
	 * 눌렀다 취소한 사람이 안 누른 사람보다 신호가 많아진다. 현재 상태는 표가 들고 있다.
	 */
	@Transactional
	public void remove(UUID userId, UUID storyId) {
		this.reactions.deleteByIdStoryIdAndIdUserId(storyId, userId);
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
		// 🔴 개인화를 끈 사람은 여기서 안 걸러도 된다 — recordFromServer 안의
		//    collectsBehaviorOf 가 이미 한다(S15P21E201-549). 두 곳에 두면 어긋난다.
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
