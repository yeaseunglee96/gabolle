package com.gabolle.backend.user.repository;

import com.gabolle.backend.user.domain.ConsentType;
import com.gabolle.backend.user.domain.UserConsent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {

	List<UserConsent> findAllByUserUserId(UUID userId);

	/**
	 * 그 사람의 그 항목, 그 정책 판의 결정 한 줄.
	 *
	 * <p>🔴 {@code policy_version} 까지 함께 찾는다. 표의 유일 제약이 그 셋이라
	 * 이 조합이 곧 "고쳐 쓸 그 행" 이다. 판을 빼고 찾으면 옛 판의 결정을 새 판의 것으로
	 * 덮어써서, 어느 약관에 동의했는지가 사라진다.
	 */
	Optional<UserConsent> findByUserUserIdAndConsentTypeAndPolicyVersion(UUID userId, ConsentType consentType,
			String policyVersion);
}
