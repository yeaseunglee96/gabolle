package com.gabolle.backend.metrics;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영 지표(S15P21E201-2010). 정의는 {@code docs/metrics/METRICS.md} 와 같아야 한다 — 바꾸면 둘 다 바꾼다.
 *
 * <p>날짜는 모두 한국 날짜다. 「오늘」도 센다(아직 끝나지 않은 날이라 작게 나온다).
 */
@Service
@Profile({ "db", "dev" })
public class MetricsQueryService {

	static final int MAX_DAYS = 365;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	@Autowired
	public MetricsQueryService(JdbcTemplate jdbc) {
		this(jdbc, Clock.system(ActivityDayRecorder.SEOUL));
	}

	MetricsQueryService(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	public record DailyRow(LocalDate day, int dau, int wau, int mau, int anonymousDau, int anonymousMau, int signups,
			int tripsAi, int tripsSelf, int tripsNoItinerary, int routePosts, int placeSuggestions) {
	}

	public record CohortRow(LocalDate signupDay, int size, Integer d1, Integer d7, Integer d30) {
	}

	public record Report(LocalDate today, LocalDate from, Map<String, Object> totals, List<DailyRow> daily,
			List<CohortRow> cohorts, Map<String, Object> retention, Map<String, Object> notes) {
	}

	@Transactional(readOnly = true)
	public Report report(int days) {
		int n = Math.max(1, Math.min(days, MAX_DAYS));
		LocalDate today = LocalDate.now(this.clock.withZone(ActivityDayRecorder.SEOUL));
		LocalDate from = today.minusDays(n - 1L);
		List<DailyRow> daily = daily(from, today);
		List<CohortRow> cohorts = cohorts(from, today);
		DailyRow last = daily.get(daily.size() - 1);

		Map<String, Object> totals = new LinkedHashMap<>();
		totals.put("dau", last.dau());
		totals.put("wau", last.wau());
		totals.put("mau", last.mau());
		totals.put("anonymousMau", last.anonymousMau());
		totals.put("users", count("SELECT count(*) FROM app_user WHERE deleted_at IS NULL"));
		totals.put("trips", count("SELECT count(*) FROM trip"));
		totals.put("routePosts", count("SELECT count(*) FROM route_post WHERE deleted_at IS NULL"));
		totals.put("placeSuggestions", count("SELECT count(*) FROM place_suggestion"));

		Map<String, Object> notes = new LinkedHashMap<>();
		notes.put("requestTrackingSince", this.jdbc
			.queryForObject("SELECT min(day) FROM user_activity_day WHERE source = 'REQUEST'", LocalDate.class));
		notes.put("timezone", "Asia/Seoul");
		notes.put("definitions", "docs/metrics/METRICS.md");
		return new Report(today, from, totals, daily, cohorts, retention(cohorts), notes);
	}

	private int count(String sql) {
		Integer v = this.jdbc.queryForObject(sql, Integer.class);
		return v == null ? 0 : v;
	}

	private List<DailyRow> daily(LocalDate from, LocalDate to) {
		String sql = """
				WITH d AS (SELECT generate_series(?::date, ?::date, interval '1 day')::date AS day),
				trips AS (
				  SELECT (t.created_at AT TIME ZONE 'Asia/Seoul')::date AS day,
				    CASE WHEN NOT EXISTS (SELECT 1 FROM itineraries i WHERE i.trip_id = t.trip_id) THEN 'NONE'
				         WHEN EXISTS (SELECT 1 FROM itineraries i JOIN itinerary_versions v ON v.itinerary_id = i.itinerary_id
				                      WHERE i.trip_id = t.trip_id AND v.version = 1 AND v.request_id = 'self_plan') THEN 'SELF'
				         ELSE 'AI' END AS kind
				  FROM trip t WHERE t.created_at >= (?::date - 1)
				)
				SELECT d.day,
				  (SELECT count(DISTINCT user_id) FROM user_activity_day a WHERE a.day = d.day) AS dau,
				  (SELECT count(DISTINCT user_id) FROM user_activity_day a WHERE a.day BETWEEN d.day - 6 AND d.day) AS wau,
				  (SELECT count(DISTINCT user_id) FROM user_activity_day a WHERE a.day BETWEEN d.day - 29 AND d.day) AS mau,
				  (SELECT count(DISTINCT session_id) FROM anonymous_activity_day a WHERE a.day = d.day) AS anon_dau,
				  (SELECT count(DISTINCT session_id) FROM anonymous_activity_day a
				     WHERE a.day BETWEEN d.day - 29 AND d.day) AS anon_mau,
				  (SELECT count(*) FROM app_user u WHERE (u.created_at AT TIME ZONE 'Asia/Seoul')::date = d.day) AS signups,
				  (SELECT count(*) FROM trips t WHERE t.day = d.day AND t.kind = 'AI') AS trips_ai,
				  (SELECT count(*) FROM trips t WHERE t.day = d.day AND t.kind = 'SELF') AS trips_self,
				  (SELECT count(*) FROM trips t WHERE t.day = d.day AND t.kind = 'NONE') AS trips_none,
				  (SELECT count(*) FROM route_post r WHERE (r.created_at AT TIME ZONE 'Asia/Seoul')::date = d.day) AS route_posts,
				  (SELECT count(*) FROM place_suggestion s
				     WHERE (s.created_at AT TIME ZONE 'Asia/Seoul')::date = d.day) AS suggestions
				FROM d ORDER BY d.day
				""";
		return this.jdbc.query(sql,
				(rs, i) -> new DailyRow(rs.getObject("day", LocalDate.class), rs.getInt("dau"), rs.getInt("wau"),
						rs.getInt("mau"), rs.getInt("anon_dau"), rs.getInt("anon_mau"), rs.getInt("signups"),
						rs.getInt("trips_ai"), rs.getInt("trips_self"), rs.getInt("trips_none"), rs.getInt("route_posts"),
						rs.getInt("suggestions")),
				Date.valueOf(from), Date.valueOf(to), Date.valueOf(from));
	}

	/**
	 * 가입한 날 기준 묶음(코호트 — 같은 날 가입한 사람들). D1·D7·D30 = 가입 1·7·30일 뒤 그날에 다시 온 사람 수. 그날이 아직
	 * 다 지나지 않았으면 비운다(null).
	 */
	private List<CohortRow> cohorts(LocalDate from, LocalDate today) {
		String sql = """
				WITH c AS (
				  SELECT user_id, (created_at AT TIME ZONE 'Asia/Seoul')::date AS day FROM app_user
				  WHERE (created_at AT TIME ZONE 'Asia/Seoul')::date BETWEEN ?::date AND ?::date
				)
				SELECT c.day, count(*) AS size,
				  count(*) FILTER (WHERE EXISTS (SELECT 1 FROM user_activity_day a
				                                 WHERE a.user_id = c.user_id AND a.day = c.day + 1)) AS d1,
				  count(*) FILTER (WHERE EXISTS (SELECT 1 FROM user_activity_day a
				                                 WHERE a.user_id = c.user_id AND a.day = c.day + 7)) AS d7,
				  count(*) FILTER (WHERE EXISTS (SELECT 1 FROM user_activity_day a
				                                 WHERE a.user_id = c.user_id AND a.day = c.day + 30)) AS d30
				FROM c GROUP BY c.day ORDER BY c.day
				""";
		return this.jdbc.query(sql, (rs, i) -> {
			LocalDate day = rs.getObject("day", LocalDate.class);
			return new CohortRow(day, rs.getInt("size"), elapsed(day, 1, today) ? rs.getInt("d1") : null,
					elapsed(day, 7, today) ? rs.getInt("d7") : null, elapsed(day, 30, today) ? rs.getInt("d30") : null);
		}, Date.valueOf(from), Date.valueOf(today));
	}

	private static boolean elapsed(LocalDate signup, int k, LocalDate today) {
		return signup.plusDays(k).isBefore(today);
	}

	private static Map<String, Object> retention(List<CohortRow> cohorts) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("d1", rate(cohorts, 1));
		out.put("d7", rate(cohorts, 7));
		out.put("d30", rate(cohorts, 30));
		return out;
	}

