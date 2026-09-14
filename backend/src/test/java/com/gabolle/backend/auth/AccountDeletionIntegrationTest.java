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

import com.gabolle.backend.auth.api.AccountDeletionPreviewResponse;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.auth.service.AccountDeletionService;
import com.gabolle.backend.auth.service.AuthCommands;
import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.auth.service.LocalAuthService;
import com.gabolle.backend.auth.support.AuthPostgresIntegrationTest;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 계정·데이터 일괄 삭제 — S15P21E201-425 (-183 포함).
 *
 * <h2>🔴 왜 진짜 DB 가 필요한가</h2>
 *
 * 이 기능의 위험은 두 가지이고 둘 다 실제 DB 에서만 드러난다.
 *
 * <p>하나는 <b>지우는 순서</b>다. 자식 행을 먼저 지우지 않으면 외래키에 걸려 통째로 실패한다.
 * 순서가 틀렸는지는 실제 제약이 걸린 표에서만 알 수 있다.
 *
 * <p>다른 하나는 <b>JPQL 이 가리키는 엔티티 이름</b>이다. 다른 담당자의 코드를 고치지 않으려고
 * 문자열로 썼기 때문에, 그쪽이 이름을 바꾸면 컴파일이 아니라 실행에서 터진다. 이 테스트가 삭제
 * 경로를 통째로 돌려서 그것을 잡는다.
 */
class AccountDeletionIntegrationTest extends AuthPostgresIntegrationTest {

	private static final String PASSWORD = "DeleteMe!2026";

	/** 사용자가 탈퇴 화면에서 직접 치는 값 — S15P21E201-837. */
	private static final String CONFIRM = AccountDeletionService.CONFIRMATION_PHRASE;

	@Autowired
	private AccountDeletionService accountDeletionService;

	@Autowired
	private LocalAuthService localAuthService;

	@Autowired
	private LocalCredentialRepository credentialRepository;

	@Autowired
	private AppUserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private UUID userId;

	private String email;

	private UUID tripId;

	private UUID otherUserId;

	private UUID otherTripId;

	/** S15P21E201-837 — 소셜로만 가입한 계정. 만든 것만 tearDown 에서 지운다. */
	private final List<UUID> socialUserIds = new ArrayList<>();

