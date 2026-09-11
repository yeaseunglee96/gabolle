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
 * 가입한 뒤에 동의를 읽고 바꾸는 자리 — S15P21E201-735.
 *
 * <h2>🔴 왜 필요했나</h2>
 * 동의를 서버에 남기는 경로가 <b>가입 요청 하나뿐</b>이었다({@code POST /auth/signup} ·
 * {@code /auth/oauth/signup} 의 {@code behaviorPersonalizationEnabled}). 그런데 앱은
 * 마이페이지와 첫 체크인 화면에 "행동으로 추천 다듬기" 토글을 뒀다. 즉 <b>가입 뒤에 켠
 * 동의는 그 기기 안에만 남고 서버에는 영영 안 갔다.</b>
 *
 * <p>이건 불편이 아니라 방침 위반 쪽에 가깝다. 처리방침은 "행동 기반 개인화는 별도 동의를
 * 받은 경우에만 처리합니다" 라고 선언해 뒀는데, 그 동의를 <b>받을 수도 철회할 수도</b>
 * 없었다. 껐다고 생각한 사람의 행동이 계속 개인화에 들어가는 상태였다.
 *
 * <h2>🔴 두 자리를 같은 트랜잭션에서 고친다</h2>
 * 행동 개인화는 서버에서 두 군데에 있다.
 * <ul>
 * <li>{@code app_user.personalization_mode} — 판정에 실제로 쓰는 <b>현재 값</b></li>
 * <li>{@code user_consent} — "언제 무엇에 동의했나" 의 <b>기록</b></li>
 * </ul>
 * 하나만 바뀌면 <b>개인화는 켜져 있는데 동의 기록은 없는</b>(또는 그 반대) 상태가 된다.
 * 어느 쪽도 오류로 나타나지 않고, 나중에 "동의받았는가" 를 물었을 때 답이 갈린다.
 * 그래서 바꾸는 입구를 이 클래스 하나로 두고 한 트랜잭션에서 둘을 고친다.
 *
 * <h2>🔴 필수 약관은 이 API 로 철회되지 않는다</h2>
 * 이용약관·개인정보 처리방침에 대한 동의를 끄는 것은 <b>토글이 아니라 탈퇴</b>다. 여기서
 * 허용하면 "서비스는 계속 쓰는데 약관에는 동의 안 한 계정" 이 생기고, 그 계정으로 무엇을
 * 해도 되는지 아무 데도 안 적혀 있다. 그래서 거부하고, 탈퇴 경로가 따로 있다는 사실을
 * 오류 메시지로 알린다({@code DELETE /api/v1/auth/me}).
 *
 * <h2>가입 검사기({@link ConsentPolicy})와 나누어 둔 이유</h2>
 * 그쪽은 <b>가입 한 번</b>의 검사라 "필수 항목이 전부 참인가" 를 본다. 여기는 부분 수정이라
 * 보내지 않은 항목을 건드리면 안 된다. 같은 클래스에 두 규칙을 넣으면 어느 쪽이 도는지가
 * 호출자에 따라 달라지고, 그 차이는 테스트에서 잘 안 보인다.
 */
@Service
@Profile({ "db", "dev" })
public class ConsentUpdateService {

	/** 🔴 이 둘은 끄는 것이 탈퇴다. 토글로 다루지 않는다. */
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
	 * <p>🔴 <b>먼저 전부 해석하고 그 다음에 쓴다.</b> 항목을 하나씩 해석하면서 바로 저장하면,
	 * 목록 뒤쪽에 모르는 이름이 하나 있을 때 <b>앞쪽은 이미 바뀐 채로</b> 400 이 나간다.
	 * 앱은 실패로 알고 화면을 되돌리는데 서버 값은 절반만 바뀌어 있다.
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

			// 🔴 끄면 이미 만들어 둔 것을 지운다 — S15P21E201-549.
			//
			//    이 줄이 없을 때 무슨 일이 있었나: 스위치와 동의 기록만 바뀌고 취향 벡터·미리
			//    만든 피드·행동 이벤트는 그대로 남았다. 껐다고 눌러도 추천은 어제 프로필로
			//    나오고, 배치가 backfill 하면 그 프로필이 다시 자란다. 사용자가 보기에 스위치가
			//    거짓말을 한다.
			//
			//    🔴 같은 트랜잭션이다. 갈라 두면 "껐다고 나오는데 벡터는 남아 있는" 상태가
			//       생기고, 그 상태는 아무 오류도 안 내면서 방침 위반이다.
			//
			//    켜는 쪽에서는 아무것도 복구하지 않는다. 지운 것은 지운 것이고 다음 배치가
			//    그 이후의 행동으로 새로 접는다.
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
	 * 지금 상태를 그대로 옮긴다.
	 *
	 * <p>🔴 옛 정책 판의 결정도 <b>안 지우고 그대로 내보낸다.</b> 판이 바뀌면 새 판의 결정이
	 * 따로 생기고 옛 결정은 "그때 그 약관에는 동의했다" 는 사실로 남는다. 최신 판만 보여주면
	 * 그 사실이 응답에서 사라진다.
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
