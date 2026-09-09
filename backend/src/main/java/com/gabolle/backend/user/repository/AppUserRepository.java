package com.gabolle.backend.user.repository;

import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.UserRole;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	/**
	 * 지금 이 권한을 가진 계정 전부 — S15P21E201-225 의 기동 시점 동기화가 쓴다.
	 *
	 * <p>ADMIN 은 배포 설정에 적은 몇 명이라 전부 읽어도 된다. 목록에서 빠진 사람을 내리려면
	 * "지금 누가 ADMIN 인가" 를 알아야 하고, 그건 설정만 보고는 알 수 없다.
	 */
	List<AppUser> findAllByRole(UserRole role);
}
