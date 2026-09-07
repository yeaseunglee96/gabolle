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
 * 신고 접수와 즉시 비노출 — S15P21E201-254.
 *
 * <h2>🔴 중복 신고 응답 — 조용히 성공한다</h2>
 * 이미 신고한 사람이 다시 신고하면 <b>204 로 조용히 성공</b> 처리한다({@code 409} 로 거절하지 않는다).
 * 409 로 거절하면 화면이 "이미 신고했습니다" 를 보여주게 되는데, 그 문구가 뜨는지 여부로 "이 사람이
 * 전에 신고한 적 있다" 는 정보가 새어 나간다(자기 신고 여부만 알려주므로 새는 정보는 적지만, 굳이
 * 오류로 만들 이유가 없다). 신고자 입장에서는 두 응답이 결과적으로 같다 — "접수됐다" 는 사실.
 *
 * <h2>🔴 선조회 + UNIQUE 이중 방어</h2>
 * {@link #file} 은 먼저 {@link StoryReportRepository#findByStoryIdAndReporterUserId} 로 흔한 경우(이미
 * 신고함)를 빠르게 답한다. 그런데 그 조회 뒤 진짜 저장 사이에 같은 사람이 두 번 동시에 눌렀다면
 * 조회만으로는 둘 다 "처음 신고" 로 보여 통과한다({@code trip/infra/JpaTripMembershipRepository} 가 같은
 * 문제를 ON CONFLICT 로 풀었다). 그래서 저장은 JPA {@code save} 가 아니라 네이티브
 * {@code INSERT ... ON CONFLICT (story_id, reporter_user_id) DO NOTHING} 이다 — 이미 만들어진
 * {@link StoryReportRepository} 인터페이스에는 이 질의를 더할 수 없어(수정 금지 목록) 이 서비스가
 * {@link EntityManager} 로 직접 낸다. 이 방식은 유니크 위반이 나도 예외를 던지지 않으므로(0행 삽입으로
 * 조용히 끝난다) 호출자의 트랜잭션을 롤백 전용으로 만들 걱정도 없다.
 *
 * <h2>🔴 자기 기록을 신고하는 것 — 허용한다</h2>
 * 막을 이유(자기 기록은 지우면 된다)도 있지만, 막으면 그 검사가 또 하나의 코드이고 실익이 적다.
 * 자기 신고가 들어와도 {@link Story#markUnderReview()} 로 잠깐 감춰질 뿐이고 작성자 자신은 언제든
 * {@code DELETE}로 완전히 지울 수 있어 새로 생기는 위험이 없다. 그래서 별도 차단 코드를 두지 않는다.
 */
@Service
@Profile({ "db", "dev" })
public class StoryReportService {

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
	 * <p>🔴 조회는 {@link StoryRepository#findActiveById}(검토 상태를 안 보는 쪽)를 쓴다.
	 * {@code findVisibleById} 를 쓰면 <b>먼저 신고한 사람 때문에 이미 UNDER_REVIEW 로 감춰진
	 * 기록을 두 번째 신고자가 신고할 수 없게 된다</b> — "두 사람이 신고하면 신고 수가 2" 라는
	 * 완료 기준이 정확히 이 경로로 깨진다. 대신 삭제된 기록과, 이미 운영자가 삭제로 처리한
	 * 기록(REMOVED)은 신고 대상에서 뺀다 — 더 처리할 것이 없다.
	 */
	@Transactional
	public void file(UUID storyId, UUID reporterUserId, StoryReportRequest request) {
		Instant now = this.clock.instant();
		StoryReportReason reason = StoryReportReason.from(request.reason())
				.orElseThrow(() -> new InvalidReasonException(request.reason()));
		Story story = requireReportable(storyId, reporterUserId, now);

		// 선조회 — 흔한 경우(이미 신고함)를 빠르게 조용한 성공으로 답한다.
		if (this.storyReportRepository.findByStoryIdAndReporterUserId(storyId, reporterUserId).isPresent()) {
			return;
		}

		StoryReport report = StoryReport.file(storyId, reporterUserId, reason, request.detail(), now);
		boolean inserted = insertIgnoringDuplicate(report);
		if (inserted) {
			// 방금 처음 접수된 신고일 때만 상태를 바꾼다. 경쟁에서 져 0행이 삽입됐다면(동시에 두 번
			// 눌러 UNIQUE 가 막은 경우) 이미 다른 요청이 같은 일을 했으니 다시 할 필요가 없다.
			story.markUnderReview();
		}
	}

	private Story requireReportable(UUID storyId, UUID reporterUserId, Instant now) {
		Story story = this.storyRepository.findActiveById(storyId)
				.orElseThrow(() -> new StoryNotFoundException(storyId));
		if (story.getModerationState() == StoryModerationState.REMOVED) {
			// 이미 운영자가 삭제로 처리했다 — 더 신고할 대상이 없다. 존재를 감춘다(404).
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

	/** 없는 기록, 지운 기록, 볼 수 없는 기록, 이미 삭제로 처리된 기록 — 전부 404. 존재를 감춘다. */
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
