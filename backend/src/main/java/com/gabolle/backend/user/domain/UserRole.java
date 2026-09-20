package com.gabolle.backend.user.domain;

/**
 * 관리자 화면이 요구하는 최소 권한 구분.
 *
 * <p>가입 경로와 무관하게 모든 계정이 갖는 값이다. 부여는 운영자가 DB 에서 직접 한다 —
 * API 로 스스로를 ADMIN 으로 올리는 경로는 없다.
 */
public enum UserRole {
	USER,
	ADMIN
}
