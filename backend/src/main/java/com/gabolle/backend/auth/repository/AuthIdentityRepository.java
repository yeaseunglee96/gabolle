package com.gabolle.backend.auth.repository;

import com.gabolle.backend.auth.domain.AuthIdentity;
import com.gabolle.backend.auth.domain.AuthProvider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, UUID> {

	Optional<AuthIdentity> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);

	List<AuthIdentity> findAllByUserUserId(UUID userId);

	/**
	 * 이 이메일로 지금 연결돼 있는 소셜 계정. 소셜 로그인만 쓰는 사람에게는
	 * {@code local_credential} 행이 없어 이메일이 여기에만 있다.
	 *
	 * <p>연결이 끊긴({@code unlinked_at} 이 있는) 행은 제외한다 — 끊긴 연결은 로그인 경로가
	 * 아니라서, 그것으로 판정하면 지금 그 계정에 못 들어가는 사람을 고르게 된다.
	 */
	List<AuthIdentity> findAllByProviderEmailAndUnlinkedAtIsNull(String providerEmail);
}
