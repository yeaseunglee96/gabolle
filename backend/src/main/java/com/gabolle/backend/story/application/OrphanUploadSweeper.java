package com.gabolle.backend.story.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.story.domain.StorageCleanupEntry;
import com.gabolle.backend.story.domain.UploadedImage;
import com.gabolle.backend.story.domain.UploadedVideo;
import com.gabolle.backend.story.repository.UploadedImageRepository;
import com.gabolle.backend.story.repository.UploadedVideoRepository;
import com.gabolle.backend.story.storage.StorageProperties;

/**
 * 어디에도 안 붙은 업로드를 치운다. 올리기와 기록 만들기가 두 요청이라 그 사이에 사람이
 * 나가면 파일만 남는다.
 *
 * <p>업로드가 붙는 자리는 둘이다. 동영상 썸네일은 {@code story_image} 에 들어가지 않으므로
 * 거기만 보면 붙어 있는 썸네일까지 고아로 판정한다. 질의
 * {@code UploadedImageRepository.findOrphansOlderThan} 이 두 자리를 다 본다 — 붙는 자리가
 * 늘어나면 그 질의도 같이 늘려야 한다.
 *
 * <p>{@code enabled=false} 여도 청소기는 돌고, 지우는 대신 지웠을 개수만 로그에 남긴다.
 *
 * <p>삭제는 {@code deletedAt} 을 찍고 파일을 {@link StorageCleanupService#deleteOrEnqueue}
 * 로 보내는 것으로 끝낸다 — 기록 삭제·계정 탈퇴와 같은 길이다.
 */
@Component
@Profile({ "db", "dev" })
public class OrphanUploadSweeper {

	private static final Logger log = LoggerFactory.getLogger(OrphanUploadSweeper.class);

	private final UploadedImageRepository uploadedImageRepository;

	private final UploadedVideoRepository uploadedVideoRepository;

	private final StorageCleanupService storageCleanupService;

	private final StorageProperties storageProperties;

	private final Clock clock;

	public OrphanUploadSweeper(UploadedImageRepository uploadedImageRepository,
			UploadedVideoRepository uploadedVideoRepository, StorageCleanupService storageCleanupService,
			StorageProperties storageProperties, Clock clock) {
		this.uploadedImageRepository = uploadedImageRepository;
		this.uploadedVideoRepository = uploadedVideoRepository;
		this.storageCleanupService = storageCleanupService;
		this.storageProperties = storageProperties;
		this.clock = clock;
	}

	/**
	 * 기본값을 인라인과 {@code application.properties} 양쪽에 둔다. {@code @Scheduled} 의
	 * {@code cron} 은 자리표시자를 프로퍼티 소스에서 직접 찾으므로 둘 다 없으면 기동이 실패한다.
	 *
	 * <p>개인정보 정리(19:00 UTC)·메뉴판 청소(18:20 UTC)와 시각을 겹치지 않게 뒀다.
	 */
	@Scheduled(cron = "${gabolle.storage.orphan-cleanup.cron:0 40 18 * * *}", zone = "UTC")
	public void runScheduled() {
		sweep();
	}

	/**
	 * 한 판 치운다. 시험과 수동 실행이 cron 을 거치지 않고 바로 부를 수 있게 따로 열어 둔다.
	 *
	 * @return 지운 수. 세기만 하는 모드에서는 지웠을 수
	 */
	@Transactional
	public SweepResult sweep() {
		StorageProperties.OrphanCleanup settings = this.storageProperties.getOrphanCleanup();
		Instant cutoff = this.clock.instant().minus(settings.getRetentionHours(), ChronoUnit.HOURS);
		PageRequest limit = PageRequest.ofSize(settings.getBatchLimit());

		List<UploadedImage> images = this.uploadedImageRepository.findOrphansOlderThan(cutoff, limit);
		List<UploadedVideo> videos = this.uploadedVideoRepository.findOrphansOlderThan(cutoff, limit);

		long bytes = images.stream().mapToLong(UploadedImage::getByteSize).sum()
				+ videos.stream().mapToLong(UploadedVideo::getByteSize).sum();

		if (!settings.isEnabled()) {
			// 지우지 않는다. 숫자만 남긴다.
			log.info("고아 업로드 청소(세기만): 지웠을 것 사진 {}개 · 동영상 {}개 · {}바이트. "
					+ "실제로 지우려면 gabolle.storage.orphan-cleanup.enabled=true",
					images.size(), videos.size(), bytes);
			return new SweepResult(images.size(), videos.size(), bytes, false);
		}

		Instant now = this.clock.instant();
		for (UploadedImage image : images) {
			image.markDeleted(now);
			this.storageCleanupService.deleteOrEnqueue(image.getStorageKey(), StorageCleanupEntry.REASON_STORY_DELETED);
		}
		for (UploadedVideo video : videos) {
			video.markDeleted(now);
			this.storageCleanupService.deleteOrEnqueue(video.getStorageKey(), StorageCleanupEntry.REASON_STORY_DELETED);
		}

		// 한 판을 꽉 채웠다는 것은 남은 것이 더 있다는 뜻이다.
		if (images.size() >= settings.getBatchLimit() || videos.size() >= settings.getBatchLimit()) {
			log.warn("고아 업로드가 한 판 상한({})을 채웠다 — 남은 것은 다음 판이 가져간다. "
					+ "계속 차 있으면 상한을 올리거나 원인을 본다", settings.getBatchLimit());
		}
		log.info("고아 업로드 청소: 사진 {}개 · 동영상 {}개 · {}바이트를 치웠다", images.size(), videos.size(), bytes);
		return new SweepResult(images.size(), videos.size(), bytes, true);
	}

	/**
	 * @param deleted 실제로 지웠는가. {@code false} 면 위의 수는 지웠을 수다
	 */
	public record SweepResult(int images, int videos, long bytes, boolean deleted) {
	}
}
