package com.gabolle.backend.auth.service;

import com.gabolle.backend.user.domain.ConsentType;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 회원가입 방식과 관계없이 동일하게 적용하는 앱 약관 정책이다.
 */
@Component
@Profile({"db", "dev"})
public class ConsentPolicy {

	private static final Set<ConsentType> REQUIRED_CONSENTS = EnumSet.of(
			ConsentType.TERMS_OF_SERVICE, ConsentType.PRIVACY_POLICY);

	public Map<ConsentType, Boolean> validate(Map<String, Boolean> rawConsents,
			boolean behaviorPersonalizationEnabled) {
		Map<ConsentType, Boolean> consents = new EnumMap<>(ConsentType.class);
		if (rawConsents != null) {
			rawConsents.forEach((type, granted) -> {
				if (type == null || type.isBlank()) {
					throw new AuthException("INVALID_CONSENT_TYPE", "동의 항목 이름이 필요합니다.",
							org.springframework.http.HttpStatus.BAD_REQUEST);
				}
				try {
					consents.put(ConsentType.valueOf(type.trim().toUpperCase(Locale.ROOT)),
							Boolean.TRUE.equals(granted));
				} catch (IllegalArgumentException exception) {
					throw new AuthException("INVALID_CONSENT_TYPE", "알 수 없는 동의 항목입니다: " + type,
							org.springframework.http.HttpStatus.BAD_REQUEST);
				}
			});
		}
		if (REQUIRED_CONSENTS.stream().anyMatch(required -> !Boolean.TRUE.equals(consents.get(required)))) {
			throw new AuthException("REQUIRED_CONSENT_MISSING", "필수 약관 동의가 필요합니다.",
					org.springframework.http.HttpStatus.BAD_REQUEST);
		}
		if (behaviorPersonalizationEnabled
				&& !Boolean.TRUE.equals(consents.get(ConsentType.BEHAVIOR_PERSONALIZATION))) {
			throw new AuthException("PERSONALIZATION_CONSENT_REQUIRED", "행동 기반 개인화 동의가 필요합니다.",
					org.springframework.http.HttpStatus.BAD_REQUEST);
		}
		return consents;
	}
}
