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
	 * 지금 이 권한을 가진 계정 전부 — S15P21E201-225 의 기동 시점 동기화가 쓴다.
	 *
	 * <p>ADMIN 은 배포 설정에 적은 몇 명이라 전부 읽어도 된다. 목록에서 빠진 사람을 내리려면
	 * "지금 누가 ADMIN 인가" 를 알아야 하고, 그건 설정만 보고는 알 수 없다.
	 */
	List<AppUser> findAllByRole(UserRole role);

	/**
	 * 이 사람의 개인화 방식만 — S15P21E201-549.
	 *
	 * <p>🔴 계정 전체를 불러오지 않는 이유는 <b>부르는 자리가 이벤트 수집</b>이기 때문이다.
	 * 행동 이벤트는 화면 한 번에 여러 건이 들어오는 경로라, 여기서 엔티티를 통째로 읽으면
	 * 동의 확인 한 번이 이벤트마다 붙는다. 필요한 것은 칸 하나다.
	 *
	 * <p>🔴 값이 없으면(계정이 없으면) 부르는 쪽은 <b>수집하지 않는 쪽으로</b> 판단한다.
	 * 동의를 확인할 수 없는 상태에서 모으는 것보다 안 모으는 것이 맞다.
	 */
	@Query("SELECT u.personalizationMode FROM AppUser u WHERE u.userId = :userId")
	Optional<PersonalizationMode> findPersonalizationMode(@Param("userId") UUID userId);
}
