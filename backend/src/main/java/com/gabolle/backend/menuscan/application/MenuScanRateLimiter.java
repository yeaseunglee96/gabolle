package com.gabolle.backend.menuscan.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.menuscan.config.MenuScanProperties;
import com.gabolle.backend.menuscan.domain.MenuScanUsage;
import com.gabolle.backend.menuscan.repository.MenuScanUsageRepository;

/**
 * 메뉴판 읽기를 얼마나 자주 부를 수 있나.
 *
 * <p>집계를 서버 메모리가 아니라 표에 둔다. 메모리에 두면 재시작할 때마다 0 이 되고 서버를 여러
 * 대로 늘리면 대수만큼 한도가 늘어난다. 이것이 지키는 것은 돈이 나가는 GMS 키다.
 *
 * <p>순서는 먼저 적고 그다음 센다. 확인부터 하면 연타가 그 사이로 들어온다. 넘었으면 예외로
 * 트랜잭션을 통째로 되돌려 방금 적은 행도 같이 지운다.
 *
 * <p>동시에 두 요청이 들어오면 둘 다 거절될 수 있다. 일부러 그쪽으로 기울였다 — 넘치는 쪽으로
 * 틀리면 돈이 나가고, 모자라는 쪽으로 틀리면 사용자가 잠시 뒤 다시 누른다.
 */
@Component
@Profile({ "db", "dev" })
public class MenuScanRateLimiter {

	private static final int ONE_MINUTE_SECONDS = 60;

	private static final int ONE_DAY_SECONDS = 24 * 60 * 60;

	private final MenuScanProperties properties;

	private final MenuScanUsageRepository usage;

	private final Clock clock;

	public MenuScanRateLimiter(MenuScanProperties properties, MenuScanUsageRepository usage, Clock clock) {
		this.properties = properties;
		this.usage = usage;
		this.clock = clock;
	}

	/**
	 * 한 번 쓴 것으로 세고, 넘었으면 거절한다.
	 *
	 * @throws TooManyScansException 하루 또는 분 한도를 넘었다
	 */
	@Transactional
	public void takeOrThrow(UUID userId) {
		OffsetDateTime now = OffsetDateTime.now(this.clock);

		// 이 사람의 지난 기록부터 치운다 — 세는 창 밖이라 어차피 안 쓰이고, 돌아오는
		// 사용자의 행이 쌓이지 않게 하는 것도 여기서 끝난다.
		this.usage.deleteExpiredFor(userId, now.minusSeconds(ONE_DAY_SECONDS));

		this.usage.save(MenuScanUsage.of(UUID.randomUUID(), userId, now));

		long lastMinute = this.usage.countByUserIdAndScannedAtAfter(userId, now.minusSeconds(ONE_MINUTE_SECONDS));
		if (lastMinute > this.properties.getPerMinuteLimit()) {
			throw new TooManyScansException("잠시 뒤에 다시 시도해 주세요.");
		}

		long today = this.usage.countByUserIdAndScannedAtAfter(userId, now.minusSeconds(ONE_DAY_SECONDS));
		if (today > this.properties.getDailyLimit()) {
			throw new TooManyScansException("오늘 사용할 수 있는 횟수를 다 썼어요.");
		}
	}

	/**
	 * 한도를 넘었다. 조용히 빈 결과를 주지 않는다 — 이 API 에서 빈 결과는 «알레르기 낱말이
	 * 없구나»로 읽힌다. 화면까지 429 로 간다.
	 */
	public static class TooManyScansException extends RuntimeException {

		public TooManyScansException(String message) {
			super(message);
		}
	}
}
