package com.gabolle.backend.moderation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.moderation.domain.StoryReport;
import com.gabolle.backend.moderation.domain.StoryReportResolution;
import com.gabolle.backend.moderation.presentation.dto.ModerationActionResponse;
import com.gabolle.backend.moderation.presentation.dto.ModerationQueueItemResponse;
import com.gabolle.backend.moderation.presentation.dto.ModerationQueueResponse;
import com.gabolle.backend.moderation.repository.StoryReportRepository;
import com.gabolle.backend.story.application.StorageCleanupService;
import com.gabolle.backend.story.domain.Story;
import com.gabolle.backend.story.domain.StoryImage;
import com.gabolle.backend.story.domain.UploadedImage;
import com.gabolle.backend.story.repository.StoryImageRepository;
import com.gabolle.backend.story.repository.StoryRepository;
import com.gabolle.backend.story.repository.UploadedImageRepository;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.repository.AppUserRepository;

/**
 * 신고 검토 목록과 삭제·기각 — S15P21E201-267.
 *
 * <h2>🔴 N+1 을 만들지 않는다</h2>
 * 목록은 신고 → 기록 → 작성자 이름 순으로 필요하다. {@link #list} 는 이것을 세 번의 질의로 끝낸다.
 * <ol>
 *   <li>{@link StoryReportRepository#findPendingOldestFirst} 로 미처리 신고를 오래된 순으로 최대
 *       {@code limit} 건 읽어, 이번 페이지에 실릴 {@code storyId} 순서를 정한다(한 기록의 신고 중
 *       가장 오래된 것이 그 기록의 순서를 정한다)</li>
 *   <li>그 {@code storyId} 들로 {@link StoryReportRepository#findPendingByStoryIdIn} 을 <b>한 번</b> 불러
 *       각 기록의 미처리 신고를 전부(1번 조회에서 limit 에 잘렸을 수 있는 것까지) 모은다</li>
 *   <li>{@link StoryRepository#findAllById} 와 {@link AppUserRepository#findAllById} 를 각각 한 번씩
 *       불러 기록 본문과 작성자 이름을 채운다</li>
 * </ol>
 * {@code itinerary/application/ActorNames.java} 와 같은 패턴이다(사용자 이름을 IN 질의 한 번으로 모아
 * 읽는다). 그 클래스를 그대로 가져다 쓰지 않은 이유는 그것이 다른 바운디드 컨텍스트
 * ({@code itinerary}) 소속이라 이 패키지가 거기 의존하게 만들고 싶지 않아서다 — 패턴만 재사용했다.
 *
 * <h2>🔴 처리는 그 기록의 미처리 신고 전부를 함께 닫는다</h2>
 * {@link #remove}·{@link #dismiss} 는 {@link StoryReportRepository#findPendingByStoryId} 로 그 기록의
 * 미처리 신고를 전부 가져와 한 번에 {@link StoryReport#resolve} 한다. 하나만 처리하면 나머지가 큐에
 * 남아 운영자가 같은 기록을 또 보게 된다.
 *
 * <h2>🔴 "이미 처리됨" 을 표현하는 방법</h2>
 * 처리할 미처리 신고가 하나도 없는 기록에 {@link #remove}·{@link #dismiss} 를 부르면
 * {@link NoPendingReportsException}(409)을 던진다. 이 기능은 <b>신고된 기록을 처리하는</b> 화면이라
 * 신고가 하나도 없는(또는 이미 다 처리된) 기록을 다시 처리하는 것은 실수이거나 중복 클릭이다.
 * {@link StoryReport#resolve} 가 이미 처리된 신고 한 건에 대해 갖는 것과 같은 뜻의 방어를
 * 기록(여러 신고의 묶음) 단위로 올린 것이다.
 */
@Service
@Profile({ "db", "dev" })
public class ModerationQueueService {

	/** 목록 한 페이지의 기본 신고 행 수. 완료 기준에 페이지 크기가 없어 피드 기본값(story 쪽)과 맞췄다. */
	static final int DEFAULT_LIMIT = 50;

	static final int MAX_LIMIT = 200;

