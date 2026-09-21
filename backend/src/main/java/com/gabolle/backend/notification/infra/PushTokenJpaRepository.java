package com.gabolle.backend.notification.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** 기기 푸시 토큰 창구 — S15P21E201-1391. */
public interface PushTokenJpaRepository extends JpaRepository<PushTokenJpaEntity, UUID> {

	Optional<PushTokenJpaEntity> findByToken(String token);

	/** 보낼 때 쓰는 유일한 질의 — 이 사람의 기기 전부. */
	List<PushTokenJpaEntity> findAllByUserId(UUID userId);

	/**
	 * 지우는 것은 «내 것» 만이다.
	 *
	 * @return 지운 줄 수. 0 이면 남의 토큰이었거나 이미 없었다 — 둘을 구분해 답하지 않는다
	 */
	long deleteByTokenAndUserId(String token, UUID userId);
}
