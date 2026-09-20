package com.gabolle.backend.user.repository;

import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	/**
	 * 지금 이 권한을 가진 계정 전부. 기동 시점 동기화가 쓴다 — 목록에서 빠진 사람을 내리려면
	 * 지금 누가 ADMIN 인지를 알아야 하고, 그건 설정만 보고는 알 수 없다.
	 */
	List<AppUser> findAllByRole(UserRole role);

	/**
	 * 이 사람의 개인화 방식만. 부르는 자리가 이벤트 수집이라 계정 전체를 읽지 않는다 —
	 * 행동 이벤트는 화면 한 번에 여러 건이 들어온다.
	 *
	 * <p>계정이 없어 값이 비면 부르는 쪽은 수집하지 않는 쪽으로 판단한다.
	 */
	@Query("SELECT u.personalizationMode FROM AppUser u WHERE u.userId = :userId")
	Optional<PersonalizationMode> findPersonalizationMode(@Param("userId") UUID userId);
}
