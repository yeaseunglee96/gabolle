package com.gabolle.backend.feedback.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 의견 보내기 — 버그·건의·기타를 받는다(S15P21E201-2049).
 *
 * <ul>
 * <li>로그인 없이도 받는다. 로그인했으면 계정을 묶는다</li>
 * <li>하루 상한 {@value #DAILY_LIMIT}건 — 계정, 없으면 기기 번호, 그것도 없으면 IP 로 센다</li>
 * <li>사진은 로그인한 사람이 직접 올린 것만, {@value #MAX_SCREENSHOTS}장까지</li>
 * <li>받은 뒤 Jira 이슈를 만든다({@link FeedbackIssueTracker}, 설정이 꺼져 있으면 아무것도 안 한다)</li>
 * <li>운영자가 DONE 으로 답하면 보낸 사람에게 알린다({@link FeedbackNotifier})</li>
 * </ul>
 */
@Service
@Profile({ "db", "dev" })
public class FeedbackService {

	private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

	public static final int DAILY_LIMIT = 5;

	public static final int CONTENT_MIN = 10;

	public static final int CONTENT_MAX = 1000;

	public static final int MAX_SCREENSHOTS = 3;

	static final Set<String> TYPES = Set.of("BUG", "IDEA", "OTHER");

	static final Set<String> STATUSES = Set.of("RECEIVED", "IN_PROGRESS", "DONE");

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]{2,}$");

	private final JdbcTemplate jdbc;

	private final FeedbackIssueTracker tracker;

	private final FeedbackNotifier notifier;

	private final Clock clock;

	public FeedbackService(JdbcTemplate jdbc, FeedbackIssueTracker tracker, FeedbackNotifier notifier, Clock clock) {
		this.jdbc = jdbc;
		this.tracker = tracker;
		this.notifier = notifier;
		this.clock = clock;
	}

	public record SubmitCommand(String type, String content, String replyEmail, List<String> screenshotUrls,
			String appVersion, String platform, String osVersion, String language, String screen, String tripId) {
	}

	public record Feedback(UUID feedbackId, UUID userId, String type, String content, String replyEmail,
			List<String> screenshotUrls, String appVersion, String platform, String osVersion, String language,
			String screen, String tripId, String status, String adminReply, String jiraKey, OffsetDateTime createdAt) {
	}

	/**
	 * @param userId 로그인했으면 계정, 아니면 비어 있다
	 * @param deviceId 앱이 보내는 기기 번호({@code X-Device-Id}). 없을 수 있다
	 * @param clientIp 요청 IP
	 */
	@Transactional
	public Feedback submit(Optional<UUID> userId, String deviceId, String clientIp, SubmitCommand c) {
		String type = c.type() == null ? null : c.type().trim().toUpperCase();
		String content = trimToNull(c.content());
		if (type == null || !TYPES.contains(type)) {
			throw new InvalidFeedbackException("종류는 BUG·IDEA·OTHER 중 하나입니다.", "type");
		}
		int length = content == null ? 0 : content.codePointCount(0, content.length());
		if (length < CONTENT_MIN || length > CONTENT_MAX) {
			throw new InvalidFeedbackException("내용을 " + CONTENT_MIN + "자 이상 " + CONTENT_MAX + "자 이하로 적어 주세요.",
					"content");
		}
		String email = trimToNull(c.replyEmail());
		if (email != null && (email.length() > 254 || !EMAIL.matcher(email).matches())) {
			throw new InvalidFeedbackException("답장 받을 이메일 주소를 확인해 주세요.", "replyEmail");
		}
		List<String> shots = c.screenshotUrls() == null ? List.of()
				: c.screenshotUrls().stream().map(FeedbackService::trimToNull).filter(s -> s != null).distinct().toList();
		if (shots.size() > MAX_SCREENSHOTS) {
			throw new InvalidFeedbackException("사진은 " + MAX_SCREENSHOTS + "장까지 붙일 수 있어요.", "screenshotUrls");
		}
		if (!shots.isEmpty()) {
			if (userId.isEmpty()) {
				throw new InvalidScreenshotException();
			}
			for (String url : shots) {
				Integer owned = this.jdbc.queryForObject(
						"SELECT count(*) FROM uploaded_image WHERE image_url = ? AND uploader_user_id = ? AND deleted_at IS NULL",
						Integer.class, url, userId.get());
				if (owned == null || owned == 0) {
					throw new InvalidScreenshotException();
				}
			}
		}
		String clientKey = userId.map(id -> "u:" + id)
				.orElseGet(() -> trimToNull(deviceId) != null ? "d:" + hash(deviceId.trim()) : "ip:" + hash(clientIp));
		OffsetDateTime now = OffsetDateTime.now(this.clock).withOffsetSameInstant(ZoneOffset.UTC);
		// 같은 열쇠의 제출을 줄 세운다 — 동시에 여러 번 눌러도 상한을 넘지 않게.
		this.jdbc.query("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(?))) AS l", rs -> {
		}, "feedback:" + clientKey);
		Integer recent = this.jdbc.queryForObject(
				"SELECT count(*) FROM feedback WHERE client_key = ? AND created_at > ?", Integer.class, clientKey,
				now.minus(Duration.ofDays(1)));
		if (recent != null && recent >= DAILY_LIMIT) {
			throw new RateLimitedException();
		}
		UUID id = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO feedback (feedback_id, user_id, client_key, type, content, reply_email, screenshot_urls,
				    app_version, platform, os_version, language, screen, trip_id, status, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'RECEIVED', ?)""", id, userId.orElse(null), clientKey,
				type, content, email, shots.isEmpty() ? null : String.join("\n", shots), cut(c.appVersion(), 40),
				cut(c.platform(), 20), cut(c.osVersion(), 60), cut(c.language(), 16), cut(c.screen(), 120),
				cut(c.tripId(), 64), now);
		Feedback saved = find(id).orElseThrow();
		afterCommit(() -> {
			try {
				String key = this.tracker.create(saved);
				if (key != null) {
					this.jdbc.update("UPDATE feedback SET jira_key = ? WHERE feedback_id = ?", key, id);
				}
			}
			catch (RuntimeException e) {
				log.warn("의견 {} 의 Jira 이슈를 못 만들었다", id, e);
			}
		});
		return saved;
	}

	@Transactional(readOnly = true)
	public List<Feedback> list(String status) {
		if (status == null || status.isBlank()) {
			return query("ORDER BY created_at DESC LIMIT 200");
		}
		if (!STATUSES.contains(status)) {
			throw new InvalidFeedbackException("status 는 RECEIVED·IN_PROGRESS·DONE 중 하나입니다.", "status");
		}
		return query("WHERE status = ? ORDER BY created_at DESC LIMIT 200", status);
	}

	/** 상태·답변을 바꾼다. DONE 이 되면서 답변이 있으면 보낸 사람(계정이 있을 때)에게 알린다. */
	@Transactional
	public Feedback update(UUID feedbackId, UUID adminId, String status, String adminReply) {
		if (status == null || !STATUSES.contains(status)) {
			throw new InvalidFeedbackException("status 는 RECEIVED·IN_PROGRESS·DONE 중 하나입니다.", "status");
		}
		String reply = trimToNull(adminReply);
		if (reply != null && reply.length() > CONTENT_MAX) {
			throw new InvalidFeedbackException("답변은 " + CONTENT_MAX + "자까지입니다.", "adminReply");
		}
		List<String> before = this.jdbc.queryForList("SELECT status FROM feedback WHERE feedback_id = ? FOR UPDATE",
				String.class, feedbackId);
		if (before.isEmpty()) {
			throw new FeedbackNotFoundException();
		}
		this.jdbc.update("""
				UPDATE feedback SET status = ?, admin_reply = coalesce(?, admin_reply), handled_by = ?, handled_at = ?
				 WHERE feedback_id = ?""", status, reply, adminId,
				OffsetDateTime.now(this.clock).withOffsetSameInstant(ZoneOffset.UTC), feedbackId);
		Feedback updated = find(feedbackId).orElseThrow();
		if ("DONE".equals(status) && reply != null && updated.userId() != null) {
			String language = this.jdbc.queryForList("SELECT language FROM app_user WHERE user_id = ?", String.class,
					updated.userId()).stream().findFirst().orElse(updated.language());
			afterCommit(() -> {
				try {
					this.notifier.replied(updated.userId(), language);
				}
				catch (RuntimeException e) {
					log.warn("의견 {} 답변 알림을 못 보냈다", feedbackId, e);
				}
			});
		}
		return updated;
	}

	private static void afterCommit(Runnable task) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					task.run();
				}
			});
		}
		else {
			task.run();
		}
	}

	private Optional<Feedback> find(UUID id) {
		return query("WHERE feedback_id = ?", id).stream().findFirst();
	}

	private List<Feedback> query(String where, Object... args) {
		return this.jdbc.query("SELECT * FROM feedback " + where, (rs, i) -> {
			String shots = rs.getString("screenshot_urls");
			return new Feedback((UUID) rs.getObject("feedback_id"), (UUID) rs.getObject("user_id"),
					rs.getString("type"), rs.getString("content"), rs.getString("reply_email"),
					shots == null ? List.of() : List.of(shots.split("\n")), rs.getString("app_version"),
					rs.getString("platform"), rs.getString("os_version"), rs.getString("language"),
					rs.getString("screen"), rs.getString("trip_id"), rs.getString("status"),
					rs.getString("admin_reply"), rs.getString("jira_key"),
					rs.getObject("created_at", OffsetDateTime.class));
		}, args);
	}

	private static String hash(String value) {
		try {
			byte[] d = MessageDigest.getInstance("SHA-256")
				.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(d).substring(0, 32);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String cut(String value, int max) {
		String t = trimToNull(value);
		return t == null || t.length() <= max ? t : t.substring(0, max);
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String t = value.trim();
		return t.isEmpty() ? null : t;
	}

	public static class InvalidFeedbackException extends RuntimeException {

		private final List<String> fields;

		public InvalidFeedbackException(String message, String... fields) {
			super(message);
			this.fields = List.of(fields);
		}

		public List<String> fields() {
			return this.fields;
		}
	}

	public static class InvalidScreenshotException extends RuntimeException {

		public InvalidScreenshotException() {
			super("직접 올린 사진만 붙일 수 있어요(로그인 필요).");
		}
	}

	public static class RateLimitedException extends RuntimeException {

		public RateLimitedException() {
			super("의견은 하루 " + DAILY_LIMIT + "건까지 보낼 수 있어요.");
		}
	}

	public static class FeedbackNotFoundException extends RuntimeException {

		public FeedbackNotFoundException() {
			super("의견을 찾을 수 없어요.");
		}
	}
}
