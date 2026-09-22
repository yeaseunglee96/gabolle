package com.gabolle.backend.menuscan.application;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.menuscan.repository.MenuScanUsageRepository;

/**
 * 하루 지난 메뉴판 호출 기록을 쓸어 간다.
 *
 * <p>{@link MenuScanRateLimiter} 가 부를 때마다 그 사람 것을 치우지만, 한 번 쓰고 안 돌아온 사람의
 * 행은 아무도 안 건드린다. 이 청소가 그 절반을 맡는다.
 *
 * <p>정리가 늦어도 한도 판정은 안 틀린다 — 세는 질의가 창을 시각으로 자른다. 이 청소가 지키는 것은
 * 표 크기뿐이다.
 */
@Component
@Profile({ "db", "dev" })
public class MenuScanUsageSweeper {

	private static final Logger log = LoggerFactory.getLogger(MenuScanUsageSweeper.class);

	private static final int ONE_DAY_SECONDS = 24 * 60 * 60;

	private final MenuScanUsageRepository usage;

	private final Clock clock;

	public MenuScanUsageSweeper(MenuScanUsageRepository usage, Clock clock) {
		this.usage = usage;
		this.clock = clock;
	}

	@Scheduled(cron = "${gabolle.menu-scan.usage-cleanup.cron:0 20 18 * * *}", zone = "UTC")
	public void runScheduled() {
		sweep();
	}

	/**
	 * 지난 기록을 지우고 몇 행을 지웠는지 돌려준다. 시험과 수동 실행이 cron 을 거치지 않고 바로
	 * 부를 수 있게 따로 열어 둔다.
	 */
	@Transactional
	public int sweep() {
		OffsetDateTime before = OffsetDateTime.now(this.clock).minusSeconds(ONE_DAY_SECONDS);
		int removed = this.usage.deleteExpired(before);
		if (removed > 0) {
			log.info("메뉴판 호출 기록 {}행을 지웠다 (기준 {} 이전)", removed, before);
		}
		return removed;
	}
}
