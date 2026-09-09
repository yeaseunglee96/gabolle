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
	 * 이 이메일로 지금 연결돼 있는 소셜 계정 — S15P21E201-225.
	 *
	 * <p>운영자 지정은 이메일로 하는데, 소셜 로그인만 쓰는 사람에게는 {@code local_credential}
	 * 행이 없다. 그 사람의 이메일은 여기에만 있다. 이 경로가 없으면 소셜로만 가입한 운영자는
	 * 설정으로 지정할 수 없고, 결국 DB 를 손으로 고치게 된다.
	 *
	 * <p>연결이 끊긴({@code unlinked_at} 이 있는) 행은 제외한다. 끊긴 연결은 더 이상 로그인
	 * 경로가 아니라서, 그것으로 권한을 주면 <b>지금 그 계정에 못 들어가는 사람</b>이 운영자가 된다.
	 */
	List<AuthIdentity> findAllByProviderEmailAndUnlinkedAtIsNull(String providerEmail);
}
