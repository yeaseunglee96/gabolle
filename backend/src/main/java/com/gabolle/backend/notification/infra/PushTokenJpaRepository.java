package com.gabolle.backend.notification.infra;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** 기기 푸시 토큰 창구 — S15P21E201-1391. */
public interface PushTokenJpaRepository extends JpaRepository<PushTokenJpaEntity, UUID> {

	Optional<PushTokenJpaEntity> findByToken(String token);

	/** 이 사람의 기기 전부. */
	List<PushTokenJpaEntity> findAllByUserId(UUID userId);

	/**
	 * 이 사람들의 기기 전부 — 알림을 보낼 때 쓴다 (S15P21E201-1391 2/2).
	 *
	 * <p>사람마다 따로 묻지 않는다. 동행이 여섯이면 질의도 여섯이 되고, 그게 «여행 하나 바꿀
	 * 때마다» 돈다.
	 */
	List<PushTokenJpaEntity> findAllByUserIdIn(Collection<UUID> userIds);

	/**
	 * 지우는 것은 «내 것» 만이다.
	 *
	 * @return 지운 줄 수. 0 이면 남의 토큰이었거나 이미 없었다 — 둘을 구분해 답하지 않는다
	 */
	long deleteByTokenAndUserId(String token, UUID userId);

	/**
	 * Expo 가 «그 기기는 이제 없다» 고 한 것들을 지운다 (S15P21E201-1391 2/2).
	 *
	 * <p>🔴 여기에는 주인 조건이 없다. 위와 달리 <b>사용자가 시킨 일이 아니라 Expo 가 알려 준
	 * 사실</b>이라서다 — 앱을 지운 기기에 주인이 누구인지는 상관이 없다. 대신 부르는 쪽은
	 * {@code DeviceNotRegistered} 로 돌아온 토큰만 여기에 넣어야 한다.
	 */
	long deleteByTokenIn(Collection<String> tokens);
}
