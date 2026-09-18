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
 * 동의 가드를 필요로 하지만 <b>동의를 재지는 않는</b> 검사들이 쓰는 조각 — S15P21E201-549.
 *
 * <h2>🔴 왜 있는가</h2>
 *
 * {@code TripCreationService} 가 민감 제약 저장 전에 동의를 보게 되면서, 여행 생성을 직접
 * 만들어 쓰는 검사 일곱 개가 인자를 하나 더 받아야 했다. 그 검사들이 재는 것은 날짜 규칙·
 * 멱등·응답 모양이지 동의가 아니다 — 각자 Mockito 로 저장소를 흉내 내게 두면 <b>같은 준비가
 * 일곱 벌</b>이 되고, 동의 판정이 바뀌는 날 일곱 곳을 고쳐야 한다.
 *
 * <p>🔴 <b>기본을 "허용" 으로 두는 것이 이 조각의 위험이기도 하다.</b> 동의를 실제로 재는
 * 검사는 이것을 쓰면 안 된다 — 무엇을 넣어도 통과하므로 초록이 아무것도 증명하지 않는다.
 * 그쪽은 {@code ConsentGuardTest} 와 {@code VisitVerificationIntegrationTest} 처럼
 * 진짜 결정을 넣고 잰다.
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
	 * <p>🔴 {@link ConsentGuard} 는 {@code getIfAvailable()} 하나만 부른다. 흉내 낼 것이
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
