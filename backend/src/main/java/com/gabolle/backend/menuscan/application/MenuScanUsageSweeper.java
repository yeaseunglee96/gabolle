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
 * 하루 지난 메뉴판 호출 기록을 쓸어 간다 — S15P21E201-1038.
 *
 * <h2>🔴 왜 부를 때 지우는 것만으로는 부족한가</h2>
 *
 * {@link MenuScanRateLimiter} 는 부를 때마다 <b>그 사람 것</b>을 치운다. 그것으로 돌아오는
 * 사용자의 행은 계속 묶인다. 하지만 <b>한 번 쓰고 안 돌아온</b> 사람의 행은 아무도 안
 * 건드린다 — 그 사람이 다시 안 부르니까. 메모리 맵 시절에 자리가 계속 쌓인 것이 정확히
 * 이 절반이 없어서였고, 표로 옮기면서 같은 구멍을 그대로 옮기지 않는다.
 *
 * <p>정리가 늦어도 한도 판정은 안 틀린다 — 세는 질의가 창을 시각으로 자르므로, 지난 행이
 * 남아 있어도 집계에는 안 들어간다. 이 청소가 지키는 것은 <b>표 크기</b>뿐이다.
 *
 * <p>주기는 {@code PrivacyCleanupScheduler} 와 같은 방식으로 설정에서 읽되 기본값을 인라인에
 * 둔다. 트래픽이 가장 적은 시각에 돈다.
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
	 * 지난 기록을 지우고 몇 행을 지웠는지 돌려준다.
	 *
	 * <p>🔴 시험과 수동 실행이 cron 을 거치지 않고 바로 부를 수 있게 따로 열어 둔다 —
	 * {@code PrivacyCleanupScheduler} 가 같은 이유로 같은 모양이다.
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
