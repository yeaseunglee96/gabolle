package com.gabolle.backend.user.support;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;

import com.gabolle.backend.user.application.ConsentGuard;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.ConsentStatus;
import com.gabolle.backend.user.domain.ConsentType;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserConsent;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.UserConsentRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 동의 가드를 필요로 하지만 동의를 재지는 않는 검사들이 쓰는 조각. 같은 준비가 여러 벌이
 * 되지 않게 한 곳에 둔다.
 *
 * <p>기본이 허용이므로 동의를 실제로 재는 검사는 이것을 쓰면 안 된다 — 무엇을 넣어도
 * 통과해 초록이 아무것도 증명하지 않는다.
 */
public final class ConsentGuards {

	private ConsentGuards() {
	}

	/** 무엇을 물어도 동의했다고 답하는 가드. 동의가 관심사가 아닌 검사에서만 쓴다. */
	public static ConsentGuard granting() {
		UserConsentRepository consents = mock(UserConsentRepository.class);
		given(consents.findFirstByUserUserIdAndConsentTypeOrderByDecidedAtDesc(any(), any()))
				.willAnswer(invocation -> Optional.of(grantOf(invocation.getArgument(1))));
		return new ConsentGuard(providerOf(consents));
	}

	/** 무엇을 물어도 동의하지 않았다고 답하는 가드. */
	public static ConsentGuard refusing() {
		UserConsentRepository consents = mock(UserConsentRepository.class);
		given(consents.findFirstByUserUserIdAndConsentTypeOrderByDecidedAtDesc(any(), any()))
				.willReturn(Optional.empty());
		return new ConsentGuard(providerOf(consents));
	}

	/**
	 * 스프링 없이 만드는 검사용 제공자.
	 *
	 * <p> {@link ConsentGuard} 는 {@code getIfAvailable()} 하나만 부른다. 흉내 낼 것이
	 * 그것뿐이라 인터페이스 전체를 구현하지 않고 그 메서드만 흉내 낸다.
	 */
	@SuppressWarnings("unchecked")
	public static ObjectProvider<UserConsentRepository> providerOf(UserConsentRepository repository) {
		ObjectProvider<UserConsentRepository> provider = mock(ObjectProvider.class);
		given(provider.getIfAvailable()).willReturn(repository);
		return provider;
	}

	private static UserConsent grantOf(ConsentType type) {
		AppUser user = AppUser.register("검사용", "KO", null, "2026-01", PersonalizationMode.EXPLICIT_ONLY,
				UserStatus.ACTIVE);
		return UserConsent.decide(user, type, ConsentStatus.GRANTED, "2026-01");
	}

	/** 검사 안에서 사용자 ID 가 필요할 때. 값 자체에는 뜻이 없다. */
	public static UUID anyUserId() {
		return UUID.randomUUID();
	}
}
