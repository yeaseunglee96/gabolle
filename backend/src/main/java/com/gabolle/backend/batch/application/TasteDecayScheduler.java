package com.gabolle.backend.batch.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 감쇠를 하루 한 번 부른다 (S15P21E201-1501).
 *
 * <p>{@code OutboxRelayScheduler} 와 같은 모양이다 — 켜짐 여부를 <b>메서드 안에서</b> 보고,
 * 예외는 여기서 삼킨다.
 */
@Component
@Profile({ "db", "dev" })
public class TasteDecayScheduler {

	private static final Logger log = LoggerFactory.getLogger(TasteDecayScheduler.class);

	private final TasteDecayService decayService;

	private final TasteDecayProperties properties;

	public TasteDecayScheduler(TasteDecayService decayService, TasteDecayProperties properties) {
		this.decayService = decayService;
		this.properties = properties;
	}

	/**
	 * 🔴 인라인 기본값({@code :0 30 4 * * *})을 <b>반드시</b> 남긴다.
	 * {@code application.properties} 의 {@code gabolle.taste.decay.cron} 에만 기대면 그 줄이
	 * 지워지는 순간 자리표시자가 안 풀려 {@code BeanCreationException} 으로 <b>애플리케이션
	 * 전체가 기동을 못 한다.</b> {@link TasteDecayProperties} 의 자바 기본값은
	 * {@code @ConfigurationProperties} 바인딩에만 쓰이고 여기 해석에는 관여하지 않는다.
	 * ({@code OutboxRelayScheduler}·{@code PrivacyCleanupScheduler} 가 같은 이유로 같은 주석을
	 * 달고 있고, 2026-09-08 에 실제로 당한 적이 있다.)
	 *
	 * <p>이 값은 {@link TasteDecayProperties} 의 자바 기본값과 같게 유지한다.
	 */
	@Scheduled(cron = "${gabolle.taste.decay.cron:0 30 4 * * *}", zone = "UTC")
	public void runScheduled() {
		if (!this.properties.isEnabled()) {
			log.debug("event=TASTE_DECAY_SKIPPED reason=DISABLED");
			return;
		}
		try {
			this.decayService.decayOnce();
		}
		catch (RuntimeException failure) {
			// 여기서 다시 던지지 않는다. 스케줄러가 예외를 로그 한 줄로 삼키고 다음 회차를
			// 그대로 도므로, 우리가 뜻을 담아 한 번 남기는 편이 되짚기 쉽다.
			log.error("event=TASTE_DECAY_FAILED", failure);
		}
	}

}
