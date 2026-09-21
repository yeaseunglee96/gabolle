package com.gabolle.backend.feed.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.feed.domain.CommunityFeedEntry;
import com.gabolle.backend.feed.domain.FeedBuild;
import com.gabolle.backend.feed.domain.FeedBuildStatus;
import com.gabolle.backend.feed.domain.FeedSurface;
import com.gabolle.backend.feed.domain.UserFeedEntry;
import com.gabolle.backend.feed.repository.CommunityFeedRepository;
import com.gabolle.backend.feed.repository.FeedBuildRepository;
import com.gabolle.backend.feed.repository.UserFeedRepository;

/**
 * 미리 만들어 둔 피드를 그대로 읽어 준다.
 *
 * <p>순위·제약 판정·장소 조회를 여기서 하지 않는다. 조회는 쿼리 두 번(세대 하나를 찾고
 * 그 줄을 읽는다)이 전부라 비용이 사용자 수나 온톨로지 크기에 안 따라 늘어난다.
 * 판정이나 정렬을 하나라도 더하면 그 성질이 깨진다 — 더 넣을 것은 읽을 때가 아니라
 * 만들 때 넣는다.
 */
@Service
@Profile({ "db", "dev" })
public class FeedQueryService {

	/** 상한이 없으면 요청 하나가 미리 만들어 둔 이점을 통째로 없앤다. */
	private static final int MAX_LIMIT = 50;

	private static final int DEFAULT_LIMIT = 20;

	/** 첫 장. 위치는 0 부터라 "그 앞" 은 -1 이다. */
	private static final int FIRST_PAGE_CURSOR = -1;

	private final FeedBuildRepository builds;

	private final UserFeedRepository homeEntries;

	private final CommunityFeedRepository communityEntries;

	private final Clock clock;

	public FeedQueryService(FeedBuildRepository builds, UserFeedRepository homeEntries,
			CommunityFeedRepository communityEntries, Clock clock) {
		this.builds = builds;
		this.homeEntries = homeEntries;
		this.communityEntries = communityEntries;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public FeedPage home(UUID userId, Integer limit, Integer afterPosition) {
		Optional<FeedBuild> build = currentBuild(userId, FeedSurface.HOME);
		if (build.isEmpty()) {
			return FeedPage.notBuiltYet();
		}
		int size = clampLimit(limit);
		int cursor = cursorOf(afterPosition);
		List<UserFeedEntry> rows = this.homeEntries.readPage(build.get().getBuildId(), cursor, page(size));
		return assemble(build.get(), rows.stream().map(FeedQueryService::toItem).toList(), size);
	}

	@Transactional(readOnly = true)
	public FeedPage community(UUID userId, Integer limit, Integer afterPosition) {
		Optional<FeedBuild> build = currentBuild(userId, FeedSurface.COMMUNITY);
		if (build.isEmpty()) {
			return FeedPage.notBuiltYet();
		}
		int size = clampLimit(limit);
		int cursor = cursorOf(afterPosition);
		List<CommunityFeedEntry> rows = this.communityEntries.readPage(build.get().getBuildId(), cursor, page(size));
		return assemble(build.get(), rows.stream().map(FeedQueryService::toItem).toList(), size);
	}

	private Optional<FeedBuild> currentBuild(UUID userId, FeedSurface surface) {
		return this.builds.findByUserIdAndSurfaceAndStatus(userId, surface, FeedBuildStatus.READY);
	}

	private FeedPage assemble(FeedBuild build, List<FeedItem> items, int requestedSize) {
		OffsetDateTime now = OffsetDateTime.now(this.clock);

		// 만료됐어도 내용은 그대로 낸다. 낡았다는 사실만 응답에 실어 보낸다.
		boolean stale = build.isExpiredAt(now);

		FeedPage.EmptyReason emptyReason = items.isEmpty() ? FeedPage.EmptyReason.NO_ITEMS : null;

		return new FeedPage(build.getBuildId(), items, nextCursor(items, requestedSize), stale, build.getReadyAt(),
				emptyReason);
	}

	/**
	 * 요청한 만큼 꽉 찼으면 다음 장이 있다고 가정한다. 정확히 알려면 한 줄을 더 읽어야 하고,
	 * 그 비용을 모든 장에서 치르는 대신 마지막 장 뒤에 빈 장이 한 번 오는 것을 받아들인다.
	 */
	private static Integer nextCursor(List<FeedItem> items, int requestedSize) {
		if (items.size() < requestedSize) {
			return null;
		}
		return items.get(items.size() - 1).position();
	}

	private static int clampLimit(Integer limit) {
		if (limit == null) {
			return DEFAULT_LIMIT;
		}
		return Math.max(1, Math.min(limit, MAX_LIMIT));
	}

	private static int cursorOf(Integer afterPosition) {
		return (afterPosition == null) ? FIRST_PAGE_CURSOR : afterPosition;
	}

	private static Pageable page(int size) {
		return PageRequest.of(0, size);
	}

	private static FeedItem toItem(UserFeedEntry entry) {
		return new FeedItem(entry.getPosition(), entry.getItemType().name(), entry.getItemId(), null, entry.getScore(),
				List.of(entry.getReasonCodes()), entry.getPayload());
	}

	private static FeedItem toItem(CommunityFeedEntry entry) {
		// 커뮤니티 줄은 표가 하나뿐이라 종류 칸이 없다. 응답에서만 이름을 붙인다.
		return new FeedItem(entry.getPosition(), "POST", entry.getPostId(), entry.getAuthorId(), entry.getScore(),
				List.of(entry.getReasonCodes()), entry.getPayload());
	}
}
