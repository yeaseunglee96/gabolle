package com.gabolle.backend.auth.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.auth.domain.OperatorAccountDeletionLog;

/** 운영자 대리 탈퇴 처리 기록 (S15P21E201-1647). */
public interface OperatorAccountDeletionLogRepository extends JpaRepository<OperatorAccountDeletionLog, Long> {

	/** 이 이메일(의 해시)로 처리한 기록, 오래된 순. 「이 주소는 처리했나」에 답한다. */
	List<OperatorAccountDeletionLog> findByEmailSha256OrderByLogIdAsc(String emailSha256);

}