	private static Map<String, Object> rate(List<CohortRow> cohorts, int k) {
		int base = 0;
		int back = 0;
		for (CohortRow c : cohorts) {
			Integer v = k == 1 ? c.d1() : k == 7 ? c.d7() : c.d30();
			if (v != null) {
				base += c.size();
				back += v;
			}
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("cohortUsers", base);
		m.put("returned", back);
		m.put("rate", base == 0 ? null : Math.round(back * 1000.0 / base) / 1000.0);
		return m;
	}

	/** CSV(쉼표로 나눈 표 — 엑셀·구글 시트에 바로 열린다). 하루 한 줄. */
	public String csv(int days) {
		Report r = report(days);
		List<String> lines = new ArrayList<>();
		lines.add("day,dau,wau,mau,anonymous_dau,anonymous_mau,signups,trips_ai,trips_self,trips_no_itinerary,"
				+ "route_posts,place_suggestions");
		for (DailyRow d : r.daily()) {
			lines.add(d.day() + "," + d.dau() + "," + d.wau() + "," + d.mau() + "," + d.anonymousDau() + ","
					+ d.anonymousMau() + "," + d.signups() + "," + d.tripsAi() + "," + d.tripsSelf() + ","
					+ d.tripsNoItinerary() + "," + d.routePosts() + "," + d.placeSuggestions());
		}
		return String.join("\n", lines) + "\n";
	}
}
