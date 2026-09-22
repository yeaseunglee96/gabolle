package com.gabolle.backend.event.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.gabolle.backend.event.config.OutboxRelayProperties;

/**
 * 아웃박스 릴레이를 주기적으로 돌린다 (S15P21E201-561).
 *
 * <p>🔴 <b>이 클래스가 없어서 릴레이가 한 번도 안 돌았다.</b> {@code OutboxRelayService.relayOnce()}
 * 는 만들어져 있었지만 {@code main} 어디에서도 부르는 곳이 없었고(테스트만 불렀다), 그래서
 * 브로커 어댑터를 꽂아도 아무것도 안 나갔을 것이다. "어댑터만 갈아 끼우면 된다" 가 사실이
 * 아니었던 자리다.
 *
 * <p>실행을 {@link #runScheduled()} 와 {@link OutboxRelayService#relayOnce()} 로 나눠 둔 것은
 * {@code PrivacyCleanupScheduler} 와 같은 이유다 — 운영 콘솔이나 테스트에서 {@code enabled}
 * 플래그와 cron 트리거를 거치지 않고 한 번 돌릴 수 있어야 한다.
 *
 * <p><b>아는 한계 — 인스턴스가 둘 이상이면 같은 행을 두 번 보낸다.</b> 지금 조회에는 행 잠금
 * ({@code SELECT … FOR UPDATE SKIP LOCKED})이 없다. 받는 쪽이 헤더 {@code event_id} 로 중복을
 * 거르므로 자료가 틀어지지는 않지만, 브로커에 같은 것이 두 번 실린다. 백엔드를 두 대 이상으로
 * 늘릴 때 이 줄을 먼저 고친다 — 늘린 뒤에 알면 이미 중복이 쌓인 뒤다.
 */
@Component
@Profile({ "db", "dev" })
public class OutboxRelayScheduler {

	private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

	private final OutboxRelayService relayService;

	private final OutboxRelayProperties properties;

	public OutboxRelayScheduler(OutboxRelayService relayService, OutboxRelayProperties properties) {
		this.relayService = relayService;
		this.properties = properties;
	}

	// 🔴 인라인 기본값(:0 * * * * *)을 반드시 남긴다. application.properties 의
	//    gabolle.event.outbox-relay.cron 에만 기대면 그 줄이 지워지는 순간 자리표시자가 안 풀려
	//    BeanCreationException 으로 애플리케이션 전체가 기동을 못 한다. OutboxRelayProperties 의
	//    자바 기본값은 @ConfigurationProperties 바인딩에만 쓰이고 여기 해석에는 관여하지 않는다.
	//    (2026-09-08 개인정보 정리 배치가 똑같이 당했다 — PrivacyCleanupScheduler 주석 참고.)
	//    이 값은 OutboxRelayProperties 의 자바 기본값과 같게 유지한다.
	@Scheduled(cron = "${gabolle.event.outbox-relay.cron:0 * * * * *}", zone = "UTC")
	public void runScheduled() {
		if (!this.properties.isEnabled()) {
			log.debug("event=OUTBOX_RELAY_SKIPPED reason=DISABLED");
			return;
		}
		try {
			int sent = this.relayService.relayOnce();
			if (sent > 0) {
				log.info("event=OUTBOX_RELAY_SENT count={}", sent);
			}
		}
		catch (RuntimeException failure) {
			// 여기서 다시 던지지 않는다. 스케줄러 프레임워크는 예외를 로그 한 줄로 삼키고,
			// 다시 던져 봐야 다음 차례가 더 잘 도는 것도 아니다. 건별 실패는 이미
			// publishOne 이 last_error 에 적는다 — 여기 오는 것은 조회 자체가 깨진 경우다.
			log.error("event=OUTBOX_RELAY_FAILED reason={}", failure.getClass().getSimpleName(), failure);
		}
	}
}
