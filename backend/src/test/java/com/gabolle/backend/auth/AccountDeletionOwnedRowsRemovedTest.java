package com.gabolle.backend.auth;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.auth.service.AccountDeletionService;
import com.gabolle.backend.auth.support.AuthPostgresIntegrationTest;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code AccountDeletionService.USER_OWNED_ROWS} 에 지운다고 적어 둔 표가 실제로 지워지는가.
 *
 * <p>목록이 문자열이라 오타는 컴파일을 통과한다. 게다가 행이 안 지워져도 탈퇴는 성공으로
 * 끝난다 — 계정 행을 익명화만 하므로 {@code ON DELETE CASCADE} 가 안 터지고 오류도 안 난다.
 *
 * <p>지우기 전에 시드가 들어갔는지를 먼저 본다. 시드가 조용히 실패하면 지운 뒤에도 0건이라
 * 검사가 거저 통과한다.
 *
 * <p>{@code story_reaction}·{@code trip_invite}·{@code trip_share_link} 는 남의 글·남의 여행에
 * 단다. 자기 것에 달면 글·여행이 지워지면서 딸려 없어져, 목록이 고장나도 통과한다.
 *
 * <p>Postgres 가 없으면 건너뛰므로 실제 판정은 CI 에서 난다.
 */
class AccountDeletionOwnedRowsRemovedTest extends AuthPostgresIntegrationTest {

	private static final String PASSWORD = "DeleteMe!2026";

	private static final String CONFIRM = AccountDeletionService.CONFIRMATION_PHRASE;

	/**
	 * 탈퇴 뒤 이 사람을 가리키는 행이 하나도 없어야 하는 자리.
	 *
	 * <p>{@code user_follow}·{@code user_block} 은 사람을 가리키는 칸이 둘이라 두 줄씩이다.
	 * 한쪽만 지우면 없는 사람을 팔로우한 기록이 남는다.
	 */
	private record Owned(String table, String userColumn) {
	}

	private static final List<Owned> OWNED = List.of(
			new Owned("saved_place", "user_id"),
			new Owned("collection", "user_id"),
			new Owned("place_review", "user_id"),
			new Owned("place_visit_verification", "user_id"),
			new Owned("menu_scan_usage", "user_id"),
			new Owned("dish_image_usage", "user_id"),
			new Owned("user_follow", "follower_user_id"),
			new Owned("user_follow", "followee_user_id"),
			new Owned("user_block", "blocker_user_id"),
			new Owned("user_block", "blocked_user_id"),
			new Owned("story_reaction", "user_id"),
			new Owned("story_view", "user_id"),
			new Owned("story_link_copy", "user_id"),
			new Owned("trip_invite", "created_by"),
			new Owned("trip_share_link", "created_by"),
			new Owned("oauth_signup_ticket", "existing_user_id"),
			// 알레르기·식단이 들어 있는 자리라 반드시 지워져야 한다.
			new Owned("user_travel_constraint", "user_id"),
			// 🔴 기기 푸시 토큰. 남으면 탈퇴한 사람의 폰으로 알림이 계속 간다 (S15P21E201-1391).
			new Owned("push_token", "user_id"),
			// 남의 여행에 매긴 별점 (S15P21E201-1908).
			new Owned("trip_rating", "user_id"),
			// 이 사람이 적은 쓴 돈 (S15P21E201-1935).
			new Owned("trip_expense", "created_by"));

	@Autowired
	private AccountDeletionService accountDeletionService;

	@Autowired
	private LocalCredentialRepository credentialRepository;

	@Autowired
	private AppUserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private UUID userId;

	private UUID otherUserId;

	private UUID placeId;

	private UUID otherTripId;

	private UUID otherStoryId;

	@BeforeEach
	void setUp() {
		this.userId = createUser("erase-" + shortId() + "@example.com");
		this.otherUserId = createUser("keep-" + shortId() + "@example.com");
		this.placeId = createPlace();
		this.otherTripId = createTrip(this.otherUserId);
		this.otherStoryId = createStory(this.otherUserId);

		seedFor(this.userId);
	}