	@BeforeEach
	void setUp() {
		this.email = "erase-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		this.userId = createUser(this.email);
		this.tripId = createTrip(this.userId);

		this.otherUserId = createUser("keep-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
		this.otherTripId = createTrip(this.otherUserId);
	}

	@AfterEach
	void tearDown() {
		// 🔴 표를 비우지 않는다. 내가 만든 것만 지운다.
		for (UUID user : new UUID[] {this.userId, this.otherUserId}) {
			this.jdbcTemplate.update("DELETE FROM story WHERE author_user_id = ?", user);
		}
		for (UUID trip : new UUID[] {this.tripId, this.otherTripId}) {
			this.jdbcTemplate.update("DELETE FROM trip WHERE trip_id = ?", trip);
		}
		for (UUID user : new UUID[] {this.userId, this.otherUserId}) {
			this.jdbcTemplate.update("DELETE FROM auth_session WHERE user_id = ?", user);
			this.jdbcTemplate.update("DELETE FROM app_user WHERE user_id = ?", user);
		}
		// S15P21E201-837 — auth_identity 는 app_user 에 ON DELETE CASCADE 로 달려 있지만,
		// 지우는 순서를 코드로 못 박아 둔다. 이 표가 남으면 provider_subject 유일 제약에 걸려
		// 다음 실행이 깨진다.
		for (UUID user : this.socialUserIds) {
			this.jdbcTemplate.update("DELETE FROM auth_identity WHERE user_id = ?", user);
			this.jdbcTemplate.update("DELETE FROM auth_session WHERE user_id = ?", user);
			this.jdbcTemplate.update("DELETE FROM app_user WHERE user_id = ?", user);
		}
		this.socialUserIds.clear();
	}

	@Test
	@DisplayName("완료 기준 — 올바른 비밀번호로 부르면 로그인 수단이 사라지고 같은 이메일로 로그인이 안 된다")
	void deletesLoginMeansAndBlocksLogin() {
		assertThat(this.credentialRepository.findByUserUserId(this.userId)).isPresent();

		this.accountDeletionService.delete(this.userId, CONFIRM, PASSWORD);

		assertThat(this.credentialRepository.findByUserUserId(this.userId)).isEmpty();
		assertThat(countByUser("local_credential", this.userId)).isZero();
		assertThat(countByUser("auth_session", this.userId)).isZero();
		assertThat(countByUser("user_consent", this.userId)).isZero();

		assertThatThrownBy(() -> this.localAuthService.login(
				new AuthCommands.Login(this.email, PASSWORD, "device")))
				.isInstanceOf(AuthException.class);
	}

	@Test
	@DisplayName("🔴 계정 행은 남지만 개인을 알아볼 값이 없다 — 남의 일정 이력이 이 행을 가리키기 때문")
	void accountRowRemainsButCarriesNoPersonalData() {
		this.accountDeletionService.delete(this.userId, CONFIRM, PASSWORD);

		AppUser remaining = this.userRepository.findById(this.userId).orElseThrow();
		assertThat(remaining.getStatus()).isEqualTo(UserStatus.DELETED);
		assertThat(remaining.getDeletedAt()).isNotNull();
		assertThat(remaining.getDisplayName()).isEqualTo("탈퇴한 사용자");
		assertThat(remaining.getAgeVerifiedAt()).isNull();
	}

	@Test
	@DisplayName("🔴 preview — 본인 여행·기록 수를 세되, 삭제는 하지 않는다")
	void previewCountsWithoutDeleting() {
		createStory(this.userId);
		createStory(this.userId);
		createStory(this.otherUserId);

		AccountDeletionPreviewResponse preview = this.accountDeletionService.preview(this.userId);

		assertThat(preview.ownedTripCount()).isEqualTo(1);
		assertThat(preview.itineraryCount()).isZero();
		assertThat(preview.recordCount()).isEqualTo(2);
		// 🔴 이름 그대로 미리보기다 — 아무것도 지워지면 안 된다.
		assertThat(tripExists(this.tripId)).isTrue();
		assertThat(this.credentialRepository.findByUserUserId(this.userId)).isPresent();
	}

	/**
	 * S15P21E201-913 — 이미 지운 여행이 안내 숫자에 섞이던 자리.
	 *
	 * <p>"내 여행" 목록은 {@code deleted_at} 이 빈 것만 내주는데 미리보기는 그 조건이 없어서,
	 * 목록에 한 개뿐인 계정에 "3개가 삭제돼요" 가 떴다. 되돌릴 수 없는 동작의 안내 숫자라
	 * 실제보다 크게 보이면 사용자가 무엇을 잃는지 잘못 알고 결정하게 된다.
	 */
	@Test
	@DisplayName("preview — 이미 지운 여행과 그 일정은 세지 않는다")
	void previewSkipsSoftDeletedTrips() {
		UUID deletedTrip = createTrip(this.userId);
		this.jdbcTemplate.update("UPDATE trip SET deleted_at = now() WHERE trip_id = ?", deletedTrip);

		AccountDeletionPreviewResponse preview = this.accountDeletionService.preview(this.userId);

		// 살아 있는 여행은 setUp 이 만든 하나뿐이다.
		assertThat(preview.ownedTripCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 삭제하면 본인 기록도 (deleted_at 찍는 방식으로) 사라지고, 남의 기록은 그대로다")
	void deletesOwnStoriesButNotOthers() {
		UUID myStory = createStory(this.userId);
		UUID otherStory = createStory(this.otherUserId);

		this.accountDeletionService.delete(this.userId, CONFIRM, PASSWORD);

		assertThat(storyDeletedAt(myStory)).isNotNull();
		assertThat(storyDeletedAt(otherStory)).isNull();
	}

	@Test
	@DisplayName("완료 기준 — 본인 여행이 사라진다 (지우는 순서와 JPQL 엔티티 이름이 맞는지가 여기서 드러난다)")
	void deletesOwnTrips() {
		assertThat(tripExists(this.tripId)).isTrue();

		this.accountDeletionService.delete(this.userId, CONFIRM, PASSWORD);

		assertThat(tripExists(this.tripId)).isFalse();
	}

	@Test
	@DisplayName("완료 기준 — 틀린 비밀번호는 거부되고 아무것도 지워지지 않는다")
	void wrongPasswordDeletesNothing() {
		assertThatThrownBy(() -> this.accountDeletionService.delete(this.userId, CONFIRM, "NotThePassword!1"))
				.isInstanceOf(AuthException.class)
				.extracting(exception -> ((AuthException) exception).getCode())
				.isEqualTo("INVALID_CREDENTIALS");

		assertThat(this.credentialRepository.findByUserUserId(this.userId)).isPresent();
		assertThat(tripExists(this.tripId)).isTrue();
		assertThat(this.userRepository.findById(this.userId).orElseThrow().getStatus())
				.isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	@DisplayName("🔴 남의 계정과 여행은 손대지 않는다")
	void otherPeopleAreUntouched() {
		this.accountDeletionService.delete(this.userId, CONFIRM, PASSWORD);

		assertThat(tripExists(this.otherTripId)).isTrue();
		assertThat(this.credentialRepository.findByUserUserId(this.otherUserId)).isPresent();
		assertThat(this.userRepository.findById(this.otherUserId).orElseThrow().getStatus())
				.isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	@DisplayName("이미 지운 계정을 다시 지우려 하면 쓸 수 없는 계정이라고 거절한다 — 검사 순서를 못 박는다")
	void deletingTwiceIsRejected() {
		this.accountDeletionService.delete(this.userId, CONFIRM, PASSWORD);

		// 🔴 S15P21E201-837 이전에는 자격증명이 사라진 덕분에 LOCAL_CREDENTIAL_REQUIRED 로 막혔다.
		// 비밀번호가 선택이 된 지금은 그 우연한 방어가 없어서 상태를 직접 본다.
		//
		// 🔴 이 검사는 **순서**도 함께 못 박는다. 비밀번호를 실어 보내는 것이 핵심이다 — 계정 상태를
		// 비밀번호보다 나중에 보면, 자격증명이 이미 사라졌으므로 PASSWORD_NOT_SET("소셜 계정입니다")
		// 이 나간다. 사실과 다른 안내이고, 쓸 수 없는 계정에 대해 "비밀번호가 있는 계정인가" 를
		// 알려 주는 것이기도 하다. 실제로 그렇게 짰다가 CI 에서 잡혔다(파이프라인 189067).
		assertThatThrownBy(() -> this.accountDeletionService.delete(this.userId, CONFIRM, PASSWORD))
				.isInstanceOf(AuthException.class)
				.extracting(exception -> ((AuthException) exception).getCode())
				.isEqualTo("ACCOUNT_UNAVAILABLE");
	}

	// ── S15P21E201-837 · 소셜로만 가입한 계정 ────────────────────────────────────

	@Test
	@DisplayName("완료 기준 — 소셜로만 가입한 계정이 확인 값만으로 탈퇴된다 (비밀번호가 없다)")
	void socialOnlyAccountCanBeDeletedWithConfirmationAlone() {
		UUID socialUserId = createSocialOnlyUser("apple-" + UUID.randomUUID());
		assertThat(this.credentialRepository.findByUserUserId(socialUserId)).isEmpty();
		assertThat(countByUser("auth_identity", socialUserId)).isEqualTo(1);

		this.accountDeletionService.delete(socialUserId, CONFIRM, null);

		// 소셜 계정의 로그인 수단은 auth_identity 다. 그것이 남으면 지운 것이 아니다.
		assertThat(countByUser("auth_identity", socialUserId)).isZero();
		assertThat(countByUser("auth_session", socialUserId)).isZero();

		AppUser remaining = this.userRepository.findById(socialUserId).orElseThrow();
		assertThat(remaining.getStatus()).isEqualTo(UserStatus.DELETED);
		assertThat(remaining.getDisplayName()).isEqualTo("탈퇴한 사용자");
	}

	@Test
	@DisplayName("🔴 확인 값이 다르면 400 이고 아무것도 지워지지 않는다")
	void wrongConfirmationDeletesNothing() {
		assertThatThrownBy(() -> this.accountDeletionService.delete(this.userId, "delete", PASSWORD))
				.isInstanceOf(AuthException.class)
				.extracting(exception -> ((AuthException) exception).getCode())
				.isEqualTo("DELETION_NOT_CONFIRMED");

		assertThat(this.credentialRepository.findByUserUserId(this.userId)).isPresent();
		assertThat(tripExists(this.tripId)).isTrue();
		assertThat(this.userRepository.findById(this.userId).orElseThrow().getStatus())
				.isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	@DisplayName("🔴 비밀번호는 선택이지만 보냈으면 맞아야 한다 — 비밀번호 없는 계정에 보내면 거절한다")
	void passwordSentToAnAccountThatHasNoneIsRejected() {
		UUID socialUserId = createSocialOnlyUser("kakao-" + UUID.randomUUID());

		assertThatThrownBy(() -> this.accountDeletionService.delete(socialUserId, CONFIRM, "anything"))
				.isInstanceOf(AuthException.class)
				.extracting(exception -> ((AuthException) exception).getCode())
				.isEqualTo("PASSWORD_NOT_SET");

		assertThat(countByUser("auth_identity", socialUserId)).isEqualTo(1);
		assertThat(this.userRepository.findById(socialUserId).orElseThrow().getStatus())
				.isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	@DisplayName("비밀번호로 가입한 계정도 확인 값만으로 탈퇴된다 — 두 종류가 같은 흐름을 탄다")
	void passwordAccountCanAlsoBeDeletedWithConfirmationAlone() {
		this.accountDeletionService.delete(this.userId, CONFIRM, null);

		assertThat(this.credentialRepository.findByUserUserId(this.userId)).isEmpty();
		assertThat(this.userRepository.findById(this.userId).orElseThrow().getStatus())
				.isEqualTo(UserStatus.DELETED);
	}

	// ── 도구 ──────────────────────────────────────────────────────────────────

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

	/**
	 * 소셜로만 가입한 계정 — S15P21E201-837.
	 *
	 * <p>🔴 {@code local_credential} 을 만들지 않는다. 그것이 이 테스트의 전부다 — 실제 소셜 가입
	 * 경로({@code OAuthAccountService}) 도 자격증명을 만들지 않는다.
	 */
	private UUID createSocialOnlyUser(String providerSubject) {
		UUID socialUserId = this.transactionTemplate.execute(status -> this.userRepository
				.save(AppUser.register("소셜 여행자", "KO", Instant.now(), "2026-01",
						PersonalizationMode.EXPLICIT_ONLY, UserStatus.ACTIVE))
				.getUserId());
		this.jdbcTemplate.update("""
				INSERT INTO auth_identity (identity_id, user_id, provider, provider_subject, provider_email, linked_at)
				VALUES (?, ?, 'APPLE', ?, NULL, now())
				""", UUID.randomUUID(), socialUserId, providerSubject);
		this.socialUserIds.add(socialUserId);
		return socialUserId;
	}

	@Test
	@DisplayName("🔴 취향 벡터가 있는 사람도 탈퇴된다 — 벡터가 설문 스냅샷을 가리키고 있어도")
	void deletesAccountEvenWhenATasteVectorPointsAtThePreferenceSnapshot() {
		// 배치(TasteVectorFoldService)가 한 번이라도 접은 사람을 그대로 재현한다.
		// 계정 기본 설문 한 판 → 그 판을 가리키는 취향 벡터 한 개.
		UUID snapshotId = UUID.randomUUID();
		this.jdbcTemplate.update("""
				INSERT INTO preference_snapshot
				  (preference_snapshot_id, user_id, trip_id, version, scope, survey_version, created_at)
				VALUES (?, ?, NULL, 1, 'USER', 'test-survey-v1', now())
				""", snapshotId, this.userId);
		this.jdbcTemplate.update("""
				INSERT INTO user_taste_vector
				  (taste_vector_id, user_id, version, source_preference_snapshot_id,
				   observed_event_count, vector_version, ontology_version, created_at)
				VALUES (?, ?, 1, ?, 0, 'test-v1', 'test-onto-v1', now())
				""", UUID.randomUUID(), this.userId, snapshotId);

		// 🔴 고치기 전에는 여기서 통째로 실패했다. deleteTripData 가 preference_snapshot 을
		//    먼저 지우는데 user_taste_vector 가 아직 그것을 가리키고 있었고,
		//    fk_user_taste_vector_preference_snapshot 은 ON DELETE 가 없어 NO ACTION 이다.
		//    트랜잭션이 하나라 500 만 나가고 아무것도 안 지워진다 — 배치가 매일 다시 접으므로
		//    다시 눌러도 성공하는 날이 없다. App Store 5.1.1(v) 가 요구하는 바로 그 기능이다.
		this.accountDeletionService.delete(this.userId, CONFIRM, PASSWORD);

		assertThat(this.userRepository.findById(this.userId))
			.get()
			.extracting(AppUser::getStatus)
			.isEqualTo(UserStatus.DELETED);

		Integer vectors = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM user_taste_vector WHERE user_id = ?", Integer.class, this.userId);
		assertThat(vectors).as("취향 벡터도 함께 지워진다").isZero();

		Integer snapshots = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM preference_snapshot WHERE user_id = ?", Integer.class, this.userId);
		assertThat(snapshots).as("설문 스냅샷도 함께 지워진다").isZero();
	}

	private UUID createTrip(UUID owner) {
		UUID trip = UUID.randomUUID();
		this.jdbcTemplate.update("""
				INSERT INTO trip (trip_id, owner_user_id, owner_type, start_date, end_date, created_at, updated_at)
				VALUES (?, ?, 'USER', ?, ?, now(), now())
				""", trip, owner, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12));
		return trip;
	}

	private UUID createStory(UUID author) {
		UUID storyId = UUID.randomUUID();
		this.jdbcTemplate.update("""
				INSERT INTO story (story_id, author_user_id, body, visibility, publish_at, created_at, updated_at)
				VALUES (?, ?, ?, 'PUBLIC', now(), now(), now())
				""", storyId, author, "테스트 기록");
		return storyId;
	}

	private Instant storyDeletedAt(UUID storyId) {
		return this.jdbcTemplate.queryForObject(
				"SELECT deleted_at FROM story WHERE story_id = ?", Instant.class, storyId);
	}

	private boolean tripExists(UUID trip) {
		Integer count = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM trip WHERE trip_id = ?", Integer.class, trip);
		return count != null && count > 0;
	}

	private Integer countByUser(String table, UUID user) {
		return this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, user);
	}
}
