package com.gabolle.backend.moderation.application;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.moderation.domain.StoryModerationState;
import com.gabolle.backend.moderation.domain.StoryReport;
import com.gabolle.backend.moderation.domain.StoryReportReason;
import com.gabolle.backend.moderation.presentation.dto.StoryReportRequest;
import com.gabolle.backend.moderation.repository.StoryReportRepository;
import com.gabolle.backend.story.application.StoryVisibilityPolicy;
import com.gabolle.backend.story.domain.Story;
import com.gabolle.backend.story.repository.StoryRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 신고 접수와 즉시 비노출.
 *
 * 중복 신고는 409 가 아니라 204 로 조용히 성공한다. 409 로 거절하면 "이미 신고했습니다" 문구가
 * 뜨는지로 그 사람의 과거 신고 여부가 새고, 신고자 입장에서 결과는 어차피 같다.
 *
 * 자기 기록을 신고하는 것은 막지 않는다 — 잠깐 감춰질 뿐이고 작성자는 언제든 지울 수 있다.
 */
@Service
@Profile({ "db", "dev" })
public class StoryReportService {

	/**
	 * 선조회와 저장 사이에 같은 사람이 두 번 동시에 누르면 조회만으로는 둘 다 통과한다. 그래서 JPA
	 * {@code save} 가 아니라 이 네이티브 질의로 저장한다 — 유니크 위반이 예외 대신 0행으로 끝나므로
	 * 호출자의 트랜잭션이 롤백 전용이 되지 않는다.
	 */
	private static final String INSERT_IGNORE_DUPLICATE = """
			INSERT INTO story_report (story_report_id, story_id, reporter_user_id, reason, detail, created_at)
			VALUES (?1, ?2, ?3, ?4, ?5, ?6)
			ON CONFLICT (story_id, reporter_user_id) DO NOTHING
			""";

	private final StoryReportRepository storyReportRepository;

	private final StoryRepository storyRepository;

	private final StoryVisibilityPolicy visibilityPolicy;

	private final Clock clock;

	@PersistenceContext
	private EntityManager entityManager;

	public StoryReportService(StoryReportRepository storyReportRepository, StoryRepository storyRepository,
			StoryVisibilityPolicy visibilityPolicy, Clock clock) {
		this.storyReportRepository = storyReportRepository;
		this.storyRepository = storyRepository;
		this.visibilityPolicy = visibilityPolicy;
		this.clock = clock;
	}

	/**
	 * 신고를 접수한다. 새 신고면 그 자리에서 기록을 검토 대기로 바꿔 즉시 비노출한다.
	 *
	 * 조회는 검토 상태를 보지 않는 {@link StoryRepository#findActiveById} 여야 한다.
	 * {@code findVisibleById} 를 쓰면 먼저 신고한 사람 때문에 UNDER_REVIEW 로 감춰진 기록을 두 번째
	 * 신고자가 신고할 수 없게 되어 신고 수가 1 에서 멈춘다.
	 */
	@Transactional
	public void file(UUID storyId, UUID reporterUserId, StoryReportRequest request) {
		Instant now = this.clock.instant();
		StoryReportReason reason = StoryReportReason.from(request.reason())
				.orElseThrow(() -> new InvalidReasonException(request.reason()));
		Story story = requireReportable(storyId, reporterUserId, now);

		if (this.storyReportRepository.findByStoryIdAndReporterUserId(storyId, reporterUserId).isPresent()) {
			return;
		}

		StoryReport report = StoryReport.file(storyId, reporterUserId, reason, request.detail(), now);
		boolean inserted = insertIgnoringDuplicate(report);
		if (inserted) {
			// 0행이 삽입됐다면 경쟁에서 진 것이고, 이긴 쪽이 이미 상태를 바꿨다.
			story.markUnderReview();
		}
	}

	private Story requireReportable(UUID storyId, UUID reporterUserId, Instant now) {
		Story story = this.storyRepository.findActiveById(storyId)
				.orElseThrow(() -> new StoryNotFoundException(storyId));
		if (story.getModerationState() == StoryModerationState.REMOVED) {
			throw new StoryNotFoundException(storyId);
		}
		if (!this.visibilityPolicy.canView(story, reporterUserId, now)) {
			throw new StoryNotFoundException(storyId);
		}
		return story;
	}

	private boolean insertIgnoringDuplicate(StoryReport report) {
		int inserted = this.entityManager.createNativeQuery(INSERT_IGNORE_DUPLICATE)
				.setParameter(1, report.getStoryReportId())
				.setParameter(2, report.getStoryId())
				.setParameter(3, report.getReporterUserId())
				.setParameter(4, report.getReason().name())
				.setParameter(5, report.getDetail())
				.setParameter(6, OffsetDateTime.ofInstant(report.getCreatedAt(), ZoneOffset.UTC))
				.executeUpdate();
		return inserted > 0;
	}

	/** 없는 기록, 지운 기록, 볼 수 없는 기록, 이미 삭제로 처리된 기록 — 전부 404 로 존재를 감춘다. */
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

	/** {@link StoryReportReason#from} 이 못 알아본 사유 — 400. */
	public static class InvalidReasonException extends RuntimeException {

		private final String reason;

		public InvalidReasonException(String reason) {
			super("신고 사유를 알 수 없습니다: " + reason);
			this.reason = reason;
		}

		public String reason() {
			return this.reason;
		}
	}
}