	@AfterEach
	void tearDown() {
		// 표를 비우지 않고 내가 만든 것만 지운다 — 같은 DB 를 여러 검사가 함께 쓴다.
		for (UUID user : new UUID[] { this.userId, this.otherUserId }) {
			for (Owned owned : OWNED) {
				this.jdbc.update("DELETE FROM " + owned.table() + " WHERE " + owned.userColumn() + " = ?", user);
			}
		}
		this.jdbc.update("DELETE FROM story WHERE story_id = ?", this.otherStoryId);
		this.jdbc.update("DELETE FROM trip WHERE trip_id = ?", this.otherTripId);
		this.jdbc.update("DELETE FROM place WHERE place_id = ?", this.placeId);
		for (UUID user : new UUID[] { this.userId, this.otherUserId }) {
			this.jdbc.update("DELETE FROM auth_session WHERE user_id = ?", user);
			this.jdbc.update("DELETE FROM local_credential WHERE user_id = ?", user);
			this.jdbc.update("DELETE FROM app_user WHERE user_id = ?", user);
		}
	}

	@Test
	@DisplayName("🔴 지운다고 적어 둔 자리가 탈퇴 뒤 전부 0건이다 — 애플 심사 5.1.1(v)")
	void everyOwnedRowIsActuallyGone() {
		// 먼저 넣었는지 본다. 시드가 조용히 실패하면 아래 검사가 거저 통과한다.
		List<String> notSeeded = new ArrayList<>();
		for (Owned owned : OWNED) {
			if (countFor(owned, this.userId) == 0) {
				notSeeded.add(owned.table() + "." + owned.userColumn());
			}
		}
		assertThat(notSeeded).as("""

				시드가 안 들어간 자리입니다: %s

				   이 검사는 "넣고 → 지우고 → 0인가" 를 봅니다. 안 넣은 자리는 지운 뒤에도 0이라
				   검사가 거저 통과합니다. 표 구조가 바뀌었다면 seedFor 를 고치십시오.
				""".formatted(notSeeded)).isEmpty();

		this.accountDeletionService.delete(this.userId, CONFIRM, PASSWORD);

		List<String> leftover = new ArrayList<>();
		for (Owned owned : OWNED) {
			long remaining = countFor(owned, this.userId);
			if (remaining > 0) {
				leftover.add(owned.table() + "." + owned.userColumn() + " = " + remaining + "건");
			}
		}
		assertThat(leftover).as("""

				🔴 탈퇴했는데 이 사람을 가리키는 행이 남았습니다: %s

				   애플 심사 5.1.1(v) 는 계정 삭제 시 이용자 자료가 실제로 지워질 것을 요구합니다.
				   AccountDeletionService.USER_OWNED_ROWS 에 그 표와 칸이 있는지,
				   칸 경로(묻힌 키는 key.x / id.x)가 맞는지 보십시오.
				   🔴 행이 안 지워져도 탈퇴는 성공으로 끝납니다 — 계정 행을 익명화만 하므로
				      ON DELETE CASCADE 가 한 번도 안 터지고 오류도 안 납니다.
				""".formatted(leftover)).isEmpty();
	}

	@Test
	@DisplayName("남의 팔로우·차단 기록은 그대로다 — 탈퇴가 남의 자료까지 쓸어 가지 않는다")
	void otherPeoplesRowsSurvive() {
		UUID thirdUserId = createUser("third-" + shortId() + "@example.com");
		this.jdbc.update("INSERT INTO user_follow (follower_user_id, followee_user_id, created_at) "
				+ "VALUES (?, ?, now())", this.otherUserId, thirdUserId);
		this.jdbc.update("INSERT INTO user_block (blocker_user_id, blocked_user_id, created_at) "
				+ "VALUES (?, ?, now())", this.otherUserId, thirdUserId);

		this.accountDeletionService.delete(this.userId, CONFIRM, PASSWORD);

		assertThat(countBetween("user_follow", "follower_user_id", "followee_user_id", this.otherUserId, thirdUserId))
				.as("남의 팔로우 기록까지 지워졌다").isEqualTo(1L);
		assertThat(countBetween("user_block", "blocker_user_id", "blocked_user_id", this.otherUserId, thirdUserId))
				.as("남의 차단 기록까지 지워졌다").isEqualTo(1L);

		this.jdbc.update("DELETE FROM user_follow WHERE follower_user_id = ? OR followee_user_id = ?",
				thirdUserId, thirdUserId);
		this.jdbc.update("DELETE FROM user_block WHERE blocker_user_id = ? OR blocked_user_id = ?",
				thirdUserId, thirdUserId);
		this.jdbc.update("DELETE FROM local_credential WHERE user_id = ?", thirdUserId);
		this.jdbc.update("DELETE FROM app_user WHERE user_id = ?", thirdUserId);
	}

