package com.gabolle.backend.dish.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.dish.config.DishProperties;
import com.gabolle.backend.dish.domain.DishImageUsage;
import com.gabolle.backend.dish.repository.DishImageUsageRepository;

/**
 * 그림을 얼마나 자주 <b>새로</b> 만들 수 있나 — S15P21E201-1272.
 *
 * <h2>🔴 메뉴판 읽기 한도와 표를 나눈 이유</h2>
 *
 * 한 표에 섞으면 그림 몇 장이 그날의 <b>메뉴판 읽기까지 막는다.</b> 읽기는 알레르기
 * 낱말을 보는 자리다. 그림은 있으면 좋은 것이고 읽기는 사람이 다칠 수 있는 자리라,
 * 둘이 같은 지갑을 쓰면 안 된다.
 *
 * <h2>이미 만들어 둔 그림은 안 센다</h2>
 *
 * 세는 것은 <b>바깥을 실제로 부를 때</b>뿐이다. 저장해 둔 그림을 꺼내 주는 것은 값이
 * 안 나가므로 세면 안 된다 — 세면 저장해 두고 다시 쓰는 뜻이 사라진다.
 *
 * <h2>세는 순서는 메뉴판 읽기와 같다</h2>
 *
 * <b>먼저 적고 그다음 센다.</b> 「확인하고 나중에 센다」로 두면 연타가 그 사이로 들어온다.
 * 넘었으면 예외를 던져 트랜잭션을 통째로 되돌린다 — 방금 적은 행도 같이 사라진다.
 */
@Component
@Profile({ "db", "dev" })
public class DishImageRateLimiter {

	private static final int ONE_MINUTE_SECONDS = 60;

	private static final int ONE_DAY_SECONDS = 24 * 60 * 60;

	private final DishProperties properties;

	private final DishImageUsageRepository usage;

	private final Clock clock;

	public DishImageRateLimiter(DishProperties properties, DishImageUsageRepository usage, Clock clock) {
		this.properties = properties;
		this.usage = usage;
		this.clock = clock;
	}

	/**
	 * 그림 하나를 새로 만드는 것으로 세고, 넘었으면 거절한다.
	 *
	 * @throws TooManyDishImagesException 하루 또는 분 한도를 넘었다
	 */
	@Transactional
	public void takeOrThrow(UUID userId) {
		OffsetDateTime now = OffsetDateTime.now(this.clock);

		this.usage.deleteExpiredFor(userId, now.minusSeconds(ONE_DAY_SECONDS));
		this.usage.save(DishImageUsage.of(UUID.randomUUID(), userId, now));

		long lastMinute = this.usage.countByUserIdAndRequestedAtAfter(userId,
				now.minusSeconds(ONE_MINUTE_SECONDS));
		if (lastMinute > this.properties.getImagePerMinuteLimit()) {
			throw new TooManyDishImagesException("그림은 조금 뒤에 다시 만들 수 있어요.");
		}

		long today = this.usage.countByUserIdAndRequestedAtAfter(userId, now.minusSeconds(ONE_DAY_SECONDS));
		if (today > this.properties.getImageDailyLimit()) {
			throw new TooManyDishImagesException("오늘 만들 수 있는 그림을 다 썼어요.");
		}
	}

	/**
	 * 그림 한도를 넘었다.
	 *
	 * <p>🔴 이것은 <b>메뉴판 읽기가 막힌 것이 아니다.</b> 화면은 이 실패를 그림 자리에만
	 * 그려야 한다 — 글자와 알레르기 낱말은 이미 왔고 그쪽은 멀쩡하다.
	 */
	public static class TooManyDishImagesException extends RuntimeException {

		public TooManyDishImagesException(String message) {
			super(message);
		}
	}
}
