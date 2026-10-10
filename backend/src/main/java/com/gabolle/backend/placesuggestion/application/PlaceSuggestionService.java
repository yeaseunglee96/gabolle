package com.gabolle.backend.placesuggestion.application;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.gabolle.backend.place.service.BusanBoundary;
import com.gabolle.backend.place.service.GeoDistance;

/**
 * 현지인 장소 제안 — S15P21E201-2031.
 *
 * <p>🔴 <b>제안은 장소가 아니다.</b> {@code place_suggestion} 에 PENDING 으로 쌓이고, 운영자가 승인한 것만
 * {@code place} 에 CURATED · {@code source_type = 'USER'} 로 한 줄 생긴다. 아무도 안 본 이름·좌표가 추천 후보가
 * 되면 안 되기 때문이다.
 *
 * <p>받기 전에 거르는 것:
 * <ul>
 * <li>부산 행정 경계 안인가 — 사각형이 아니라 경계({@link BusanBoundary}, 오픈스트리트맵 적재가 쓰는 것과 같다)</li>
 * <li>이미 있는 장소인가 — {@value #DUPLICATE_RADIUS_M}m 안에 이름이 비슷한 장소·대기 중인 제안({@link NameSimilarity})</li>
 * <li>하루 상한 — 한 사람이 {@value #DAILY_LIMIT}건. 목록을 한 사람이 채우지 못하게</li>
 * <li>욕설·광고 문구({@link TextGuard})</li>
 * <li>사진은 그 사람이 직접 올린 것만 — 기록 사진과 같은 창구·같은 정리(EXIF 위치 제거)를 거친다</li>
 * </ul>
 */
@Service
@Profile({ "db", "dev" })
public class PlaceSuggestionService {

	static final int DUPLICATE_RADIUS_M = 150;

	static final int DAILY_LIMIT = 5;

	/** 추천 이유의 최소 글자 수 — 사용자 지시(2026-10-10). */
	public static final int REASON_MIN = 10;

	/** 추천 결과의 datasetVersion 이 비지 않게 — 이 줄은 수집분이 아니라 사람이 넣은 것이다. */
	static final String DATASET_VERSION = "user-suggestion-v1";

	/** 사람이 고를 수 있는 갈래. 숙소·축제는 뺀다 — 숙소는 추천 갈래가 다르고, 축제는 기간이 있어야 한다. */
	public static final Set<String> CATEGORIES = Set.of("FOOD", "CAFE_HEALING", "SEA_BEACH", "NATURE_WALK",
			"CULTURE_TEMPLE", "SHOPPING", "NIGHT_VIEW", "ACTIVITY", "MARKET");

	private final JdbcTemplate jdbc;

	private final ApprovalNotifier notifier;

	private final Clock clock;

	public PlaceSuggestionService(JdbcTemplate jdbc, ApprovalNotifier notifier, Clock clock) {
		this.jdbc = jdbc;
		this.notifier = notifier;
		this.clock = clock;
	}

	public record SubmitCommand(String name, String category, Double lat, Double lng, String reason, String photoUrl,
			String openingHours, String phone) {
	}

	public record Suggestion(UUID suggestionId, String name, String category, double lat, double lng, String reason,
			String photoUrl, String openingHours, String phone, String status, String rejectReason, UUID placeId,
			UUID suggesterUserId, String suggesterName, OffsetDateTime createdAt) {
	}

	/** 이미 있는 장소(또는 대기 중인 제안). 장소면 {@code placeId}, 제안이면 {@code null}. */
	public record Duplicate(UUID placeId, String name, int distanceM) {
	}

	public record LocalPick(UUID placeId, String suggesterName, String reason, String openingHours, String phone) {
	}