	/**
	 * 🔴 탈퇴가 취향 <b>무게</b>까지 지우는지 본다 — S15P21E201-1500 을 위한 안전장치.
	 *
	 * <p><b>왜 위의 검사로는 못 잡나.</b> 위 {@link #everyOwnedRowIsActuallyGone()} 은
	 * {@code USER_OWNED_ROWS}(사용자 칸을 직접 가진 표들)를 돌지만, {@code user_taste_weight}
	 * 에는 <b>사용자 칸이 없다</b> — 벡터를 가리킨다. 그래서 그 목록에 못 들어가고,
	 * 실제로 지우는 것은 {@code deletePersonalizationArtifacts()} 의 별도 JPQL 이다.
	 * <b>지금까지 그 JPQL 을 보는 검사가 하나도 없었다.</b>
	 *
	 * <p><b>왜 지금 필요한가.</b> {@code S15P21E201-1500} 이 무게를 <b>소비자가 증분으로
	 * 더하는</b> 모양으로 바꾼다. 그러면 행동 한 건의 기여가 이벤트가 아니라 <b>이 표 안에</b>
	 * 들어앉는다 — 원본 이벤트를 지워도 파생값이 남을 수 있는 모양이 된다. 애플 심사에
	 * <i>"계정과 이용자 자료를 지운다"</i>고 이미 선언했고(5.1.1(v)), Play 데이터 안전 양식
	 * ({@code S15P21E201-1018})도 아직 제출 전이라 <b>여기서 뭐라고 하느냐가 그 양식에
	 * 적힐 문장</b>이다.
	 *
	 * <p>같은 함정을 이 저장소가 이미 한 번 겪었다 — 푸시 토큰에 {@code ON DELETE CASCADE} 가
	 * 걸려 있었는데 탈퇴가 {@code app_user} 를 <b>익명화</b>해서 그 규칙이 한 번도 안 돌았다.
	 * 취향 쪽은 부모가 {@code app_user} 가 아니라 벡터이고 그 벡터를 실제로 지우므로 지금은
	 * 안전한데, <b>그 안전이 검사로 묶여 있지 않았다.</b>
	 */
	@Test
	@DisplayName("🔴 탈퇴하면 취향 «무게»까지 지워진다 — 벡터만 보면 못 잡는다 (S15P21E201-1500)")
	void tasteWeightsAreDeletedWithTheVector() {
		UUID myVector = insertTasteVector(this.userId);
		// 설문에서 온 무게와 행동에서 온 무게를 둘 다 넣는다. -1500 뒤에는 뒤엣것이
		// 소비자가 증분으로 쌓는 줄이 되므로, 그때도 같이 지워지는지가 이 검사의 요점이다.
		insertTasteWeight(myVector, "CATEGORY", "FOOD", "SURVEY");
		insertTasteWeight(myVector, "ATMOSPHERE", "QUIET", "INTERACTION");

		UUID othersVector = insertTasteVector(this.otherUserId);
		insertTasteWeight(othersVector, "CATEGORY", "FOOD", "SURVEY");

		// 시드가 조용히 실패하면 아래 검사가 거저 통과한다.
		assertThat(tasteWeightCount(myVector)).as("시드가 안 들어갔다 — 검사가 거저 통과한다").isEqualTo(2L);

		this.accountDeletionService.delete(this.userId, CONFIRM, PASSWORD);

		assertThat(tasteWeightCount(myVector)).as("""

				🔴 탈퇴했는데 취향 무게가 남았습니다.

				   원본 이벤트를 지워도 이 표가 그 사람의 행동을 계속 담고 있으면,
				   「계정과 이용자 자료를 지운다」(애플 심사 5.1.1(v))는 선언과 실제가 어긋납니다.
				   AccountDeletionService.deletePersonalizationArtifacts() 를 보십시오.
				""").isZero();
		assertThat(tasteVectorCount(this.userId)).as("벡터도 함께 지워져야 한다").isZero();
		assertThat(tasteWeightCount(othersVector))
				.as("남의 취향까지 쓸어 갔다 — 탈퇴는 그 사람 것만 지운다").isEqualTo(1L);

		this.jdbc.update("DELETE FROM user_taste_weight WHERE taste_vector_id = ?", othersVector);
		this.jdbc.update("DELETE FROM user_taste_vector WHERE taste_vector_id = ?", othersVector);
	}

