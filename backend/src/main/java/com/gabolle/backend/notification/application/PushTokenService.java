package com.gabolle.backend.notification.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

	/**
	 * 이 사람들에게 알림을 보낼 기기 목록 — S15P21E201-1391 (2/2).
	 *
	 * <p>알림을 켠 사람이 하나도 없으면 빈 목록이다. 흔한 일이고 고장이 아니다.
	 *
	 * <p>{@code REQUIRES_NEW} 인 이유는 {@link #forget} 과 같다 — {@code AFTER_COMMIT} 에서
	 * 불린다. 읽기라 잘못돼도 값이 사라지지는 않지만, 끝난 트랜잭션에 얹혀 읽는 것은
	 * 「돌긴 도는데 왜 되는지 아무도 설명 못 하는」 자리가 된다. 둘을 같은 모양으로 둔다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public List<String> tokensOf(Collection<String> userIds) {
		if (userIds == null || userIds.isEmpty()) {
			return List.of();
		}
		List<UUID> ids = userIds.stream().filter((id) -> id != null && !id.isBlank())
				.distinct().map(UUID::fromString).toList();
		if (ids.isEmpty()) {
			return List.of();
		}
		return this.repository.findAllByUserIdIn(ids).stream().map(PushTokenJpaEntity::token).toList();
	}

	/**
	 * 이제 없는 기기를 표에서 지운다 — S15P21E201-1391 (2/2).
	 *
	 * <p>🔴 <b>여기에는 주인 확인이 없다.</b> {@link #unregister} 와 정반대인데, 이 값의 출처가
	 * 사용자가 아니라 Expo 의 {@code DeviceNotRegistered} 대답이기 때문이다 — 앱을 지운 기기에
	 * 주인이 누구였는지는 상관이 없다. 그래서 <b>부르는 쪽이 그 대답으로 온 토큰만</b> 넣어야 한다.
	 * 다른 실패(예: 잠깐 몰려서 거절)로 부르면 멀쩡한 사람이 알림을 영영 못 받는다.
	 *
	 * <p>🔴 <b>{@code REQUIRES_NEW} 여야 한다 — S15P21E201-1484.</b> 이것을 부르는 곳은
	 * {@link TripPushNotifier} 의 {@code AFTER_COMMIT} 리스너다. 거기서는 바깥 트랜잭션이
	 * <b>이미 커밋을 끝냈지만 아직 정리되지 않은 상태</b>로 매달려 있어서, 기본 전파
	 * ({@code REQUIRED} — 「도는 것이 있으면 합류한다」)로 들어가면 그 끝난 트랜잭션에
	 * 합류한다. DELETE 는 실행되고 예외도 안 나는데 <b>다시 커밋될 일이 없어서 행이 그대로
	 * 남는다.</b> 조용히 아무 일도 안 일어나므로 스스로는 드러나지 않는다 — 죽은 기기로
	 * 헛발송이 영영 계속된다.
	 *
	 * <p>이 저장소가 이미 아는 함정이다. {@code LoginAttemptGuard} 와
	 * {@code PlaceFacetViewWriter} 가 정확히 같은 이유로 같은 전파를 쓴다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void forget(Collection<String> tokens) {
		if (tokens == null || tokens.isEmpty()) {
			return;
		}
		this.repository.deleteByTokenIn(tokens.stream().distinct().toList());
	}

	private static String require(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " 이(가) 비어 있습니다.");
		}
		return value.trim();
	}
}
