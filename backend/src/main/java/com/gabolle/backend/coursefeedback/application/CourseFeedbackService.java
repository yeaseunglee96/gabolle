package com.gabolle.backend.coursefeedback.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 코스별 품질 피드백 — S15P21E201-2020 (투자자 피드백 「경로 추천의 품질은? 각 코스마다 피드백할 수 있는 UI」).
 *
 * <ul>
 * <li>추천으로 만든 일정만 받는다 — 직접 짜기({@code self_plan}) 일정은 평가할 추천이 없다</li>
 * <li>한 사람이 한 일정의 한 판(version)에 하나. 다시 보내면 고친다</li>
 * <li>여행 회원(trip_member)만 남기고 볼 수 있다</li>
 * </ul>
 */
@Service
@Profile({ "db", "dev" })
public class CourseFeedbackService {

	/** 화면의 이유 칩. 좋아요·아쉬워요 어느 쪽에서도 고를 수 있다(화면은 쪽마다 어울리는 것만 보여 준다). */
	public static final Set<String> REASONS = Set.of("LONG_TRANSIT", "NOT_MY_TASTE", "TOO_TIGHT", "PLACE_CLOSED",
			"HOURS_MISMATCH", "GREAT_PLACES", "GOOD_FLOW", "GOOD_PACE");

	public static final int COMMENT_MAX = 200;

