package com.gabolle.backend.auth;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.gabolle.backend.auth.api.UserConsentsResponse;
import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.auth.service.ConsentUpdateService;
import com.gabolle.backend.auth.support.AuthPostgresIntegrationTest;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.ConsentStatus;
import com.gabolle.backend.user.domain.ConsentType;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserConsent;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;
import com.gabolle.backend.user.repository.UserConsentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 가입한 뒤에 동의를 바꾼다 — S15P21E201-735.
 *
 * <h2>🔴 왜 진짜 DB 가 필요한가</h2>
 * 이 기능의 위험 둘이 실제 DB 에서만 드러난다.
 *
 * <p>하나는 <b>유일 제약</b>이다. {@code user_consent} 는 {@code (user_id, consent_type,
 * policy_version)} 이 유일해서, 같은 판에서 결정을 바꿀 때 행을 새로 넣으면 저장 시점에
 * 터진다. 가짜 저장소로는 그 제약이 없어서 초록이 나오고, 운영에서 처음 빨개진다.
 *
 * <p>다른 하나는 <b>두 자리가 같이 바뀌는가</b>다. 행동 개인화는 {@code app_user
 * .personalization_mode}(판정에 쓰는 값)와 {@code user_consent}(기록) 두 군데에 있고,
 * 하나만 바뀌면 "개인화는 켜져 있는데 동의는 없는" 상태가 된다. 어느 쪽도 오류로 나타나지
 * 않으므로 표를 직접 읽어 확인한다.
 */
class ConsentUpdateIntegrationTest extends AuthPostgresIntegrationTest {

	@Autowired
	private ConsentUpdateService consentUpdateService;

	@Autowired
	private AppUserRepository userRepository;

	@Autowired
	private UserConsentRepository consentRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private UUID userId;

	@BeforeEach
	void setUp() {
		// 가입 직후 상태를 그대로 만든다 — 행동 개인화는 꺼져 있고 필수 약관만 동의돼 있다.
		this.userId = this.transactionTemplate.execute(status -> {
			AppUser user = this.userRepository.save(AppUser.register("여행자", "KO", Instant.now(), "2026-01",
					PersonalizationMode.EXPLICIT_ONLY, UserStatus.ACTIVE));
			this.consentRepository.save(UserConsent.decide(user, ConsentType.TERMS_OF_SERVICE,
					ConsentStatus.GRANTED, "2026-01"));
			this.consentRepository.save(UserConsent.decide(user, ConsentType.PRIVACY_POLICY,
					ConsentStatus.GRANTED, "2026-01"));
			return user.getUserId();
		});
	}

	@Test
	@DisplayName("🔴 가입 뒤에 켠 동의가 서버에 남는다 — 지금까지는 그 기기 안에만 있었다")
	void turningBehaviourPersonalizationOnReachesTheServer() {
		UserConsentsResponse before = this.consentUpdateService.get(this.userId);
		assertThat(before.behaviorPersonalizationEnabled()).isFalse();

		UserConsentsResponse after = this.transactionTemplate.execute(status -> this.consentUpdateService
				.update(this.userId, Map.of("BEHAVIOR_PERSONALIZATION", true)));

		assertThat(after.behaviorPersonalizationEnabled()).isTrue();
		assertThat(after.consents())
				.anySatisfy(item -> assertThat(item.consentType()).isEqualTo("BEHAVIOR_PERSONALIZATION"));

		// 🔴 판정에 쓰는 값과 기록이 <b>둘 다</b> 바뀌어야 한다. 하나만 바뀌면
		//    "개인화는 켜져 있는데 동의는 없다" 가 되고, 그건 코드가 아니라 방침을 어긴 것이다.
		assertThat(personalizationMode()).isEqualTo("BEHAVIOR_ENABLED");
		assertThat(consentStatus(ConsentType.BEHAVIOR_PERSONALIZATION)).isEqualTo("GRANTED");
	}

	@Test
	@DisplayName("🔴 껐다 켜도 같은 정책 판의 행은 하나다 — 유일 제약에 걸려 터지면 안 된다")
	void togglingTwiceKeepsASingleRowPerPolicyVersion() {
		this.transactionTemplate.executeWithoutResult(status -> this.consentUpdateService
				.update(this.userId, Map.of("BEHAVIOR_PERSONALIZATION", true)));
		this.transactionTemplate.executeWithoutResult(status -> this.consentUpdateService
				.update(this.userId, Map.of("BEHAVIOR_PERSONALIZATION", false)));

		Integer rows = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM user_consent WHERE user_id = ? AND consent_type = ? AND policy_version = ?",
				Integer.class, this.userId, "BEHAVIOR_PERSONALIZATION", "2026-01");

