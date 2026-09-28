package com.gabolle.backend.batch.application;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 뒤처진 사람의 취향 판을 하루 한 번 접는다 — Airflow 없이 (S15P21E201-1516).
 *
 * <p>Airflow DAG {@code taste_vector_daily} 가 하던 일을 그대로 옮겼다. 그 DAG 는
 * {@code /stale} 로 뒤처진 사람을 찾고 {@code /rebuild} 로 접는 것이 전부였고, 두 주소 뒤의 일은
 * 이미 {@link TasteVectorBatchService} 에 있다. 여기는 HTTP 를 거치지 않고 그것을 바로 부른다.
 *
 * <p>DAG 와 같은 규칙을 지킨다.
 * <ul>
 * <li>{@code asOf} 를 <b>한 번</b> 정해 두 단계에 똑같이 넘긴다 — 찾을 때와 접을 때 「지금」이
 * 다르면, 그 사이에 도착한 것을 찾지는 않고 접기만 한다</li>
 * <li>상한에 걸려 잘려도 실패가 아니다. 남은 사람은 내일 다시 걸린다</li>
 * <li>한 사람의 실패가 나머지를 멈추지 않는다. 실패한 사람도 내일 다시 걸린다</li>
 * </ul>
 *
 * <p>{@code TasteDecayScheduler} 와 같은 모양이다 — 켜짐 여부를 <b>메서드 안에서</b> 보고,
 * 예외는 여기서 삼킨다.
 */
@Component
@Profile({ "db", "dev" })
public class TasteVectorDailyFoldScheduler {

	private static final Logger log = LoggerFactory.getLogger(TasteVectorDailyFoldScheduler.class);

	private final TasteVectorBatchService batchService;

	private final TasteVectorDailyFoldProperties properties;

	private final Clock clock;

	public TasteVectorDailyFoldScheduler(TasteVectorBatchService batchService,
			TasteVectorDailyFoldProperties properties, Clock clock) {
		this.batchService = batchService;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * 🔴 인라인 기본값({@code :0 0 19 * * *})을 <b>반드시</b> 남긴다.
	 * {@code application.properties} 의 {@code gabolle.taste-vector.daily-fold.cron} 에만 기대면
	 * 그 줄이 지워지는 순간 자리표시자가 안 풀려 <b>애플리케이션 전체가 기동을 못 한다</b>
	 * ({@code TasteDecayScheduler} 와 같은 이유, 2026-09-08 에 실제로 당했다).
	 *
	 * <p>{@code zone = "UTC"} 다. 19:00 UTC 가 한국 04:00 이다.
	 */
	@Scheduled(cron = "${gabolle.taste-vector.daily-fold.cron:0 0 19 * * *}", zone = "UTC")
	public void runScheduled() {
		if (!this.properties.isEnabled()) {
			log.debug("event=TASTE_VECTOR_DAILY_FOLD_SKIPPED reason=DISABLED");
			return;
		}
		try {
			runOnce();
		}
		catch (RuntimeException failure) {
			// 다시 던지지 않는다. 스케줄러가 로그 한 줄로 삼키고 다음 회차를 도므로, 뜻을 담아
			// 여기서 한 번 남기는 편이 되짚기 쉽다.
			log.error("event=TASTE_VECTOR_DAILY_FOLD_FAILED", failure);
		}
	}

	/** 한 번 돈다. 스위치를 안 본다 — 스위치는 {@link #runScheduled} 가 본다. */
	public void runOnce() {
		OffsetDateTime asOf = OffsetDateTime.now(this.clock);
		TasteVectorBatchService.StalePage page = this.batchService.staleUsers(asOf, this.properties.getLimit());
		if (page.userIds().isEmpty()) {
			// 빈 목록으로 접지 않는다 — 뒤처진 사람이 없는 날은 정상이다.
			log.info("event=TASTE_VECTOR_DAILY_FOLD asOf={} stale=0", asOf);
			return;
		}

		TasteVectorBatchReport report = this.batchService.rebuild(page.userIds(), asOf);
		log.info("event=TASTE_VECTOR_DAILY_FOLD asOf={} stale={} truncated={} processed={} rebuilt={} "
				+ "watermarkAdvanced={} unchanged={} nothingToFold={} failed={}",
				asOf, page.userIds().size(), page.truncated(), report.processed(),
				report.count(TasteVectorFoldOutcome.Action.REBUILT),
				report.count(TasteVectorFoldOutcome.Action.WATERMARK_ADVANCED),
				report.count(TasteVectorFoldOutcome.Action.UNCHANGED),
				report.count(TasteVectorFoldOutcome.Action.NOTHING_TO_FOLD), report.failed());
		if (report.failed() > 0) {
			log.warn("event=TASTE_VECTOR_DAILY_FOLD_PARTIAL failed={} users={} — 내일 다시 걸린다",
					report.failed(), report.failedUserIds());
		}
	}

}
