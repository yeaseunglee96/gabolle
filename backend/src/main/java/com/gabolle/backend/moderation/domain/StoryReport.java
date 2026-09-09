package com.gabolle.backend.moderation.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 기록 하나에 대한 신고 한 건 — S15P21E201-254 · -267.
 *
 * <h2>🔴 신고자를 외래키로 걸지 않는다</h2>
 * {@code reporter_user_id} 는 {@code app_user} 를 참조하지만 외래키가 없다. 탈퇴한 사람의
 * 신고도 <b>검토 근거로 남아야</b> 하기 때문이다. 외래키를 걸면 계정 삭제가 이 행을 지우거나
 * ({@code CASCADE}) 신고자를 비우는데({@code SET NULL}), 둘 다 곤란하다 — 지우면 신고 수가
 * 줄어 "두 사람이 신고했다" 가 한 사람으로 보이고, 비우면 같은 사람의 중복 신고를 막는
 * UNIQUE 가 무력해진다.
 *
 * <p>대신 신고자 이름은 <b>검토 화면에 보여주지 않는다.</b> 운영자가 판단할 것은 기록의 내용이고
 * 누가 신고했는지가 판단을 바꾸면 안 된다. 그래서 이 값의 유일한 용도는 중복 신고 판정이다.
 */
@Entity
@Table(name = "story_report")
public class StoryReport {

	@Id
	@Column(name = "story_report_id", nullable = false, updatable = false)
	private UUID storyReportId;

	@Column(name = "story_id", nullable = false, updatable = false)
	private UUID storyId;

	@Column(name = "reporter_user_id", nullable = false, updatable = false)
	private UUID reporterUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "reason", nullable = false, length = 30, updatable = false)
	private StoryReportReason reason;

	/** {@link StoryReportReason#OTHER} 일 때만 채워진다. */
	@Column(name = "detail", length = 500, updatable = false)
	private String detail;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "resolution", length = 20)
	private StoryReportResolution resolution;

	@Column(name = "resolved_by")
	private UUID resolvedBy;

	protected StoryReport() {
	}

	private StoryReport(UUID storyReportId, UUID storyId, UUID reporterUserId, StoryReportReason reason,
			String detail, Instant createdAt) {
		this.storyReportId = storyReportId;
		this.storyId = storyId;
		this.reporterUserId = reporterUserId;
		this.reason = reason;
		this.detail = detail;
		this.createdAt = createdAt;
	}

	/**
	 * 신고를 접수한다.
	 *
	 * <p>🔴 {@code detail} 은 {@link StoryReportReason#OTHER} 가 아니면 버린다. 사유를 골라
	 * 놓고 자유 입력을 함께 보내는 클라이언트가 있으면 그 글이 검토 화면에 섞여 운영자가
	 * 읽어야 하는 것이 늘어난다.
	 */
	public static StoryReport file(UUID storyId, UUID reporterUserId, StoryReportReason reason, String detail,
			Instant now) {
		if (storyId == null || reporterUserId == null || reason == null) {
			throw new IllegalArgumentException("신고에 필요한 값이 없습니다.");
		}
		String keptDetail = (reason == StoryReportReason.OTHER) ? trimToNull(detail) : null;
		return new StoryReport(UUID.randomUUID(), storyId, reporterUserId, reason, keptDetail, now);
	}

	/**
	 * 운영자가 처리했다고 표시한다.
	 *
	 * <p>이미 처리된 신고를 다시 처리하지 않는다 — 두 운영자가 같은 신고를 동시에 열었을 때
	 * 나중 판단이 앞선 판단을 덮어쓰면 어느 것이 실제 결정인지 알 수 없다.
	 */
	public void resolve(StoryReportResolution resolution, UUID adminUserId, Instant now) {
		if (this.resolvedAt != null) {
			throw new AlreadyResolvedException(this.storyReportId, this.resolution);
		}
		this.resolution = resolution;
		this.resolvedBy = adminUserId;
		this.resolvedAt = now;
	}

	public boolean isPending() {
		return this.resolvedAt == null;
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	public UUID getStoryReportId() {
		return this.storyReportId;
	}

	public UUID getStoryId() {
		return this.storyId;
	}

	public UUID getReporterUserId() {
		return this.reporterUserId;
	}

	public StoryReportReason getReason() {
		return this.reason;
	}

	public String getDetail() {
		return this.detail;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getResolvedAt() {
		return this.resolvedAt;
	}

	public StoryReportResolution getResolution() {
		return this.resolution;
	}

	public UUID getResolvedBy() {
		return this.resolvedBy;
	}

	/** 이미 처리된 신고를 다시 처리하려 했다 — 409. */
	public static class AlreadyResolvedException extends RuntimeException {

		private final UUID storyReportId;

		private final StoryReportResolution resolution;

		public AlreadyResolvedException(UUID storyReportId, StoryReportResolution resolution) {
			super("이미 처리된 신고입니다: " + storyReportId + " (" + resolution + ")");
			this.storyReportId = storyReportId;
			this.resolution = resolution;
		}

		public UUID storyReportId() {
			return this.storyReportId;
		}

		public StoryReportResolution resolution() {
			return this.resolution;
		}
	}
}
