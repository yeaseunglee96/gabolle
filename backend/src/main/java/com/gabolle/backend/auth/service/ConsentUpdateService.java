package com.gabolle.backend.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.auth.api.UserConsentsResponse;
import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.user.application.BehaviorPersonalizationReset;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.ConsentStatus;
import com.gabolle.backend.user.domain.ConsentType;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserConsent;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;
import com.gabolle.backend.user.repository.UserConsentRepository;

/**
 * 가입한 뒤에 동의를 읽고 바꾸는 자리.
 *
 * <p>행동 개인화는 서버에서 두 군데에 있다 — 판정에 실제로 쓰는 현재 값
 * ({@code app_user.personalization_mode})과 "언제 무엇에 동의했나" 의 기록
 * ({@code user_consent}). 하나만 바뀌면 개인화는 켜져 있는데 동의 기록은 없는(또는 그 반대)
 * 상태가 되고, 어느 쪽도 오류로 나타나지 않는다. 그래서 바꾸는 입구를 이 클래스 하나로 두고
 * 한 트랜잭션에서 둘을 고친다.
 *
 * <p>필수 약관은 이 API 로 철회되지 않는다. 끄는 것은 토글이 아니라 탈퇴이고, 허용하면
 * "서비스는 계속 쓰는데 약관에는 동의 안 한 계정" 이 생긴다.
 *
 * <p>가입 검사기({@link ConsentPolicy})와 나눠 둔 것은 그쪽이 "필수 항목이 전부 참인가" 를
 * 보는 반면 여기는 부분 수정이라 보내지 않은 항목을 건드리면 안 되기 때문이다.
 */
@Service
@Profile({ "db", "dev" })
public class ConsentUpdateService {

	/** 이 둘은 끄는 것이 탈퇴다. 토글로 다루지 않는다. */
	private static final Set<ConsentType> NOT_REVOCABLE = EnumSet.of(
			ConsentType.TERMS_OF_SERVICE, ConsentType.PRIVACY_POLICY);

	private final AppUserRepository userRepository;

	private final UserConsentRepository consentRepository;

	private final AuthProperties properties;

	private final BehaviorPersonalizationReset behaviorReset;

	private final Clock clock;

	public ConsentUpdateService(AppUserRepository userRepository, UserConsentRepository consentRepository,
			AuthProperties properties, BehaviorPersonalizationReset behaviorReset, Clock clock) {
		this.userRepository = userRepository;
		this.consentRepository = consentRepository;
		this.properties = properties;
		this.behaviorReset = behaviorReset;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public UserConsentsResponse get(UUID userId) {
		return describe(activeUser(userId));
	}

	/**
	 * 보낸 항목만 바꾼다. 안 보낸 항목은 그대로 둔다.
	 *
	 * <p>먼저 전부 해석하고 그 다음에 쓴다. 하나씩 해석하면서 바로 저장하면 목록 뒤쪽에 모르는
	 * 이름이 하나 있을 때 앞쪽은 이미 바뀐 채로 400 이 나간다.
	 */
	@Transactional
	public UserConsentsResponse update(UUID userId, Map<String, Boolean> rawConsents) {
		Map<ConsentType, Boolean> decisions = parse(rawConsents);

		AppUser user = activeUser(userId);
		Instant now = Instant.now(this.clock);
		String policyVersion = this.properties.getConsentPolicyVersion();

		decisions.forEach((type, granted) -> {
			ConsentStatus status = granted ? ConsentStatus.GRANTED : ConsentStatus.REVOKED;
			this.consentRepository.findByUserUserIdAndConsentTypeAndPolicyVersion(userId, type, policyVersion)
					.ifPresentOrElse(
							existing -> existing.redecide(status, now),
							() -> this.consentRepository.save(
									UserConsent.decide(user, type, status, policyVersion)));
		});

		Boolean behavior = decisions.get(ConsentType.BEHAVIOR_PERSONALIZATION);
		if (behavior != null) {
			user.changePersonalizationMode(behavior
					? PersonalizationMode.BEHAVIOR_ENABLED : PersonalizationMode.EXPLICIT_ONLY);

			// 끄면 이미 만들어 둔 취향 벡터·피드·행동 이벤트를 지운다. 안 지우면 껐다고 눌러도
			// 추천이 어제 프로필로 나오고 배치가 그 프로필을 다시 키운다. 같은 트랜잭션이어야
			// 한다 — 갈라 두면 "껐다고 나오는데 벡터는 남아 있는" 상태가 오류 없이 생긴다.
			// 켜는 쪽에서는 아무것도 복구하지 않는다. 다음 배치가 그 이후의 행동으로 새로 접는다.
			if (!behavior) {
				this.behaviorReset.forget(userId);
			}
		}

		return describe(user);
	}

	private Map<ConsentType, Boolean> parse(Map<String, Boolean> rawConsents) {
		Map<ConsentType, Boolean> decisions = new EnumMap<>(ConsentType.class);
		if (rawConsents == null || rawConsents.isEmpty()) {
			throw new AuthException("EMPTY_CONSENT_UPDATE", "바꿀 동의 항목이 필요합니다.", HttpStatus.BAD_REQUEST);
		}
		rawConsents.forEach((name, granted) -> {
			ConsentType type = toConsentType(name);
			boolean value = Boolean.TRUE.equals(granted);
			if (!value && NOT_REVOCABLE.contains(type)) {
				throw new AuthException("REQUIRED_CONSENT_NOT_REVOCABLE",
						"필수 약관 동의는 이 화면에서 철회할 수 없습니다. 탈퇴를 이용해 주세요.", HttpStatus.BAD_REQUEST);
			}
			decisions.put(type, value);
		});
		return decisions;
	}

	private static ConsentType toConsentType(String name) {
		if (name == null || name.isBlank()) {
			throw new AuthException("INVALID_CONSENT_TYPE", "동의 항목 이름이 필요합니다.", HttpStatus.BAD_REQUEST);
		}
		try {
			return ConsentType.valueOf(name.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException exception) {
			throw new AuthException("INVALID_CONSENT_TYPE", "알 수 없는 동의 항목입니다: " + name,
					HttpStatus.BAD_REQUEST);
		}
	}

	private AppUser activeUser(UUID userId) {
		return this.userRepository.findById(userId)
				.filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
				.orElseThrow(() -> new AuthException("ACCOUNT_UNAVAILABLE", "사용할 수 없는 계정입니다.",
						HttpStatus.UNAUTHORIZED));
	}

	/**
	 * 지금 상태를 그대로 옮긴다. 옛 정책 판의 결정도 함께 내보낸다 — 최신 판만 보여주면
	 * "그때 그 약관에는 동의했다" 는 사실이 응답에서 사라진다.
	 */
	private UserConsentsResponse describe(AppUser user) {
		List<UserConsentsResponse.Item> items = new ArrayList<>();
		for (UserConsent consent : this.consentRepository.findAllByUserUserId(user.getUserId())) {
			items.add(new UserConsentsResponse.Item(consent.getConsentType().name(), consent.getStatus().name(),
					consent.getPolicyVersion(), consent.getDecidedAt()));
		}
		items.sort(Comparator.comparing(UserConsentsResponse.Item::consentType)
				.thenComparing(UserConsentsResponse.Item::policyVersion));

		return new UserConsentsResponse(
				user.getPersonalizationMode() == PersonalizationMode.BEHAVIOR_ENABLED,
				List.copyOf(items));
	}
}
