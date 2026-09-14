package com.gabolle.backend.story.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.story.domain.Story;
import com.gabolle.backend.story.presentation.dto.TripStoriesResponse;
import com.gabolle.backend.story.repository.StoryRepository;
import com.gabolle.backend.trip.application.TripQueryService;

/**
 * 한 여행에 달린 기록을 읽는다 — S15P21E201-829. 추억 지도({@code S15P21E201-248})가 이것을 쓴다.
 *
 * <h2>문을 두 개 지난다</h2>
 * <ol>
 *   <li><b>여행 참여자인가</b> — {@link TripQueryService#get} 이 판정한다. 회원이 아니거나 지운
 *       여행이면 존재를 감춘 404 다. 이 판정을 여기서 다시 쓰지 않고 그 메서드를 부르는 이유는
 *       그것이 여행에 닿는 공통 관문이기 때문이다 — 일정 열람·초대·공유 링크·추천이 전부 거기를
 *       지나고, 판정을 복사하면 언젠가 한쪽만 고쳐진다</li>
 *   <li><b>그 기록을 볼 수 있는가</b> — {@link StoryVisibilityPolicy#canView} 가 판정한다.
 *       같은 여행의 참여자라고 해서 남의 나만 보기 기록까지 보이면 S15P21E201-137 에서 막은
 *       구멍이 "여행에 달렸다" 는 이유로 다시 열린다</li>
 * </ol>
 *
 * <p>🔴 두 문 중 하나만으로는 부족하다. 여행 참여만 보면 남의 사적인 기록이 새고, 공개 범위만
 * 보면 여행과 무관한 사람이 그 여행의 기록 묶음을 통째로 읽는다.
 *
 * <h2>감춰진 기록은 참여자에게도 안 보인다</h2>
 * 신고로 가려진 기록({@code moderation_state != VISIBLE})은 질의 단계에서 빠진다
 * ({@code StoryRepository.findTripStories}). 상세 조회는 참여자에게만 예외를 두는데
 * ({@code StoryService.requireVisible}) 여기서는 두지 않는다 — 추억 지도는 <b>여럿이 함께 보는
 * 화면</b>이라 참여자에게 보이는 것이 곧 동행자 전원에게 보이는 것이고, 운영자가 가린 사진이
 * 그 자리에 남으면 가린 것이 아니다. 작성자가 자기 기록을 되짚는 길은 상세 조회로 남아 있다.
 *
 * @see StoryFeedService 공개 피드 — 이쪽은 커서로 이어 보고, 여행이 아니라 공개 범위로 고른다
 */
@Service
@Profile({ "db", "dev" })
public class TripStoryService {

	/**
	 * 한 번에 돌려주는 최대 개수.
	 *
	 * <p>여행 하나에 달리는 기록은 사람이 올리는 것이라 수십 건이 현실적인 상한이다. 그래도
	 * 상한을 두는 이유는 <b>상한 없는 응답을 만들지 않기 위해서</b>다 — 어떤 경로로든 한 여행에
	 * 기록이 수천 건 쌓이면 이 응답이 그만큼 커지고, 그때 느려지는 것은 이 API 를 부르는 화면이다.
	 * 이 값을 넘겨야 하는 날이 오면 그때 이어 보기를 붙인다.
	 */
	public static final int MAX_STORIES = 200;

	private final TripQueryService tripQueryService;

	private final StoryRepository storyRepository;

	private final StoryVisibilityPolicy visibilityPolicy;

	private final StoryResponseAssembler assembler;

	private final Clock clock;

	public TripStoryService(TripQueryService tripQueryService, StoryRepository storyRepository,
			StoryVisibilityPolicy visibilityPolicy, StoryResponseAssembler assembler, Clock clock) {
		this.tripQueryService = tripQueryService;
		this.storyRepository = storyRepository;
		this.visibilityPolicy = visibilityPolicy;
		this.assembler = assembler;
		this.clock = clock;
	}

	/**
	 * 그 여행에 달린, 요청자가 볼 수 있는 기록을 쓴 순서대로.
	 *
	 * @throws TripQueryService.TripNotFoundException 없는 여행이거나 요청자가 그 여행의 회원이
	 * 아니다 — 둘을 구분해 응답하지 않는다(404)
	 */
	@Transactional(readOnly = true)
	public TripStoriesResponse list(String tripId, UUID viewer) {
		this.tripQueryService.get(tripId, viewer.toString());

		Instant now = this.clock.instant();
		List<Story> rows = this.storyRepository.findTripStories(parseTripId(tripId),
				PageRequest.of(0, MAX_STORIES));

		List<Story> visible = new ArrayList<>(rows.size());
		for (Story story : rows) {
			if (this.visibilityPolicy.canView(story, viewer, now)) {
				visible.add(story);
			}
		}
		return new TripStoriesResponse(this.assembler.many(visible, viewer, now));
	}

	/**
	 * 🔴 여행 식별자는 {@code Trip} 에서 문자열이고 {@code Story.tripId} 에서는 {@code UUID} 다.
	 * 앞선 참여자 판정을 통과했으면 그 여행은 실재하므로 여기서 실패할 일이 없지만, 그래도
	 * 형식이 안 맞을 때 500 을 내지 않고 같은 404 로 답한다 — 이 경로가 존재 여부를 알려 주지
	 * 않는다는 규칙이 응답 코드 하나로 깨지지 않게 한다.
	 */
	private static UUID parseTripId(String tripId) {
		try {
			return UUID.fromString(tripId);
		}
		catch (IllegalArgumentException e) {
			throw new TripQueryService.TripNotFoundException(tripId);
		}
	}
}
