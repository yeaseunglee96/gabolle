package com.gabolle.backend.menuscan.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.gabolle.backend.menuscan.config.MenuScanProperties;

/**
 * 한 사람이 얼마나 자주 부를 수 있나 — S15P21E201-1025.
 *
 * <h2>🔴 왜 필요한가 — 키가 팀 공용이다</h2>
 * 크레딧이 하나뿐이라 <b>한 사람이 다 태우면 전부 멈춘다.</b> 그리고 큰 사진을 반복해서
 * 보내는 것은 그 자체로 공격이 된다.
 *
 * <h2>🔴 왜 표에 안 남기나</h2>
 * 횟수를 DB 에 적으려면 «누가 언제 메뉴판을 찍었는가» 를 남겨야 한다. 개인정보 처리방침에
 * <b>「사진을 보관하지 않는다」</b> 고 적어 놓고 <b>그 행동의 기록</b>을 쌓는 것은 약속의
 * 가장자리를 갉는 일이다. 이 기능에 꼭 필요한 최소는 <b>「지금 이 사람이 최근에 몇 번
 * 불렀나」</b> 뿐이고, 그건 메모리에 두면 된다.
 *
 * <p>🔴 <b>대가를 정직하게 적는다.</b> 서버를 다시 띄우면 세던 것이 사라지고, 서버가 여러
 * 대가 되면 대마다 따로 센다. 즉 이 한도는 <b>새는 한도</b>다. 막으려는 것이 «악의적
 * 고갈» 이 아니라 «실수와 연타로 크레딧이 녹는 것» 이라 그 정도로 충분하다고 봤다.
 * 진짜 고갈 공격을 막아야 할 날이 오면 그때는 <b>표가 아니라 중계 앞단</b>에서 막는 것이 맞다.
 */
@Component
public class MenuScanRateLimiter {

	private final MenuScanProperties properties;

	private final Clock clock;

	/** 사용자마다 최근 호출 시각. 오래된 것은 볼 때마다 버린다. */
	private final Map<UUID, Deque<Instant>> recentCalls = new ConcurrentHashMap<>();

	public MenuScanRateLimiter(MenuScanProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * 한 번 쓴 것으로 세고, 넘었으면 거절한다.
	 *
	 * <p>🔴 <b>세는 것과 부르는 것 사이를 벌리지 않는다.</b> 「확인하고 나중에 센다」로
	 * 두면 연타가 그 사이로 들어온다.
	 *
	 * @throws TooManyScansException 하루 또는 분 한도를 넘었다
	 */
	public void takeOrThrow(UUID userId) {
		Instant now = this.clock.instant();
		Deque<Instant> calls = this.recentCalls.computeIfAbsent(userId, (key) -> new ArrayDeque<>());

		synchronized (calls) {
			calls.removeIf((at) -> at.isBefore(now.minusSeconds(24 * 60 * 60)));

			long lastMinute = calls.stream().filter((at) -> at.isAfter(now.minusSeconds(60))).count();
			if (lastMinute >= this.properties.getPerMinuteLimit()) {
				throw new TooManyScansException("잠시 뒤에 다시 시도해 주세요.");
			}
			if (calls.size() >= this.properties.getDailyLimit()) {
				throw new TooManyScansException("오늘 사용할 수 있는 횟수를 다 썼어요.");
			}

			calls.addLast(now);
		}
	}

	/**
	 * 한도를 넘었다.
	 *
	 * <p>🔴 <b>조용히 빈 결과를 주지 않는다.</b> 이 API 에서 빈 결과는 사용자에게
	 * 「알레르기 낱말이 없구나」로 읽힌다 — 한도 때문에 못 읽은 것을 그렇게 보이게 하면
	 * 사람이 다칠 수 있다.
	 */
	public static class TooManyScansException extends RuntimeException {

		public TooManyScansException(String message) {
			super(message);
		}
	}
}
