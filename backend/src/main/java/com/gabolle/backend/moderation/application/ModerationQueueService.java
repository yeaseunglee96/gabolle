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

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.moderation.domain.StoryRemovedByModerator;
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
 * 신고 검토 목록과 삭제·기각.
 *
 * {@link #list} 는 질의 세 번으로 끝낸다 — 미처리 신고를 오래된 순으로 읽어 기록 순서를 정하고,
 * 그 {@code storyId} 들로 미처리 신고를 한 번에 모으고, 기록과 작성자 이름을 각각 한 번씩 읽는다.
 * 기록마다 따로 부르면 목록 길이만큼 질의가 늘어난다.
 *
 * {@link #remove}·{@link #dismiss} 는 그 기록의 미처리 신고를 전부 함께 닫는다. 하나만 처리하면
 * 나머지가 큐에 남아 운영자가 같은 기록을 또 보게 된다. 닫을 미처리 신고가 하나도 없으면
 * {@link NoPendingReportsException}(409) 이다 — 중복 클릭이거나 다른 운영자가 이미 처리한 것이다.
 */
@Service
@Profile({ "db", "dev" })
public class ModerationQueueService {

	/** 목록 한 페이지의 기본 신고 행 수. 피드 기본값(story 쪽)과 맞췄다. */
	static final int DEFAULT_LIMIT = 50;

	static final int MAX_LIMIT = 200;

	/** {@code storage_cleanup_queue.reason} 은 CHECK 제약이 없는 자유 문자열이다. */
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
			AppUserRepository appUserRepository, StorageCleanupService storageCleanupService,
			ApplicationEventPublisher events, Clock clock) {
		this.storyReportRepository = storyReportRepository;
		this.storyRepository = storyRepository;
		this.storyImageRepository = storyImageRepository;
		this.uploadedImageRepository = uploadedImageRepository;
		this.appUserRepository = appUserRepository;
		this.storageCleanupService = storageCleanupService;
		this.events = events;
		this.clock = clock;
	}

	private final ApplicationEventPublisher events;

	/**
	 * 알림 메일에 싣는 본문의 길이(자). 어느 기록인지 알아볼 만큼만 싣는다 — 길게 실으면 신고까지
	 * 받은 글이 메일 서버와 우편함에 한 벌 더 남는다.
	 */
	static final int EXCERPT_LENGTH = 60;

	@Transactional(readOnly = true)
	public ModerationQueueResponse list(Integer requestedLimit) {
		int limit = clampLimit(requestedLimit);
		Instant now = this.clock.instant();

		List<StoryReport> firstPage = this.storyReportRepository.findPendingOldestFirst(Limit.of(limit));
		if (firstPage.isEmpty()) {
			return new ModerationQueueResponse(List.of());
		}

		// 한 기록이 이 목록에 처음 나타나는 자리가 곧 그 기록의 가장 오래된 신고 자리다.
		// LinkedHashSet 이어야 그 순서가 유지된다.
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
				// 신고 행은 있는데 기록 행이 없다 — FK 가 CASCADE 라 사실상 안 생긴다. 빈 줄을
				// 보여주지 않으려고 건너뛴다.
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
	 * 저장소에서 지운다. 사진 삭제는 실패해도 예외를 던지지 않아 이 트랜잭션이 롤백되지 않는다 —
	 * 못 지운 키는 {@code storage_cleanup_queue} 에 남아 나중에 다시 지워진다.
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
		publishRemoved(story);
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

	/**
	 * 지웠다는 사실만 사건으로 띄운다 — 메일은 듣는 쪽
	 * ({@code auth.service.StoryRemovalNotifier})이 보낸다. 여기서 직접 보내면 발송기 빈이 없는
	 * 기록·신고 테스트 슬라이스가 컨텍스트 로딩부터 깨진다.
	 *
	 * 듣는 쪽은 커밋 뒤에 돌므로({@code AFTER_COMMIT}) 영속성 컨텍스트가 닫혀 있다 — 필요한 값을
	 * 지금 뽑아 사건에 담는다. 발송을 커밋 앞에 두면 메일 서버 장애가 삭제를 롤백시킨다.
	 */
	private void publishRemoved(Story story) {
		this.events.publishEvent(new StoryRemovedByModerator(story.getStoryId(), story.getAuthorUserId(),
				excerptOf(story.getBody())));
	}

	/** 앞 {@link #EXCERPT_LENGTH} 자. 잘렸으면 그 사실이 보이게 말줄임을 붙인다. */
	static String excerptOf(String body) {
		if (body == null) {
			return "";
		}
		String trimmed = body.strip();
		if (trimmed.length() <= EXCERPT_LENGTH) {
			return trimmed;
		}
		return trimmed.substring(0, EXCERPT_LENGTH) + "…";
	}

	/** {@code StoryService.delete} 의 사진 정리와 같은 절차를 지나야 한다. */
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

	/** 처리할 미처리 신고가 없다 — 409 로 나간다. */
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
