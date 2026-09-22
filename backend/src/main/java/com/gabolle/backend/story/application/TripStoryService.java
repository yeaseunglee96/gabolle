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
 * 한 여행에 달린 기록을 읽는다. 추억 지도가 이것을 쓴다.
 *
 * <p>문을 두 개 지난다. 여행 참여자인지는 {@link TripQueryService#get} 이 판정하고(여행에 닿는 공통
 * 관문이라 판정을 복사하지 않는다), 그 기록을 볼 수 있는지는 {@link StoryVisibilityPolicy#canView}
 * 가 판정한다. 하나만으로는 부족하다 — 여행 참여만 보면 남의 나만 보기 기록이 새고, 공개 범위만
 * 보면 여행과 무관한 사람이 그 여행의 기록 묶음을 통째로 읽는다.
 *
 * <p>신고로 가려진 기록은 질의 단계에서 빠지고, 상세 조회와 달리 참여자에게도 예외를 두지 않는다 —
 * 추억 지도는 동행자 전원이 함께 보는 화면이라 가린 것이 그 자리에 남으면 가린 것이 아니다.
 *
 * @see StoryFeedService 공개 피드 — 이쪽은 커서로 이어 보고, 여행이 아니라 공개 범위로 고른다
 */
@Service
@Profile({ "db", "dev" })
public class TripStoryService {

	/**
	 * 한 번에 돌려주는 최대 개수. 여행 하나에 달리는 기록은 수십 건이 현실적인 상한이라 여유가 있고,
	 * 상한 없는 응답을 만들지 않으려고 둔다. 이 값을 넘겨야 하는 날이 오면 이어 보기를 붙인다.
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
	 * 여행 식별자는 {@code Trip} 에서 문자열이고 {@code Story.tripId} 에서는 {@code UUID} 다. 형식이
	 * 안 맞아도 500 이 아니라 404 로 답한다 — 응답 코드 하나로 존재 여부가 새지 않게.
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
