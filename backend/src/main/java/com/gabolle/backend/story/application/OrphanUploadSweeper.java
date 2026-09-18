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
 * 어디에도 안 붙은 업로드를 치운다 — S15P21E201-1284.
 *
 * <h2>왜 필요한가 — 동영상이 이 길을 실제로 열었다</h2>
 *
 * 올리기와 기록 만들기는 <b>두 요청</b>이다. 그 사이에 사람이 나가면 파일만 남고 아무도 안
 * 건드린다. 사진일 때는 한 장에 몇백 KB 라 티가 안 났다. <b>동영상은 한 개가 사진 수십
 * 장</b>이고, 압축이 오래 걸리면 사람이 그냥 나가는 일이 <b>드물지 않고 흔하다.</b>
 *
 * <p>이 서버는 디스크를 쉽게 못 늘린다. <b>차는 순간 업로드가 아니라 서비스 전체가 멈춘다.</b>
 *
 * <h2>🔴 붙는 자리가 <b>둘</b>이다 — 하나만 보면 썸네일을 전부 지운다</h2>
 *
 * 자연스러운 질의는 {@code story_image} 만 보는 것이다. 그런데 <b>동영상 썸네일은 거기 안
 * 들어간다</b> — 「한 기록에 사진 3장」 한 칸을 안 먹게 하려고 일부러 그렇게 뒀다
 * (S15P21E201-1275 · -1279). {@code story_image} 만 보면 <b>붙어 있는 동영상의 썸네일이
 * 전부 고아로 보이고</b>, 글이 멀쩡히 살아 있는데도 지워진다.
 *
 * <p>그러면 <b>화면에는 검은 칸만 남고 아무 오류도 안 난다.</b> 몇 주 뒤 사람이 눈으로 볼
 * 때까지 아무도 모른다. 질의는 {@code UploadedImageRepository.findOrphansOlderThan} 에 있고
 * 거기서 두 자리를 다 본다.
 *
 * <p>🔴 <b>앞으로 붙는 자리가 하나 더 생기면 그 질의도 같이 늘려야 한다.</b> 안 늘리면
 * 그 자리에 붙은 파일이 조용히 지워진다.
 *
 * <h2>🔴 기본은 「끄기」가 아니라 「세기만 하기」다</h2>
 *
 * {@code enabled=false} 여도 이 청소기는 <b>돈다.</b> 다만 지우지 않고 <b>「지웠을 것 N개」만
 * 로그에 남긴다.</b> 며칠 그 숫자를 보고 나서 켠다 — 파일 삭제는 되돌릴 수 없고, 되돌릴 수
 * 없는 일은 먼저 세어 보고 한다. 아예 안 돌게 두면 <b>켤 때 처음으로 숫자를 보게 되는데</b>
 * 그때는 이미 지운 뒤다.
 *
 * <h2>지우는 길은 새로 만들지 않는다</h2>
 *
 * {@code deletedAt} 을 찍고 파일은 {@link StorageCleanupService#deleteOrEnqueue} 로 보낸다 —
 * 기록 삭제·계정 탈퇴가 쓰는 그 길이다. 못 지운 파일은 뒷정리 대기열에 남아 재시도된다.
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
	 * 🔴 기본값을 인라인에도 두고 {@code application.properties} 에도 둔다.
	 * {@code @Scheduled} 의 {@code cron} 은 자리표시자를 <b>프로퍼티 소스에서 직접</b> 찾으므로,
	 * 둘 다 없으면 <i>"Could not resolve placeholder"</i> 로 <b>기동 자체가 죽는다</b>
	 * (S15P21E201-357 이 2026-09-08 에 그렇게 배포에 실패했다).
	 *
	 * <p>개인정보 정리(19:00 UTC)·메뉴판 청소(18:20 UTC)와 시각을 겹치지 않게 뒀다. 같은 시각에
	 * 여러 삭제가 돌면 느려지는 이유를 나중에 로그만 보고 가려내기 어렵다.
	 */
	@Scheduled(cron = "${gabolle.storage.orphan-cleanup.cron:0 40 18 * * *}", zone = "UTC")
	public void runScheduled() {
		sweep();
	}

	/**
	 * 한 판 치운다.
	 *
	 * <p>시험과 수동 실행이 cron 을 거치지 않고 바로 부를 수 있게 따로 열어 둔다 —
	 * {@code MenuScanUsageSweeper} 가 같은 이유로 같은 모양이다.
	 *
	 * @return 지운 수(세기만 하는 모드에서는 <b>지웠을 수</b>)
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
			// 🔴 지우지 않는다. 숫자만 남긴다 — 이것을 며칠 보고 나서 켠다.
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

		// 🔴 한 판을 꽉 채웠다는 것은 남은 것이 더 있다는 뜻이다. 사람이 그 사실을 알아야
		//    「왜 아직도 안 줄지」를 안 헤맨다. 개인정보 배치가 같은 자리에 같은 경고를 둔다.
		if (images.size() >= settings.getBatchLimit() || videos.size() >= settings.getBatchLimit()) {
			log.warn("고아 업로드가 한 판 상한({})을 채웠다 — 남은 것은 다음 판이 가져간다. "
					+ "계속 차 있으면 상한을 올리거나 원인을 본다", settings.getBatchLimit());
		}
		log.info("고아 업로드 청소: 사진 {}개 · 동영상 {}개 · {}바이트를 치웠다", images.size(), videos.size(), bytes);
		return new SweepResult(images.size(), videos.size(), bytes, true);
	}

	/**
	 * 한 판의 결과.
	 *
	 * @param deleted 실제로 지웠는가. {@code false} 면 위의 수는 <b>지웠을 수</b>다 —
	 * 이 칸이 없으면 로그를 보는 사람이 「지웠다」와 「지웠을 것이다」를 구분할 수 없다
	 */
	public record SweepResult(int images, int videos, long bytes, boolean deleted) {
	}
}
