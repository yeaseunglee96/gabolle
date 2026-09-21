package com.gabolle.backend.notification.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.notification.infra.PushTokenJpaEntity;
import com.gabolle.backend.notification.infra.PushTokenJpaRepository;

/**
 * 기기 푸시 토큰을 저장하고 지운다 — S15P21E201-1391.
 *
 * <p>앱은 로그인할 때마다 올리고 로그아웃할 때 지운다
 * ({@code frontend/src/notifications/pushToken.ts}, 1429 로 이미 머지됨).
 */
@Service
@Profile({ "db", "dev" })
public class PushTokenService {

	/** 앱이 보내는 값 둘. 표의 CHECK 와 같은 어휘다 — 한쪽만 늘리면 저장할 때 터진다. */
	private static final Set<String> PLATFORMS = Set.of("ios", "android");

	/**
	 * 받아들일 토큰 최대 길이.
	 *
	 * <p>Expo 토큰은 {@code ExponentPushToken[...]} 꼴로 40자 안팎이다. 잰 값이 아니라 고른 값이고,
	 * 무한정 긴 문자열이 표에 들어오는 것을 막는 자리다 — 길이를 표에 박지는 않았다(형식이 바뀔 수 있다).
	 */
	private static final int MAX_TOKEN_LENGTH = 512;

	private final PushTokenJpaRepository repository;

	private final Clock clock;

	public PushTokenService(PushTokenJpaRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/**
	 * 이 기기를 이 사람의 것으로 둔다. 이미 있으면 주인과 갈래를 덮는다.
	 *
	 * <p>🔴 <b>덮는 것이 핵심이다.</b> 기기를 물려주거나 한 폰에서 계정을 바꿔 로그인하면 토큰은 그대로인데
	 * 주인만 바뀐다. 옛 줄을 남기면 앞사람 계정의 알림이 뒷사람 폰으로 간다.
	 *
	 * @throws IllegalArgumentException 토큰이 비었거나 너무 길거나, 갈래가 ios·android 가 아닐 때
	 */
	@Transactional
	public void register(UUID userId, String token, String platform) {
		String trimmedToken = require(token, "token");
		if (trimmedToken.length() > MAX_TOKEN_LENGTH) {
			throw new IllegalArgumentException(
					"token 이 너무 깁니다: " + trimmedToken.length() + "자 (최대 " + MAX_TOKEN_LENGTH + "자)");
		}
		String normalizedPlatform = require(platform, "platform").toLowerCase(Locale.ROOT);
		if (!PLATFORMS.contains(normalizedPlatform)) {
			throw new IllegalArgumentException("platform 은 ios · android 중 하나여야 합니다: " + platform);
		}

		Instant now = Instant.now(this.clock);
		Optional<PushTokenJpaEntity> existing = this.repository.findByToken(trimmedToken);
		if (existing.isPresent()) {
			existing.get().reassign(userId, normalizedPlatform, now);
			return;
		}
		this.repository.save(PushTokenJpaEntity.register(userId, trimmedToken, normalizedPlatform, now));
	}

	/**
	 * 이 기기를 이 사람에게서 뗀다.
	 *
	 * <p>🔴 <b>남의 토큰은 못 지운다.</b> 토큰은 요청 본문이 아니라 주소에 실려 오므로, 주인을 안 보면
	 * 아무나 남의 기기를 알림에서 떼어 낼 수 있다. 이미 없거나 남의 것이면 조용히 지나간다 —
	 * 「그 토큰이 있다」를 알려 주지 않기 위해서다.
	 */
	@Transactional
	public void unregister(UUID userId, String token) {
		String trimmedToken = require(token, "token");
		this.repository.deleteByTokenAndUserId(trimmedToken, userId);
	}

	private static String require(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " 이(가) 비어 있습니다.");
		}
		return value.trim();
	}
}