		assertThat(rows).isEqualTo(1);
		assertThat(consentStatus(ConsentType.BEHAVIOR_PERSONALIZATION)).isEqualTo("REVOKED");
		assertThat(personalizationMode()).isEqualTo("EXPLICIT_ONLY");
	}

	@Test
	@DisplayName("보내지 않은 항목은 안 바뀐다 — 앱 화면에 없는 동의가 조용히 철회되면 안 된다")
	void untouchedConsentsAreLeftAlone() {
		this.transactionTemplate.executeWithoutResult(status -> this.consentUpdateService
				.update(this.userId, Map.of("PRECISE_LOCATION", true)));

		assertThat(consentStatus(ConsentType.TERMS_OF_SERVICE)).isEqualTo("GRANTED");
		assertThat(consentStatus(ConsentType.PRIVACY_POLICY)).isEqualTo("GRANTED");
		assertThat(consentStatus(ConsentType.PRECISE_LOCATION)).isEqualTo("GRANTED");
	}

	@Test
	@DisplayName("🔴 필수 약관은 이 경로로 철회되지 않는다 — 끄는 것은 탈퇴다")
	void requiredConsentsCannotBeRevokedHere() {
		assertThatThrownBy(() -> this.transactionTemplate.executeWithoutResult(status -> this.consentUpdateService
				.update(this.userId, Map.of("TERMS_OF_SERVICE", false))))
				.isInstanceOf(AuthException.class)
				.hasMessageContaining("탈퇴");

		assertThat(consentStatus(ConsentType.TERMS_OF_SERVICE)).isEqualTo("GRANTED");
	}

	@Test
	@DisplayName("🔴 모르는 항목이 하나 섞이면 앞쪽도 안 바뀐다 — 절반만 바뀐 상태를 안 만든다")
	void anUnknownConsentTypeRejectsTheWholeRequest() {
		java.util.Map<String, Boolean> mixed = new java.util.LinkedHashMap<>();
		mixed.put("BEHAVIOR_PERSONALIZATION", true);
		mixed.put("MIND_READING", true);

		assertThatThrownBy(() -> this.transactionTemplate
				.executeWithoutResult(status -> this.consentUpdateService.update(this.userId, mixed)))
				.isInstanceOf(AuthException.class);

		assertThat(personalizationMode()).isEqualTo("EXPLICIT_ONLY");
		Integer rows = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM user_consent WHERE user_id = ? AND consent_type = ?",
				Integer.class, this.userId, "BEHAVIOR_PERSONALIZATION");
		assertThat(rows).isZero();
	}

	@Test
	@DisplayName("없는 계정으로 부르면 401 이다 — 남의 동의를 지정할 자리가 애초에 없다")
	void unknownAccountsAreRejected() {
		assertThatThrownBy(() -> this.consentUpdateService.get(UUID.randomUUID()))
				.isInstanceOf(AuthException.class)
				.hasMessageContaining("사용할 수 없는 계정");
	}

	// ── 2026-09-11 (S15P21E201-549) — 끄면 이미 만들어 둔 것도 사라지는가 ─────────

	/**
	 * 🔴 이 검사가 없을 때 무엇이 통과했나.
	 *
	 * <p>스위치와 동의 기록만 바뀌고 <b>취향 벡터·미리 만든 피드·행동 이벤트는 그대로</b>
	 * 남았다. 위쪽 검사들은 전부 초록이었다 — 그것들이 재는 것이 스위치와 기록뿐이기
	 * 때문이다. 껐다고 눌러도 추천은 어제 프로필로 나오고, 배치가 backfill 하면 그 프로필이
	 * 다시 자란다.
	 *
	 * <p>그래서 여기서는 <b>표를 직접 읽어</b> 없어졌는지 본다. 서비스가 무엇을 불렀는지가
	 * 아니라 행이 남았는지가 사용자에게 일어나는 일이다.
	 */
	@Test
	@DisplayName("🔴 행동 개인화를 끄면 취향 벡터·미리 만든 피드·행동 이벤트가 함께 지워진다")
	void turningBehaviourPersonalizationOffErasesWhatWasDerivedFromBehaviour() {
		UUID tasteVectorId = UUID.randomUUID();
		UUID buildId = UUID.randomUUID();
		UUID behaviorEventId = UUID.randomUUID();
		UUID explicitEventId = UUID.randomUUID();
		seedDerivedData(tasteVectorId, buildId, behaviorEventId, explicitEventId);

		this.transactionTemplate.executeWithoutResult(status -> this.consentUpdateService
				.update(this.userId, Map.of("BEHAVIOR_PERSONALIZATION", false)));

		assertThat(personalizationMode()).isEqualTo("EXPLICIT_ONLY");
		assertThat(consentStatus(ConsentType.BEHAVIOR_PERSONALIZATION)).isEqualTo("REVOKED");

		assertThat(count("SELECT count(*) FROM user_taste_vector WHERE user_id = ?", this.userId)).isZero();
		assertThat(count("SELECT count(*) FROM user_taste_weight WHERE taste_vector_id = ?", tasteVectorId)).isZero();
		assertThat(count("SELECT count(*) FROM user_feed WHERE build_id = ?", buildId)).isZero();
		assertThat(count("SELECT count(*) FROM feed_build WHERE user_id = ?", this.userId)).isZero();

		// 🔴 행동 관찰은 지우고, 사람이 직접 넣은 것은 남긴다. 껐다는 것이
		//    "내가 고른 것도 잊으라" 는 뜻은 아니다.
		assertThat(count("SELECT count(*) FROM event_outbox WHERE event_id = ?", behaviorEventId)).isZero();
		assertThat(count("SELECT count(*) FROM event_outbox WHERE event_id = ?", explicitEventId)).isOne();
	}

	@Test
	@DisplayName("다시 켜도 지운 것은 안 돌아온다 — 껐던 기간의 행동은 영영 안 쓴다")
	void turningItBackOnDoesNotRestoreAnything() {
		UUID tasteVectorId = UUID.randomUUID();
		seedDerivedData(tasteVectorId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

		this.transactionTemplate.executeWithoutResult(status -> this.consentUpdateService
				.update(this.userId, Map.of("BEHAVIOR_PERSONALIZATION", false)));
		this.transactionTemplate.executeWithoutResult(status -> this.consentUpdateService
				.update(this.userId, Map.of("BEHAVIOR_PERSONALIZATION", true)));

		assertThat(personalizationMode()).isEqualTo("BEHAVIOR_ENABLED");
		assertThat(count("SELECT count(*) FROM user_taste_vector WHERE user_id = ?", this.userId)).isZero();
	}

	/** 개인화가 이미 한 바퀴 돈 사람의 상태 — 벡터 · 성분 · 피드 세대 · 줄 · 이벤트 둘. */
	private void seedDerivedData(UUID tasteVectorId, UUID buildId, UUID behaviorEventId, UUID explicitEventId) {
		this.jdbcTemplate.update("""
				INSERT INTO user_taste_vector (taste_vector_id, user_id, version, observed_event_count,
				                               observed_until, vector_version, ontology_version, created_at)
				VALUES (?, ?, 1, 3, now(), 'v1', 'onto-1', now())
				""", tasteVectorId, this.userId);
		this.jdbcTemplate.update("""
				INSERT INTO user_taste_weight (taste_vector_id, dimension, code, weight, evidence, support, updated_at)
				VALUES (?, 'ATMOSPHERE', 'QUIET', 1.0, 'SURVEY', 0, now())
				""", tasteVectorId);

		this.jdbcTemplate.update("""
				INSERT INTO feed_build (build_id, user_id, surface, status, taste_vector_id, entry_count, created_at)
				VALUES (?, ?, 'HOME', 'BUILDING', ?, 1, now())
				""", buildId, this.userId, tasteVectorId);
		this.jdbcTemplate.update("""
				INSERT INTO user_feed (build_id, position, item_type, item_id, reason_codes, payload, created_at)
				VALUES (?, 0, 'PLACE', ?, '{PERSONALIZED}', '{}'::jsonb, now())
				""", buildId, UUID.randomUUID());

		insertEvent(behaviorEventId, "place_view");
		insertEvent(explicitEventId, "trip_created");
	}

	private void insertEvent(UUID eventId, String eventType) {
		this.jdbcTemplate.update("""
				INSERT INTO event_outbox (event_id, event_type, event_version, aggregate_type, aggregate_id,
				                          partition_key, payload, occurred_at, received_at, user_id)
				VALUES (?, ?, 1, 'user', ?, ?, '{}'::jsonb, now(), now(), ?)
				""", eventId, eventType, this.userId, this.userId.toString(), this.userId);
	}

	private int count(String sql, UUID argument) {
		Integer rows = this.jdbcTemplate.queryForObject(sql, Integer.class, argument);
		return (rows == null) ? 0 : rows;
	}

	private String personalizationMode() {
		return this.jdbcTemplate.queryForObject(
				"SELECT personalization_mode FROM app_user WHERE user_id = ?", String.class, this.userId);
	}

	private String consentStatus(ConsentType type) {
		return this.jdbcTemplate.queryForObject(
				"SELECT status FROM user_consent WHERE user_id = ? AND consent_type = ?",
				String.class, this.userId, type.name());
	}
}