	private UUID insertTasteVector(UUID user) {
		UUID vectorId = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO user_taste_vector
				    (taste_vector_id, user_id, version, observed_event_count,
				     vector_version, ontology_version, created_at)
				VALUES (?, ?, 1, 0, 'test-vector-v1', 'test-ontology-v1', now())
				""", vectorId, user);
		return vectorId;
	}

	/** {@code evidence} 가 키의 일부다 (S15P21E201-1499) — 설문 몫과 행동 몫이 다른 줄로 앉는다. */
	private void insertTasteWeight(UUID vectorId, String dimension, String code, String evidence) {
		this.jdbc.update("""
				INSERT INTO user_taste_weight
				    (taste_vector_id, dimension, code, evidence, weight, raw, support, updated_at)
				VALUES (?, ?, ?, ?, 0.5, 3.0, 1, now())
				""", vectorId, dimension, code, evidence);
	}

	private long tasteWeightCount(UUID vectorId) {
		Long n = this.jdbc.queryForObject(
				"SELECT count(*) FROM user_taste_weight WHERE taste_vector_id = ?", Long.class, vectorId);
		return n == null ? 0 : n;
	}

	private long tasteVectorCount(UUID user) {
		Long n = this.jdbc.queryForObject(
				"SELECT count(*) FROM user_taste_vector WHERE user_id = ?", Long.class, user);
		return n == null ? 0 : n;
	}

	private long countBetween(String table, String left, String right, UUID leftUser, UUID rightUser) {
		Long n = this.jdbc.queryForObject(
				"SELECT count(*) FROM " + table + " WHERE " + left + " = ? AND " + right + " = ?",
				Long.class, leftUser, rightUser);
		return n == null ? 0 : n;
	}

	private long countFor(Owned owned, UUID user) {
		Long n = this.jdbc.queryForObject(
				"SELECT count(*) FROM " + owned.table() + " WHERE " + owned.userColumn() + " = ?",
				Long.class, user);
		return n == null ? 0 : n;
	}

	private void seedFor(UUID user) {
		this.jdbc.update("INSERT INTO saved_place (saved_place_id, user_id, place_id, created_at) "
				+ "VALUES (?, ?, ?, now())", UUID.randomUUID(), user, this.placeId);
		this.jdbc.update("INSERT INTO collection (collection_id, user_id, name, created_at, updated_at) "
				+ "VALUES (?, ?, '가보고 싶은 곳', now(), now())", UUID.randomUUID(), user);
		this.jdbc.update("INSERT INTO place_review (place_review_id, place_id, user_id, verified, food_score) "
				+ "VALUES (?, ?, ?, false, 4)", UUID.randomUUID(), this.placeId, user);
		this.jdbc.update("INSERT INTO place_visit_verification "
				+ "(place_visit_verification_id, place_id, user_id, distance_m) VALUES (?, ?, ?, 12)",
				UUID.randomUUID(), this.placeId, user);
		this.jdbc.update("INSERT INTO menu_scan_usage (menu_scan_usage_id, user_id, scanned_at) "
				+ "VALUES (?, ?, now())", UUID.randomUUID(), user);
		this.jdbc.update("INSERT INTO dish_image_usage (dish_image_usage_id, user_id, requested_at) "
				+ "VALUES (?, ?, now())", UUID.randomUUID(), user);
		this.jdbc.update("INSERT INTO story_reaction (story_id, user_id, reaction, created_at, updated_at) "
				+ "VALUES (?, ?, 'LIKE', now(), now())", this.otherStoryId, user);
		this.jdbc.update("INSERT INTO story_view "
				+ "(story_view_id, story_id, user_id, viewed_on, created_at) "
				+ "VALUES (?, ?, ?, current_date, now())", UUID.randomUUID(), this.otherStoryId, user);
		this.jdbc.update("INSERT INTO story_link_copy "
				+ "(story_link_copy_id, story_id, user_id, copied_on, created_at) "
				+ "VALUES (?, ?, ?, current_date, now())", UUID.randomUUID(), this.otherStoryId, user);
		// SAVED 로 심어야 value 가 채워져 ck_user_travel_constraint_value_matches_status 를 지난다.
		// 값이 있는 상태로 지워지는지를 보는 자리다.
		this.jdbc.update("INSERT INTO user_travel_constraint "
				+ "(user_id, status, value, created_at, updated_at) "
				+ "VALUES (?, 'SAVED', ?::jsonb, now(), now())", user, "{\"allergies\":[\"peanut\"]}");
		this.jdbc.update("INSERT INTO trip_invite "
				+ "(trip_invite_id, trip_id, token, role, created_by, created_at, expires_at) "
				+ "VALUES (?, ?, ?, 'EDITOR', ?, now(), now() + interval '7 day')",
				UUID.randomUUID(), this.otherTripId, "inv-" + shortId(), user);
		this.jdbc.update("INSERT INTO trip_share_link "
				+ "(trip_share_link_id, trip_id, token, created_by, created_at, expires_at) "
				+ "VALUES (?, ?, ?, ?, now(), now() + interval '30 day')",
				UUID.randomUUID(), this.otherTripId, "shr-" + shortId(), user);
		this.jdbc.update("INSERT INTO oauth_signup_ticket "
				+ "(oauth_ticket_id, kind, ticket_hash, provider, provider_subject, existing_user_id, "
				+ "created_at, expires_at) "
				+ "VALUES (?, 'LINK', ?, 'GOOGLE', ?, ?, now(), now() + interval '1 hour')",
				UUID.randomUUID(), "hash-" + shortId(), "subject-" + shortId(), user);
		// 사람을 가리키는 칸이 둘인 표. 양쪽 칸이 다 걸리도록 두 줄씩 넣는다.
		this.jdbc.update("INSERT INTO user_follow (follower_user_id, followee_user_id, created_at) "
				+ "VALUES (?, ?, now())", user, this.otherUserId);
		this.jdbc.update("INSERT INTO user_follow (follower_user_id, followee_user_id, created_at) "
				+ "VALUES (?, ?, now())", this.otherUserId, user);
		this.jdbc.update("INSERT INTO user_block (blocker_user_id, blocked_user_id, created_at) "
				+ "VALUES (?, ?, now())", user, this.otherUserId);
		this.jdbc.update("INSERT INTO user_block (blocker_user_id, blocked_user_id, created_at) "
				+ "VALUES (?, ?, now())", this.otherUserId, user);
		this.jdbc.update("INSERT INTO push_token (push_token_id, user_id, token, platform, created_at, updated_at) "
				+ "VALUES (?, ?, ?, 'android', now(), now())",
				UUID.randomUUID(), user, "ExponentPushToken[" + shortId() + "]");
		this.jdbc.update("INSERT INTO trip_rating (trip_id, user_id, score, created_at, updated_at) "
				+ "VALUES (?, ?, 4, now(), now())", this.otherTripId, user);
		this.jdbc.update("INSERT INTO trip_expense (expense_id, trip_id, created_by, paid_by, amount_krw, category, split_even, spent_at, created_at) "
				+ "VALUES (?, ?, ?, ?, 18000, 'FOOD', true, now(), now())", UUID.randomUUID(), this.otherTripId, user, user);
	}

	private UUID createUser(String address) {
		return this.transactionTemplate.execute(status -> {
			AppUser user = this.userRepository.save(AppUser.register("여행자", "KO", Instant.now(), "2026-01",
					PersonalizationMode.EXPLICIT_ONLY, UserStatus.ACTIVE));
			LocalCredential credential = LocalCredential.create(user, address,
					this.passwordEncoder.encode(PASSWORD));
			credential.markEmailVerified(Instant.now());
			this.credentialRepository.save(credential);
			return user.getUserId();
		});
	}

	private UUID createPlace() {
		UUID newPlaceId = UUID.randomUUID();
		this.jdbc.update("INSERT INTO place (place_id, name_ko, address, lat, lng, created_at) "
				+ "VALUES (?, '동래할매파전', '부산광역시 동래구 명륜동', 35.16, 129.16, now())", newPlaceId);
		return newPlaceId;
	}

	private UUID createTrip(UUID owner) {
		UUID trip = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO trip (trip_id, owner_user_id, owner_type, start_date, end_date, created_at, updated_at)
				VALUES (?, ?, 'USER', ?, ?, now(), now())
				""", trip, owner, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12));
		return trip;
	}

	private UUID createStory(UUID author) {
		UUID storyId = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO story (story_id, author_user_id, body, visibility, publish_at, created_at, updated_at)
				VALUES (?, ?, ?, 'PUBLIC', now(), now(), now())
				""", storyId, author, "테스트 기록");
		return storyId;
	}

	private static String shortId() {
		return UUID.randomUUID().toString().substring(0, 8);
	}
}
