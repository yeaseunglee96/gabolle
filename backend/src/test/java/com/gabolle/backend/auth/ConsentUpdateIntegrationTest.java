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
