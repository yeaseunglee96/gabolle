package com.gabolle.backend.routepost;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 루트 포스트 — 올리기·피드(최신/인기)·상세·지우기·좋아요·저장·복사 기록, 그리고 장소별 루트(-2028).
 *
 * <p>🔴 <b>누가 무엇을 보나.</b> 루트 포스트는 공개 글만 있다(표의 CHECK). 그래도 아래는 안 보인다 —
 * 지운 글, 탈퇴한 사람의 글, 나를 차단했거나 내가 차단한 사람의 글. 모두 404 하나로 답해 응답 차이로
 * 존재가 새지 않게 한다. 이 판정은 {@link #VISIBLE} 한 곳에만 있고 모든 읽기가 그것을 쓴다.
 */
@Service
@Profile({ "db", "dev" })
public class RoutePostService {

	public static final int DEFAULT_LIMIT = 20;

	public static final int MAX_LIMIT = 50;

	/** 장소 화면에 띄우는 루트 수 — 「~5개」(-2028). */
	public static final int PLACE_LIMIT_DEFAULT = 5;

	public static final int PLACE_LIMIT_MAX = 20;

	/**
	 * 인기 점수 — 좋아요 1, 저장 2, 「이 루트로 여행 만들기」 3. 따라 해 본 것이 가장 강한 신호다.
	 * 동점이면 최신이 위.
	 */
	static final String POPULARITY = "(rp.like_count + 2 * rp.save_count + 3 * rp.copy_count)";

	/** 보는 사람이 이 글을 볼 수 있는가. {@code :viewer} 가 NULL 이면 로그인하지 않은 사람. */
	static final String VISIBLE = """
			rp.deleted_at IS NULL
			AND rp.visibility = 'PUBLIC'
			AND u.deleted_at IS NULL
			AND (CAST(:viewer AS uuid) IS NULL OR NOT EXISTS (
			      SELECT 1 FROM user_block b
			       WHERE (b.blocker_user_id = rp.author_user_id AND b.blocked_user_id = CAST(:viewer AS uuid))
			          OR (b.blocker_user_id = CAST(:viewer AS uuid) AND b.blocked_user_id = rp.author_user_id)))
			""";

	private static final String SELECT = """
			SELECT rp.*, u.display_name, u.avatar_url,
			       EXISTS (SELECT 1 FROM route_post_reaction r WHERE r.route_post_id = rp.route_post_id
			                 AND r.user_id = CAST(:viewer AS uuid) AND r.kind = 'LIKE') AS liked_by_me,
			       EXISTS (SELECT 1 FROM route_post_reaction r WHERE r.route_post_id = rp.route_post_id
			                 AND r.user_id = CAST(:viewer AS uuid) AND r.kind = 'SAVE') AS saved_by_me
			  FROM route_post rp
			  JOIN app_user u ON u.user_id = rp.author_user_id
			""";

	public enum Sort {
		RECENT, POPULAR
	}

	public enum Kind {
		LIKE, SAVE, COPY
	}

	/**
	 * @param dayIndex {@code null} 이면 일정 전체
	 */
	public record PublishCommand(String itineraryId, Integer dayIndex, String title, String body) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	private final RouteSnapshotSource source;

	private final ObjectMapper json;

	private final Clock clock;

	public RoutePostService(NamedParameterJdbcTemplate jdbc, RouteSnapshotSource source, ObjectMapper json,
			Clock clock) {
		this.jdbc = jdbc;
		this.source = source;
		this.json = json;
		this.clock = clock;
	}

	@Transactional
	public RoutePostResponse publish(UUID author, PublishCommand command) {
		String title = command.title() == null ? "" : command.title().strip();
		if (title.isEmpty() || title.length() > 80) {
			throw new RoutePostExceptions.InvalidRouteException("INVALID_TITLE");
		}
		String body = command.body() == null ? "" : command.body().strip();
		if (body.length() > 500) {
			throw new RoutePostExceptions.InvalidRouteException("INVALID_BODY");
		}
		RouteSnapshot snap = this.source.snapshot(author, command.itineraryId(), command.dayIndex());
		UUID id = UUID.randomUUID();
		Instant now = this.clock.instant();
		this.jdbc.update("""
				INSERT INTO route_post (route_post_id, author_user_id, trip_id, itinerary_id, day_index, day_count,
				    title, body, duration_min, distance_m, path, stop_count, created_at)
				VALUES (:id, :author, :trip, :itinerary, :day, :dayCount, :title, :body, :duration, :distance,
				    CAST(:path AS jsonb), :stopCount, :now)
				""", new MapSqlParameterSource("id", id).addValue("author", author)
				.addValue("trip", snap.tripId()).addValue("itinerary", snap.itineraryId())
				.addValue("day", snap.dayIndex()).addValue("dayCount", snap.dayCount()).addValue("title", title)
				.addValue("body", body).addValue("duration", snap.durationMin()).addValue("distance", snap.distanceM())
				.addValue("path", writePath(snap.path())).addValue("stopCount", snap.stops().size())
				.addValue("now", Timestamp.from(now)));
		int seq = 0;
		for (RouteSnapshot.Stop stop : snap.stops()) {
			this.jdbc.update("""
					INSERT INTO route_post_stop (route_post_id, seq, day_index, place_id, name, lat, lng, stay_minutes)
					VALUES (:id, :seq, :day, :place, :name, :lat, :lng, :stay)
					""", new MapSqlParameterSource("id", id).addValue("seq", seq++).addValue("day", stop.dayIndex())
					.addValue("place", stop.placeId()).addValue("name", truncate(stop.name(), 200))
					.addValue("lat", stop.lat()).addValue("lng", stop.lng()).addValue("stay", stop.stayMinutes()));
		}
		return get(id, author);
	}

	@Transactional(readOnly = true)
	public RoutePostResponse get(UUID routePostId, UUID viewer) {
		List<RoutePostResponse> rows = query(SELECT + " WHERE rp.route_post_id = :id AND " + VISIBLE,
				params(viewer).addValue("id", routePostId), viewer);
		if (rows.isEmpty()) {
			throw new RoutePostExceptions.NotFoundException();
		}
		return rows.get(0);
	}

	/** 피드. 커서는 건너뛸 개수를 감싼 글자다 — 인기순은 점수가 바뀌어 키로 이어 붙일 수 없다. */
	@Transactional(readOnly = true)
	public RoutePostResponse.Page feed(UUID viewer, Sort sort, String cursor, Integer limit) {
		int size = clamp(limit, DEFAULT_LIMIT, MAX_LIMIT);
		int offset = decodeCursor(cursor);
		String order = sort == Sort.POPULAR ? POPULARITY + " DESC, rp.created_at DESC, rp.route_post_id"
				: "rp.created_at DESC, rp.route_post_id";
		List<RoutePostResponse> rows = query(
				SELECT + " WHERE " + VISIBLE + " ORDER BY " + order + " LIMIT :limit OFFSET :offset",
				params(viewer).addValue("limit", size + 1).addValue("offset", offset), viewer);
		String next = null;
		if (rows.size() > size) {
			rows = rows.subList(0, size);
			next = encodeCursor(offset + size);
		}
		return new RoutePostResponse.Page(rows, next);
	}

	/** 이 장소가 들어간 루트(-2028) — 인기순, 많아야 {@code limit} 개. */
	@Transactional(readOnly = true)
	public List<RoutePostResponse> byPlace(UUID placeId, UUID viewer, Integer limit) {
		int size = clamp(limit, PLACE_LIMIT_DEFAULT, PLACE_LIMIT_MAX);
		return query(SELECT + """
				 WHERE EXISTS (SELECT 1 FROM route_post_stop s WHERE s.route_post_id = rp.route_post_id AND s.place_id = :place)
				   AND """ + " " + VISIBLE + " ORDER BY " + POPULARITY + " DESC, rp.created_at DESC LIMIT :limit",
				params(viewer).addValue("place", placeId).addValue("limit", size), viewer);
	}

	/** 내가 저장한 루트. 최근 저장이 위. */
	@Transactional(readOnly = true)
	public List<RoutePostResponse> saved(UUID viewer) {
		return query(SELECT + """
				 JOIN route_post_reaction mine ON mine.route_post_id = rp.route_post_id
				  AND mine.user_id = CAST(:viewer AS uuid) AND mine.kind = 'SAVE'
				 WHERE """ + " " + VISIBLE + " ORDER BY mine.created_at DESC LIMIT 100", params(viewer), viewer);
	}

	@Transactional
	public void delete(UUID routePostId, UUID actor) {
		RoutePostResponse post = get(routePostId, actor);
		if (!post.mine()) {
			throw new RoutePostExceptions.ForbiddenException();
		}
		this.jdbc.update("UPDATE route_post SET deleted_at = :now WHERE route_post_id = :id",
				new MapSqlParameterSource("id", routePostId).addValue("now", Timestamp.from(this.clock.instant())));
	}

	/** 반응을 남긴다. 이미 있으면 아무것도 안 바뀐다 — 연타·재시도가 수를 부풀리지 않게. */
	@Transactional
	public RoutePostResponse react(UUID routePostId, UUID actor, Kind kind) {
		get(routePostId, actor);
		int inserted = this.jdbc.update("""
				INSERT INTO route_post_reaction (route_post_id, user_id, kind, created_at)
				VALUES (:id, :user, :kind, :now) ON CONFLICT DO NOTHING
				""", new MapSqlParameterSource("id", routePostId).addValue("user", actor).addValue("kind", kind.name())
				.addValue("now", Timestamp.from(this.clock.instant())));
		if (inserted > 0) {
			bump(routePostId, kind, 1);
		}
		return get(routePostId, actor);
	}

	/**
	 * 「이 루트로 여행 만들기」를 센다 — 같은 사람은 한 번(S15P21E201-2043). 만든 일정을 알려 주면 그 여행을 적어 두고,
	 * 그 여행을 지우면 {@link #onTripDeleted} 가 뺀다. 남의 일정이면 적지 않는다(센 것은 그대로).
	 */
	@Transactional
	public RoutePostResponse copied(UUID routePostId, UUID actor, String itineraryId) {
		get(routePostId, actor);
		UUID tripId = ownedTripOf(itineraryId, actor);
		Boolean inserted = this.jdbc.queryForObject("""
				INSERT INTO route_post_reaction (route_post_id, user_id, kind, created_at, trip_id)
				VALUES (:id, :user, 'COPY', :now, :trip)
				ON CONFLICT (route_post_id, user_id, kind)
				DO UPDATE SET trip_id = COALESCE(EXCLUDED.trip_id, route_post_reaction.trip_id)
				RETURNING (xmax = 0)
				""", new MapSqlParameterSource("id", routePostId).addValue("user", actor)
				.addValue("now", Timestamp.from(this.clock.instant())).addValue("trip", tripId), Boolean.class);
		if (Boolean.TRUE.equals(inserted)) {
			bump(routePostId, Kind.COPY, 1);
		}
		return get(routePostId, actor);
	}

	private UUID ownedTripOf(String itineraryId, UUID actor) {
		if (itineraryId == null || itineraryId.isBlank()) {
			return null;
		}
		UUID parsed;
		try {
			parsed = UUID.fromString(itineraryId.trim());
		}
		catch (IllegalArgumentException e) {
			return null;
		}
		List<UUID> trips = this.jdbc.queryForList("""
				SELECT i.trip_id FROM itineraries i
				  JOIN trip_member m ON m.trip_id = i.trip_id AND m.user_id = :user AND m.role = 'OWNER'
				 WHERE i.itinerary_id = :it
				""", new MapSqlParameterSource("it", parsed).addValue("user", actor), UUID.class);
		return trips.isEmpty() ? null : trips.get(0);
	}

	/** 여행을 지우면 그 여행으로 센 「따라 간 사람」을 뺀다 (S15P21E201-2043). 지운 트랜잭션 안에서 돈다. */
	@org.springframework.context.event.EventListener
	public void onTripDeleted(com.gabolle.backend.trip.domain.TripDeleted event) {
		UUID tripId;
		try {
			tripId = UUID.fromString(event.tripId());
		}
		catch (IllegalArgumentException e) {
			return;
		}
		List<UUID> posts = this.jdbc.queryForList(
				"DELETE FROM route_post_reaction WHERE kind = 'COPY' AND trip_id = :trip RETURNING route_post_id",
				new MapSqlParameterSource("trip", tripId), UUID.class);
		for (UUID post : posts) {
			bump(post, Kind.COPY, -1);
		}
	}

	/** 좋아요·저장을 거둔다. 없던 것을 거둬도 성공이다. 복사는 거둘 수 없다. */
	@Transactional
	public RoutePostResponse unreact(UUID routePostId, UUID actor, Kind kind) {
		get(routePostId, actor);
		int removed = this.jdbc.update(
				"DELETE FROM route_post_reaction WHERE route_post_id = :id AND user_id = :user AND kind = :kind",
				new MapSqlParameterSource("id", routePostId).addValue("user", actor).addValue("kind", kind.name()));
		if (removed > 0) {
			bump(routePostId, kind, -1);
		}
		return get(routePostId, actor);
	}

	private void bump(UUID id, Kind kind, int delta) {
		String column = switch (kind) {
			case LIKE -> "like_count";
			case SAVE -> "save_count";
			case COPY -> "copy_count";
		};
		this.jdbc.update("UPDATE route_post SET " + column + " = GREATEST(0, " + column + " + :d) WHERE route_post_id = :id",
				new MapSqlParameterSource("id", id).addValue("d", delta));
	}

	private List<RoutePostResponse> query(String sql, MapSqlParameterSource params, UUID viewer) {
		List<Row> rows = this.jdbc.query(sql, params, (rs, i) -> row(rs));
		if (rows.isEmpty()) {
			return List.of();
		}
		Map<UUID, List<RoutePostResponse.Stop>> stops = new LinkedHashMap<>();
		for (Row r : rows) {
			stops.put(r.id, new ArrayList<>());
		}
		this.jdbc.query("""
				SELECT s.*, p.name_ko, p.name_en, p.photo_url
				  FROM route_post_stop s JOIN place p ON p.place_id = s.place_id
				 WHERE s.route_post_id IN (:ids) ORDER BY s.route_post_id, s.seq
				""", new MapSqlParameterSource("ids", stops.keySet()), rs -> {
			UUID postId = rs.getObject("route_post_id", UUID.class);
			String current = rs.getString("name_ko");
			stops.get(postId).add(new RoutePostResponse.Stop(rs.getInt("seq"), rs.getInt("day_index"),
					rs.getObject("place_id", UUID.class).toString(),
					current == null || current.isBlank() ? rs.getString("name") : current, rs.getString("name_en"),
					rs.getDouble("lat"), rs.getDouble("lng"), rs.getString("photo_url"),
					(Integer) rs.getObject("stay_minutes")));
		});
		List<RoutePostResponse> out = new ArrayList<>(rows.size());
		for (Row r : rows) {
			out.add(new RoutePostResponse(r.id.toString(),
					new RoutePostResponse.Author(r.author.toString(), r.displayName, r.avatarUrl), r.title, r.body,
					r.dayIndex, r.dayCount, r.durationMin, r.distanceM, readPath(r.path), stops.get(r.id), r.likes,
					r.saves, r.copies, r.liked, r.saved, r.author.equals(viewer), r.createdAt.toString()));
		}
		return out;
	}

	private record Row(UUID id, UUID author, String displayName, String avatarUrl, String title, String body,
			Integer dayIndex, int dayCount, Integer durationMin, Integer distanceM, String path, int likes, int saves,
			int copies, boolean liked, boolean saved, Instant createdAt) {
	}

	private static Row row(ResultSet rs) throws SQLException {
		return new Row(rs.getObject("route_post_id", UUID.class), rs.getObject("author_user_id", UUID.class),
				rs.getString("display_name"), rs.getString("avatar_url"), rs.getString("title"), rs.getString("body"),
				(Integer) rs.getObject("day_index"), rs.getInt("day_count"), (Integer) rs.getObject("duration_min"),
				(Integer) rs.getObject("distance_m"), rs.getString("path"), rs.getInt("like_count"),
				rs.getInt("save_count"), rs.getInt("copy_count"), rs.getBoolean("liked_by_me"),
				rs.getBoolean("saved_by_me"), rs.getTimestamp("created_at").toInstant());
	}

	private static MapSqlParameterSource params(UUID viewer) {
		return new MapSqlParameterSource("viewer", viewer);
	}

	private String writePath(List<double[]> path) {
		return this.json.writeValueAsString(path);
	}

	private List<double[]> readPath(String path) {
		if (path == null) {
			return List.of();
		}
		try {
			return this.json.readValue(path, new TypeReference<List<double[]>>() {
			});
		}
		catch (JacksonException e) {
			return List.of();
		}
	}

	private static int clamp(Integer limit, int def, int max) {
		if (limit == null || limit <= 0) {
			return def;
		}
		return Math.min(limit, max);
	}

	static String encodeCursor(int offset) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(("o:" + offset).getBytes(StandardCharsets.UTF_8));
	}

	static int decodeCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return 0;
		}
		try {
			String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			if (!raw.startsWith("o:")) {
				throw new RoutePostExceptions.InvalidRouteException("INVALID_CURSOR");
			}
			int offset = Integer.parseInt(raw.substring(2));
			if (offset < 0 || offset > 10_000) {
				throw new RoutePostExceptions.InvalidRouteException("INVALID_CURSOR");
			}
			return offset;
		}
		catch (IllegalArgumentException e) {
			throw new RoutePostExceptions.InvalidRouteException("INVALID_CURSOR");
		}
	}

	private static String truncate(String s, int max) {
		return s.length() <= max ? s : s.substring(0, max);
	}
}