	/** {@code storage_cleanup_queue.reason} 은 CHECK 제약이 없는 자유 문자열이다(표 참고). */
	static final String REASON_STORY_REMOVED_BY_MODERATOR = "STORY_REMOVED_BY_MODERATOR";

	private final StoryReportRepository storyReportRepository;

	private final StoryRepository storyRepository;

	private final StoryImageRepository storyImageRepository;

	private final UploadedImageRepository uploadedImageRepository;

	private final AppUserRepository appUserRepository;

	private final StorageCleanupService storageCleanupService;

	private final Clock clock;

	public ModerationQueueService(StoryReportRepository storyReportRepository, StoryRepository storyRepository,
			StoryImageRepository storyImageRepository, UploadedImageRepository uploadedImageRepository,
			AppUserRepository appUserRepository, StorageCleanupService storageCleanupService, Clock clock) {
		this.storyReportRepository = storyReportRepository;
		this.storyRepository = storyRepository;
		this.storyImageRepository = storyImageRepository;
		this.uploadedImageRepository = uploadedImageRepository;
		this.appUserRepository = appUserRepository;
		this.storageCleanupService = storageCleanupService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ModerationQueueResponse list(Integer requestedLimit) {
		int limit = clampLimit(requestedLimit);
		Instant now = this.clock.instant();

		List<StoryReport> firstPage = this.storyReportRepository.findPendingOldestFirst(Limit.of(limit));
		if (firstPage.isEmpty()) {
			return new ModerationQueueResponse(List.of());
		}

		// 오래된 신고가 먼저 나오는 순서 그대로 storyId 를 모은다 — 한 기록이 이 목록에 처음 나타나는
		// 자리가 곧 그 기록의 가장 오래된 신고 자리다. LinkedHashSet 이 그 순서를 그대로 지킨다.
		Set<UUID> storyIds = new LinkedHashSet<>();
		for (StoryReport report : firstPage) {
			storyIds.add(report.getStoryId());
		}
		List<UUID> orderedStoryIds = new ArrayList<>(storyIds);

		List<StoryReport> allPending = this.storyReportRepository.findPendingByStoryIdIn(orderedStoryIds);
		Map<UUID, List<StoryReport>> byStory = new LinkedHashMap<>();
		for (UUID id : orderedStoryIds) {
			byStory.put(id, new ArrayList<>());
		}
		for (StoryReport report : allPending) {
			byStory.get(report.getStoryId()).add(report);
		}

		Map<UUID, Story> stories = new LinkedHashMap<>();
		for (Story story : this.storyRepository.findAllById(orderedStoryIds)) {
			stories.put(story.getStoryId(), story);
		}

		Set<UUID> authorIds = new LinkedHashSet<>();
		for (Story story : stories.values()) {
			authorIds.add(story.getAuthorUserId());
		}
		Map<UUID, String> authorNames = new LinkedHashMap<>();
		if (!authorIds.isEmpty()) {
			for (AppUser user : this.appUserRepository.findAllById(authorIds)) {
				authorNames.put(user.getUserId(), user.getDisplayName());
			}
		}

		List<ModerationQueueItemResponse> items = new ArrayList<>();
		for (UUID storyId : orderedStoryIds) {
			Story story = stories.get(storyId);
			if (story == null) {
				// 신고 행은 있는데 기록 행이 없다 — FK 가 story_id 를 CASCADE 로 걸어 두어 실무에서는
				// 안 생기지만, 방어적으로 건너뛴다(운영자에게 빈 줄을 보여주지 않는다).
				continue;
			}
			List<StoryReport> reports = byStory.get(storyId);
			items.add(toItem(story, reports, authorNames.get(story.getAuthorUserId()), now));
		}
		return new ModerationQueueResponse(items);
	}

	private ModerationQueueItemResponse toItem(Story story, List<StoryReport> reports, String authorName,
			Instant now) {
		Set<String> reasons = new LinkedHashSet<>();
		for (StoryReport report : reports) {
			reasons.add(report.getReason().name());
		}
		Instant oldestReportedAt = reports.get(0).getCreatedAt();
		long elapsedSeconds = Math.max(0, Duration.between(oldestReportedAt, now).getSeconds());
		return new ModerationQueueItemResponse(story.getStoryId(), authorName, story.getBody(),
				List.copyOf(reasons), reports.size(), oldestReportedAt, elapsedSeconds);
	}

	/**
	 * 삭제로 처리한다. 그 기록의 미처리 신고를 전부 닫고, 기록을 모두에게서 감추고, 딸린 사진을
	 * 저장소에서 지운다.
	 *
	 * <p>🔴 사진 삭제는 {@link StorageCleanupService#deleteOrEnqueue} 로 한다 —
	 * {@code StoryService.delete} 가 지나는 길과 같다. 실패해도 예외를 던지지 않아 이 트랜잭션이
	 * 롤백되지 않고, 못 지운 키는 {@code storage_cleanup_queue} 에 남아 나중에 다시 지워진다.
	 */
	@Transactional
	public ModerationActionResponse remove(UUID storyId, UUID adminUserId) {
		Instant now = this.clock.instant();
		Story story = this.storyRepository.findActiveById(storyId)
				.orElseThrow(() -> new StoryNotFoundException(storyId));
		List<StoryReport> pending = this.storyReportRepository.findPendingByStoryId(storyId);
		if (pending.isEmpty()) {
			throw new NoPendingReportsException(storyId);
		}
		for (StoryReport report : pending) {
			report.resolve(StoryReportResolution.REMOVED, adminUserId, now);
		}
		story.markRemovedByModerator();
		purgeImages(storyId, now);
		return new ModerationActionResponse(storyId, pending.size());
	}

	/** 기각한다. 그 기록의 미처리 신고를 전부 닫고 기록을 다시 보이게 한다. 사진은 건드리지 않는다. */
	@Transactional
	public ModerationActionResponse dismiss(UUID storyId, UUID adminUserId) {
		Instant now = this.clock.instant();
		Story story = this.storyRepository.findActiveById(storyId)
				.orElseThrow(() -> new StoryNotFoundException(storyId));
		List<StoryReport> pending = this.storyReportRepository.findPendingByStoryId(storyId);
		if (pending.isEmpty()) {
			throw new NoPendingReportsException(storyId);
		}
		for (StoryReport report : pending) {
			report.resolve(StoryReportResolution.DISMISSED, adminUserId, now);
		}
		story.restoreVisibility();
		return new ModerationActionResponse(storyId, pending.size());
	}

	/** {@code StoryService.delete} 의 사진 정리 부분과 같은 절차 — 직접 지우지 않고 재사용한다. */
	private void purgeImages(UUID storyId, Instant now) {
		List<StoryImage> images = this.storyImageRepository.findByStoryIdOrderByPositionAsc(storyId);
		if (images.isEmpty()) {
			return;
		}
		List<UUID> uploadIds = images.stream().map(StoryImage::getUploadedImageId).toList();
		for (UploadedImage upload : this.uploadedImageRepository.findByUploadedImageIdIn(uploadIds)) {
			upload.markDeleted(now);
			this.storageCleanupService.deleteOrEnqueue(upload.getStorageKey(), REASON_STORY_REMOVED_BY_MODERATOR);
		}
	}

	static int clampLimit(Integer requested) {
		if (requested == null || requested <= 0) {
			return DEFAULT_LIMIT;
		}
		return Math.min(requested, MAX_LIMIT);
	}

	/** 대상 기록이 없다(운영자용 — 존재를 감출 필요는 없지만 형태를 story 쪽과 맞췄다). */
	public static class StoryNotFoundException extends RuntimeException {

		private final UUID storyId;

		public StoryNotFoundException(UUID storyId) {
			super("기록을 찾을 수 없습니다.");
			this.storyId = storyId;
		}

		public UUID storyId() {
			return this.storyId;
		}
	}

	/** 처리할 미처리 신고가 없다 — 이미 처리됐거나 애초에 신고가 없다. 409. */
	public static class NoPendingReportsException extends RuntimeException {

		private final UUID storyId;

		public NoPendingReportsException(UUID storyId) {
			super("처리할 미처리 신고가 없습니다: " + storyId);
			this.storyId = storyId;
		}

		public UUID storyId() {
			return this.storyId;
		}
	}
}