	static final String SELF_PLAN_REQUEST_ID = "self_plan";

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public CourseFeedbackService(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	public record SubmitCommand(String rating, List<String> reasons, String comment, String courseLabel) {
	}

	public record CourseFeedback(UUID itineraryId, int version, String courseLabel, String rating,
			List<String> reasons, String comment, OffsetDateTime updatedAt) {
	}

	public record Bucket(String key, long total, long up, Integer upPercent) {
	}

	public record Summary(int days, long total, long up, Integer upPercent, List<Bucket> byCourse,
			List<Bucket> byArea, Map<String, Long> reasons) {
	}

	/** 내 가장 최근 평가. 없으면 비어 있다. */
	@Transactional(readOnly = true)
	public Optional<CourseFeedback> mine(UUID userId, UUID itineraryId) {
		requireMember(userId, itineraryId);
		return this.jdbc.query("""
				SELECT * FROM course_feedback WHERE user_id = ? AND itinerary_id = ?
				ORDER BY version DESC, updated_at DESC LIMIT 1
				""", CourseFeedbackService::map, userId, itineraryId).stream().findFirst();
	}

	/** 지금 판에 평가를 남기거나 고친다. */
	@Transactional
	public CourseFeedback submit(UUID userId, UUID itineraryId, SubmitCommand command) {
		Context ctx = requireMember(userId, itineraryId);
		if (ctx.selfPlan()) {
			throw new NotRecommendedException();
		}
		String rating = command.rating() == null ? "" : command.rating().trim().toUpperCase();
		if (!rating.equals("UP") && !rating.equals("DOWN")) {
			throw new InvalidException("rating");
		}
		LinkedHashSet<String> reasons = new LinkedHashSet<>();
		for (String reason : command.reasons() == null ? List.<String>of() : command.reasons()) {
			if (reason == null || !REASONS.contains(reason)) {
				throw new InvalidException("reasons");
			}
			reasons.add(reason);
		}
		String comment = command.comment() == null ? null : command.comment().strip();
		if (comment != null && comment.isEmpty()) {
			comment = null;
		}
		if (comment != null && comment.codePointCount(0, comment.length()) > COMMENT_MAX) {
			throw new InvalidException("comment");
		}
		String label = command.courseLabel() == null ? null : command.courseLabel().trim().toUpperCase();
		if (label != null && !label.matches("[A-Z]")) {
			throw new InvalidException("courseLabel");
		}
		OffsetDateTime now = OffsetDateTime.now(this.clock).withOffsetSameInstant(ZoneOffset.UTC);
		this.jdbc.update("""
				INSERT INTO course_feedback (course_feedback_id, user_id, itinerary_id, version, course_label,
				    area_code, rating, reasons, comment, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (user_id, itinerary_id, version) DO UPDATE SET
				    course_label = COALESCE(EXCLUDED.course_label, course_feedback.course_label),
				    rating = EXCLUDED.rating, reasons = EXCLUDED.reasons, comment = EXCLUDED.comment,
				    updated_at = EXCLUDED.updated_at
				""", UUID.randomUUID(), userId, itineraryId, ctx.latestVersion(), label, ctx.areaCode(), rating,
				String.join(",", reasons), comment, now, now);
		return mine(userId, itineraryId).orElseThrow();
	}

	/** 내 평가를 지운다(모든 판). */
	@Transactional
	public void delete(UUID userId, UUID itineraryId) {
		requireMember(userId, itineraryId);
		this.jdbc.update("DELETE FROM course_feedback WHERE user_id = ? AND itinerary_id = ?", userId, itineraryId);
	}

	/** 운영자 집계 — 최근 {@code days}일, 코스(A/B/C)·지역별 좋아요 비율과 이유별 건수. */
	@Transactional(readOnly = true)
	public Summary summary(int days) {
		int window = Math.max(1, Math.min(days, 365));
		OffsetDateTime since = OffsetDateTime.now(this.clock).minus(Duration.ofDays(window));
		List<Bucket> byCourse = buckets("COALESCE(course_label, '-')", since);
		List<Bucket> byArea = buckets("COALESCE(area_code, '-')", since);
		long total = byCourse.stream().mapToLong(Bucket::total).sum();
		long up = byCourse.stream().mapToLong(Bucket::up).sum();
		Map<String, Long> reasons = new TreeMap<>();
		this.jdbc.query("""
				SELECT r AS reason, count(*) AS n
				FROM course_feedback, unnest(string_to_array(reasons, ',')) AS r
				WHERE updated_at >= ? AND r <> '' GROUP BY r
				""", rs -> {
			reasons.put(rs.getString("reason"), rs.getLong("n"));
		}, since);
		return new Summary(window, total, up, percent(up, total), byCourse, byArea, reasons);
	}

	private List<Bucket> buckets(String keyExpression, OffsetDateTime since) {
		return new ArrayList<>(this.jdbc.query("""
				SELECT %s AS k, count(*) AS total, count(*) FILTER (WHERE rating = 'UP') AS up
				FROM course_feedback WHERE updated_at >= ? GROUP BY 1 ORDER BY 1
				""".formatted(keyExpression), (rs, i) -> {
			long total = rs.getLong("total");
			long up = rs.getLong("up");
			return new Bucket(rs.getString("k"), total, up, percent(up, total));
		}, since));
	}

	private static Integer percent(long up, long total) {
		return total == 0 ? null : (int) Math.round(up * 100.0 / total);
	}

	private record Context(int latestVersion, String areaCode, boolean selfPlan) {
	}

	private Context requireMember(UUID userId, UUID itineraryId) {
		List<Context> rows = this.jdbc.query("""
				SELECT i.latest_version, t.origin_area_code,
				    EXISTS (SELECT 1 FROM itinerary_versions v WHERE v.itinerary_id = i.itinerary_id
				        AND v.version = 1 AND v.request_id = ?) AS self_plan
				FROM itineraries i JOIN trip t ON t.trip_id = i.trip_id
				WHERE i.itinerary_id = ?
				  AND (t.owner_user_id = ? OR EXISTS (SELECT 1 FROM trip_member m
				      WHERE m.trip_id = t.trip_id AND m.user_id = ?))
				""", (rs, i) -> new Context(rs.getInt(1), rs.getString(2), rs.getBoolean(3)), SELF_PLAN_REQUEST_ID,
				itineraryId, userId, userId);
		if (rows.isEmpty()) {
			// 남의 일정과 없는 일정을 같은 답으로 — 있는지조차 알려 주지 않는다.
			throw new NotFoundException();
		}
		return rows.get(0);
	}

	private static CourseFeedback map(ResultSet rs, int rowNum) throws SQLException {
		String reasons = rs.getString("reasons");
		return new CourseFeedback(rs.getObject("itinerary_id", UUID.class), rs.getInt("version"),
				rs.getString("course_label"), rs.getString("rating"),
				reasons == null || reasons.isEmpty() ? List.of() : Arrays.asList(reasons.split(",")),
				rs.getString("comment"), rs.getObject("updated_at", OffsetDateTime.class));
	}

	public static class InvalidException extends RuntimeException {

		private final String field;

		InvalidException(String field) {
			super("평가 값이 올바르지 않습니다: " + field);
			this.field = field;
		}

		public String field() {
			return this.field;
		}
	}

	public static class NotFoundException extends RuntimeException {

		NotFoundException() {
			super("일정을 찾을 수 없습니다.");
		}
	}

	public static class NotRecommendedException extends RuntimeException {

		NotRecommendedException() {
			super("직접 짠 일정은 코스 평가를 받지 않습니다.");
		}
	}
}