	@Transactional
	public Suggestion submit(UUID userId, SubmitCommand c) {
		String name = trimToNull(c.name());
		String reason = trimToNull(c.reason());
		String hours = trimToNull(c.openingHours());
		String phone = trimToNull(c.phone());
		String photoUrl = trimToNull(c.photoUrl());
		if (name == null || name.length() > 60 || reason == null || reason.length() > 80
				|| c.category() == null || !CATEGORIES.contains(c.category()) || c.lat() == null || c.lng() == null
				|| (hours != null && hours.length() > 120) || (phone != null && !phone.matches("[0-9+()\\- ]{4,30}"))) {
			throw new InvalidSuggestionException("이름·갈래·위치·추천 이유를 확인해 주세요.");
		}
		// 사용자 지시(2026-10-10): 장소·사진·추천 이유를 모두 적게 한다 — 이유는 한 마디가 아니라 한 줄(10자 이상).
		if (reason.codePointCount(0, reason.length()) < REASON_MIN) {
			throw new InvalidSuggestionException("추천 이유를 " + REASON_MIN + "자 이상 적어 주세요.", "reason");
		}
		if (photoUrl == null) {
			throw new PhotoRequiredException();
		}
		if (!BusanBoundary.contains(c.lat(), c.lng())) {
			throw new OutsideBusanException();
		}
		if (TextGuard.blocked(name) || TextGuard.blocked(reason) || TextGuard.blocked(hours)) {
			throw new BlockedTextException();
		}
		OffsetDateTime now = OffsetDateTime.now(this.clock).withOffsetSameInstant(ZoneOffset.UTC);
		// 같은 사람의 제출을 줄 세운다 — 동시에 여섯 번 눌러도 상한을 넘지 않게.
		this.jdbc.queryForList("SELECT user_id FROM app_user WHERE user_id = ? FOR UPDATE", userId);
		Integer recent = this.jdbc.queryForObject(
				"SELECT count(*) FROM place_suggestion WHERE suggester_user_id = ? AND created_at > ?", Integer.class,
				userId, now.minus(Duration.ofDays(1)));
		if (recent != null && recent >= DAILY_LIMIT) {
			throw new RateLimitedException();
		}
		if (photoUrl != null) {
			Integer owned = this.jdbc.queryForObject(
					"SELECT count(*) FROM uploaded_image WHERE image_url = ? AND uploader_user_id = ? AND deleted_at IS NULL",
					Integer.class, photoUrl, userId);
			if (owned == null || owned == 0) {
				throw new InvalidPhotoException();
			}
		}
		List<Duplicate> duplicates = duplicates(name, c.lat(), c.lng());
		if (!duplicates.isEmpty()) {
			throw new DuplicateException(duplicates.get(0));
		}
		UUID id = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO place_suggestion (place_suggestion_id, suggester_user_id, name, category, lat, lng, reason,
				    photo_url, opening_hours, phone, status, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)""", id, userId, name, c.category(), c.lat(), c.lng(),
				reason, photoUrl, hours, phone, now);
		return find(id).orElseThrow();
	}

	/**
	 * 이 이름·위치에 이미 있는 것. 가까운 것부터. 장소는 찾아 주는 조회에 나오는 줄(CURATED)만 본다 — 숨긴 줄·합쳐진
	 * 줄을 「이미 있다」고 하면 눌러도 안 열린다.
	 */
	@Transactional(readOnly = true)
	public List<Duplicate> duplicates(String name, Double lat, Double lng) {
		if (name == null || name.isBlank() || lat == null || lng == null) {
			return List.of();
		}
		// 150m 를 넉넉히 덮는 사각형으로 먼저 줄이고, 거리는 자바에서 잰다.
		double dLat = 0.0025;
		double dLng = 0.0031;
		List<Duplicate> found = new ArrayList<>();
		this.jdbc.query("""
				SELECT place_id, name_ko, lat, lng FROM place
				 WHERE curation_status = 'CURATED' AND lat BETWEEN ? AND ? AND lng BETWEEN ? AND ?""", rs -> {
			add(found, name, lat, lng, (UUID) rs.getObject("place_id"), rs.getString("name_ko"), rs.getDouble("lat"),
					rs.getDouble("lng"));
		}, lat - dLat, lat + dLat, lng - dLng, lng + dLng);
		this.jdbc.query("""
				SELECT name, lat, lng FROM place_suggestion
				 WHERE status = 'PENDING' AND lat BETWEEN ? AND ? AND lng BETWEEN ? AND ?""", rs -> {
			add(found, name, lat, lng, null, rs.getString("name"), rs.getDouble("lat"), rs.getDouble("lng"));
		}, lat - dLat, lat + dLat, lng - dLng, lng + dLng);
		found.sort(Comparator.comparingInt(Duplicate::distanceM));
		return found;
	}

	private static void add(List<Duplicate> found, String name, double lat, double lng, UUID placeId, String otherName,
			double otherLat, double otherLng) {
		double meters = GeoDistance.meters(lat, lng, otherLat, otherLng);
		if (meters <= DUPLICATE_RADIUS_M && NameSimilarity.similar(name, otherName)) {
			found.add(new Duplicate(placeId, otherName, (int) Math.round(meters)));
		}
	}

	@Transactional(readOnly = true)
	public List<Suggestion> mine(UUID userId) {
		return query("WHERE s.suggester_user_id = ? ORDER BY s.created_at DESC LIMIT 50", userId);
	}

	@Transactional(readOnly = true)
	public List<Suggestion> review(String status) {
		String wanted = (status == null || status.isBlank()) ? "PENDING" : status;
		if (!Set.of("PENDING", "APPROVED", "REJECTED").contains(wanted)) {
			throw new InvalidSuggestionException("status 는 PENDING·APPROVED·REJECTED 중 하나입니다.");
		}
		return query("WHERE s.status = ? ORDER BY s.created_at ASC LIMIT 100", wanted);
	}

	/** 승인 — {@code place} 에 CURATED · source=USER 한 줄을 만들고 제안한 사람에게 알린다. */
	@Transactional
	public Suggestion approve(UUID suggestionId, UUID adminId) {
		Suggestion s = lockPending(suggestionId);
		OffsetDateTime now = OffsetDateTime.now(this.clock).withOffsetSameInstant(ZoneOffset.UTC);
		UUID placeId = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO place (place_id, name_ko, category, lat, lng, created_at, source_type, source_id,
				    collected_at, observed_at, dataset_version, photo_url, photo_source, curation_status)
				VALUES (?, ?, ?, ?, ?, ?, 'USER', ?, ?, ?, ?, ?, ?, 'CURATED')""", placeId, s.name(), s.category(),
				s.lat(), s.lng(), now, s.suggestionId().toString(), now, s.createdAt(), DATASET_VERSION, s.photoUrl(),
				s.photoUrl() == null ? null : "현지인 제공");
		this.jdbc.update("""
				UPDATE place_suggestion SET status = 'APPROVED', place_id = ?, reviewed_by = ?, reviewed_at = ?
				 WHERE place_suggestion_id = ?""", placeId, adminId, now, suggestionId);
		String language = this.jdbc.queryForList("SELECT language FROM app_user WHERE user_id = ?", String.class,
				s.suggesterUserId()).stream().findFirst().orElse(null);
		// 승인이 커밋된 뒤에 알린다 — 커밋이 실패했는데 「승인됐어요」가 가면 안 된다.
		Runnable notify = () -> {
			try {
				this.notifier.approved(s.suggesterUserId(), language, s.name(), placeId);
			}
			catch (RuntimeException ignored) {
				// 알림 실패로 승인을 되돌리지 않는다 — 구현이 로그를 남긴다.
			}
		};
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					notify.run();
				}
			});
		}
		else {
			notify.run();
		}
		return find(suggestionId).orElseThrow();
	}

	@Transactional
	public Suggestion reject(UUID suggestionId, UUID adminId, String reason) {
		lockPending(suggestionId);
		String why = trimToNull(reason);
		if (why != null && why.length() > 200) {
			why = why.substring(0, 200);
		}
		this.jdbc.update("""
				UPDATE place_suggestion SET status = 'REJECTED', reject_reason = ?, reviewed_by = ?, reviewed_at = ?
				 WHERE place_suggestion_id = ?""", why, adminId,
				OffsetDateTime.now(this.clock).withOffsetSameInstant(ZoneOffset.UTC), suggestionId);
		return find(suggestionId).orElseThrow();
	}

	/** 이 장소가 현지인 제안으로 생겼으면 누가·왜 추천했는지. */
	@Transactional(readOnly = true)
	public Optional<LocalPick> localPick(UUID placeId) {
		return query("WHERE s.place_id = ? AND s.status = 'APPROVED'", placeId).stream().findFirst()
				.map(s -> new LocalPick(placeId, s.suggesterName(), s.reason(), s.openingHours(), s.phone()));
	}

	private Suggestion lockPending(UUID suggestionId) {
		List<Map<String, Object>> rows = this.jdbc.queryForList(
				"SELECT status FROM place_suggestion WHERE place_suggestion_id = ? FOR UPDATE", suggestionId);
		if (rows.isEmpty()) {
			throw new SuggestionNotFoundException();
		}
		if (!"PENDING".equals(rows.get(0).get("status"))) {
			throw new AlreadyReviewedException();
		}
		return find(suggestionId).orElseThrow();
	}

	private Optional<Suggestion> find(UUID id) {
		return query("WHERE s.place_suggestion_id = ?", id).stream().findFirst();
	}

	private List<Suggestion> query(String where, Object arg) {
		return this.jdbc.query("""
				SELECT s.*, u.display_name FROM place_suggestion s
				  LEFT JOIN app_user u ON u.user_id = s.suggester_user_id
				""" + where, (rs, i) -> new Suggestion((UUID) rs.getObject("place_suggestion_id"), rs.getString("name"),
				rs.getString("category"), rs.getDouble("lat"), rs.getDouble("lng"), rs.getString("reason"),
				rs.getString("photo_url"), rs.getString("opening_hours"), rs.getString("phone"), rs.getString("status"),
				rs.getString("reject_reason"), (UUID) rs.getObject("place_id"),
				(UUID) rs.getObject("suggester_user_id"), rs.getString("display_name"),
				rs.getObject("created_at", OffsetDateTime.class)), arg);
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String t = value.trim();
		return t.isEmpty() ? null : t;
	}

	public static class InvalidSuggestionException extends RuntimeException {

		private final java.util.List<String> fields;

		public InvalidSuggestionException(String message, String... fields) {
			super(message);
			this.fields = java.util.List.of(fields);
		}

		public java.util.List<String> fields() {
			return this.fields;
		}
	}

	public static class PhotoRequiredException extends RuntimeException {

		public PhotoRequiredException() {
			super("사진을 한 장 이상 올려 주세요.");
		}
	}

	public static class OutsideBusanException extends RuntimeException {

		public OutsideBusanException() {
			super("부산 안의 장소만 제안할 수 있어요.");
		}
	}

	public static class BlockedTextException extends RuntimeException {

		public BlockedTextException() {
			super("욕설·광고·연락처 문구는 넣을 수 없어요.");
		}
	}

	public static class RateLimitedException extends RuntimeException {

		public RateLimitedException() {
			super("하루에 " + DAILY_LIMIT + "곳까지 제안할 수 있어요.");
		}
	}

	public static class InvalidPhotoException extends RuntimeException {

		public InvalidPhotoException() {
			super("직접 올린 사진만 붙일 수 있어요.");
		}
	}

	public static class DuplicateException extends RuntimeException {

		private final Duplicate duplicate;

		public DuplicateException(Duplicate duplicate) {
			super("이미 있는 장소예요: " + duplicate.name());
			this.duplicate = duplicate;
		}

		public Duplicate duplicate() {
			return this.duplicate;
		}
	}

	public static class SuggestionNotFoundException extends RuntimeException {

		public SuggestionNotFoundException() {
			super("제안을 찾을 수 없어요.");
		}
	}

	public static class AlreadyReviewedException extends RuntimeException {

		public AlreadyReviewedException() {
			super("이미 검토한 제안이에요.");
		}
	}
}
