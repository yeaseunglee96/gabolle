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
 * 미리 만들어 둔 피드를 그대로 읽어 준다 (S15P21E201-580).
 *
 * <h2>🔴 이 클래스가 <b>하지 않는</b> 일이 이 클래스의 정의다</h2>
 *
 * <ul>
 * <li><b>순위를 매기지 않는다.</b> 순서는 만들 때 정해졌고 여기서는 읽기만 한다</li>
 * <li><b>제약을 판정하지 않는다.</b> 알레르기·휠체어 판정은 만들 때 이미 끝났다</li>
 * <li><b>장소·글을 조회하지 않는다.</b> 화면에 필요한 값은 줄 안에 사본으로 들어 있다</li>
 * <li><b>온톨로지를 훑지 않는다.</b> 그게 이 티켓의 목적이다</li>
 * </ul>
 *
 * 그래서 조회는 <b>표 두 개, 쿼리 두 번</b>이 전부다 — 세대 하나를 찾고, 그 줄을 읽는다.
 * 이 비용은 사용자 수에도 온톨로지 크기에도 안 따라 늘어난다.
 *
 * <p>여기에 판정이나 정렬을 하나라도 추가하면 그 성질이 깨진다. 무언가를 더 넣고
 * 싶어지면 <b>읽을 때가 아니라 만들 때</b> 넣는 것이 이 설계의 규칙이다.
 */
@Service
@Profile({ "db", "dev" })
public class FeedQueryService {

	/**
	 * 한 번에 최대 몇 줄까지.
	 *
	 * <p>🔴 상한을 두는 이유 — 부르는 쪽이 {@code limit=100000} 을 보내면 미리 만들어 둔
	 * 이점이 그 요청 하나로 사라진다. 저장은 싸도 <b>내보내는 것은 안 싸다.</b>
	 */
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

	/** 앱을 켰을 때 보여 줄 것. */
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

	/** 커뮤니티에 들어갔을 때 보여 줄 것. */
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

		// 🔴 만료됐어도 그대로 낸다. 낡은 화면이 빈 화면보다 낫다 — 사람은 어제 추천이라도
		//    볼 수 있으면 앱을 쓰고, 빈 화면을 보면 앱이 고장 났다고 생각한다.
		//    대신 낡았다는 사실을 숨기지 않고 응답에 실어 보낸다.
		boolean stale = build.isExpiredAt(now);

		FeedPage.EmptyReason emptyReason = items.isEmpty() ? FeedPage.EmptyReason.NO_ITEMS : null;

		return new FeedPage(build.getBuildId(), items, nextCursor(items, requestedSize), stale, build.getReadyAt(),
				emptyReason);
	}

	/**
	 * 다음 장을 부를 커서.
	 *
	 * <p>🔴 요청한 만큼 꽉 찼으면 다음이 있다고 <b>가정한다.</b> 정확히 알려면 한 줄을 더
	 * 읽어 봐야 하는데, 그 한 번의 낭비를 마지막 장에서만 치르는 대신 모든 장에서 치르게
	 * 된다. 마지막 장 다음에 빈 장이 한 번 오는 것은 앱이 감당할 수 있는 오차다.
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
		// 커뮤니티 줄의 종류는 표가 하나뿐이라 칸으로 두지 않았다. 응답에서만 이름을 붙인다.
		return new FeedItem(entry.getPosition(), "POST", entry.getPostId(), entry.getAuthorId(), entry.getScore(),
				List.of(entry.getReasonCodes()), entry.getPayload());
	}
}
