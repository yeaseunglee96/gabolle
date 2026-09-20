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
	 * <p> {@code policy_version} 까지 함께 찾는다. 표의 유일 제약이 그 셋이라
	 * 이 조합이 곧 "고쳐 쓸 그 행" 이다. 판을 빼고 찾으면 옛 판의 결정을 새 판의 것으로
	 * 덮어써서, 어느 약관에 동의했는지가 사라진다.
	 */
	Optional<UserConsent> findByUserUserIdAndConsentTypeAndPolicyVersion(UUID userId, ConsentType consentType,
			String policyVersion);

	/**
	 * 그 항목에 대한 가장 최근 결정. 방침 판을 가리지 않는다.
	 *
	 * <p>위 메서드와 일부러 다르다. 위는 고쳐 쓸 행을 찾는 것이라 판까지 맞추고, 이것은
	 * 지금 동의한 상태인가를 묻는 것이라 판을 맞추면 안 된다 — 맞추면 방침이 새 판으로
	 * 올라가는 날 이미 동의한 사람 전원이 조용히 미동의가 된다.
	 *
	 * <p>정렬이 {@code decided_at} 내림차순이라 철회가 옛 동의를 이긴다.
	 */
	Optional<UserConsent> findFirstByUserUserIdAndConsentTypeOrderByDecidedAtDesc(UUID userId,
			ConsentType consentType);
}
