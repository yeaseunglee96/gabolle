package com.gabolle.backend.privacy.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.gabolle.backend.privacy.domain.PrivacyCleanupResult;
import com.gabolle.backend.privacy.domain.PrivacyCleanupRun;
import com.gabolle.backend.privacy.repository.PrivacyCleanupRunRepository;

/**
 * 개인정보 자동 정리 배치를 매일 돌린다 — S15P21E201-357 · -166.
 *
 * <h2>🔴 이 저장소 최초의 {@code @Scheduled} 다</h2>
 *
 * 지금까지 이 백엔드에는 정기 배치 기반이 전혀 없었다({@code @EnableScheduling} 이 어디에도
 * 없었다 — 구현 전 grep 으로 확인). 그래서 스케줄링 인프라 자체를 이 티켓이 새로 놓는다.
 * {@link com.gabolle.backend.GabolleBackendApplication} 에 {@code @EnableScheduling} 을
 * 붙인 것이 그 등록이다.
 *
 * <p>같은 이유로, {@code StorageCleanupService.retryPending()}(S15P21E201-226)도 정기적으로
 * 불려야 하는데 지금까지 아무도 부르지 않는 죽은 메서드였다 — 이 스케줄러가 그것까지 해결하지는
 * 않는다(이 티켓의 범위 밖). 다음에 그 티켓을 다루는 사람이 이 클래스를 스케줄링 기반의 예시로
 * 볼 수 있게 이 사실만 남겨 둔다.
 *
 * <h2>실행 기록과 실패 알림</h2>
 *
 * 실행마다 {@link PrivacyCleanupRun} 행을 하나 남긴다 — 성공/실패와 카테고리별 삭제 건수.
 *
 * <p>🔴 S15P21E201-1216 이 <b>조회·복사 낱개 치우기를 이 배치에 붙였다.</b> 새 스케줄러를 만들지
 * 않은 이유는 여기에 실행 기록 행과 실패 알림이 이미 있기 때문이다 — 배치를 하나 더 만들면 그
 * 둘을 또 만들어야 하고, 둘 중 하나만 실패했을 때 어느 기록을 봐야 하는지가 애매해진다.
 * 실패하면 그 사실을 조용히 넘기지 않는다: ERROR 로그와, {@code alertWebhookUrl} 이 설정돼
 * 있으면 MatterMost 알림까지 함께 보낸다.
 *
 * <p>🔴 {@link com.gabolle.backend.common.security.SecurityAlertNotifier} 를 그대로 재사용하지
 * 않은 이유 — 그 클래스는 {@code SecurityEvent} 종류별 <b>급증(짧은 시간의 반복)</b> 을 판정하는
 * 슬라이딩 윈도 로직이 핵심이다. 이 배치는 하루 한 번만 돌고, 실패하면 그 자체로 바로 알려야
 * 한다(급증까지 기다릴 이유가 없다). 그래서 창·cooldown 없이 실패 시 바로 보내는 훨씬 단순한
 * 전송만 이 클래스에 직접 둔다 — 웹훅이 없을 때 조용히 로그만 남기고, 전송이 실패해도 예외를
 * 삼키는 계약은 그대로 따른다(그 실패 때문에 배치의 실행 기록 저장까지 막히면 안 된다).
 */
@Component
@Profile({ "db", "dev" })
public class PrivacyCleanupScheduler {

	private static final Logger log = LoggerFactory.getLogger(PrivacyCleanupScheduler.class);

	private static final Duration WEBHOOK_CONNECT_TIMEOUT = Duration.ofSeconds(2);
	private static final Duration WEBHOOK_READ_TIMEOUT = Duration.ofSeconds(3);

	private final PrivacyCleanupService cleanupService;
	private final PrivacyCleanupRunRepository runRepository;
	private final PrivacyCleanupProperties properties;
	private final Clock clock;
	private final RestClient restClient;

	public PrivacyCleanupScheduler(PrivacyCleanupService cleanupService, PrivacyCleanupRunRepository runRepository,
			PrivacyCleanupProperties properties, Clock clock, RestClient.Builder restClientBuilder) {
		this.cleanupService = cleanupService;
		this.runRepository = runRepository;
		this.properties = properties;
		this.clock = clock;
		this.restClient = restClientBuilder.requestFactory(timeoutRequestFactory()).build();
	}

	private static ClientHttpRequestFactory timeoutRequestFactory() {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(WEBHOOK_CONNECT_TIMEOUT);
		requestFactory.setReadTimeout(WEBHOOK_READ_TIMEOUT);
		return requestFactory;
	}

	// 🔴 2026-09-08 사고(S15P21E201-357 배포 실패 · docs 참고) — 이 문자열 안에 인라인
	// 기본값(:0 0 19 * * *)을 반드시 둔다. application.properties 의
	// gabolle.privacy.cleanup.cron 이 그때그때 정의돼 있는 것에 기대면, 그 줄 하나가
	// 실수로 지워지는 순간 이 애노테이션의 ${...} 플레이스홀더가 안 풀려
	// BeanCreationException 으로 애플리케이션 전체가 기동을 못 한다(실제로 겪음 —
	// PrivacyCleanupProperties 의 자바 기본값은 @ConfigurationProperties 바인딩에만
	// 쓰이고, @Scheduled 의 플레이스홀더 해석에는 전혀 관여하지 않는다). 인라인
	// 기본값을 두면 프로퍼티 파일에 그 줄이 있든 없든 이 클래스 혼자서 안전하다 —
	// PrivacyCleanupProperties 의 자바 기본값과 반드시 같은 값으로 유지한다.
	@Scheduled(cron = "${gabolle.privacy.cleanup.cron:0 0 19 * * *}", zone = "UTC")
	public void runScheduled() {
		if (!this.properties.isEnabled()) {
			log.debug("event=PRIVACY_CLEANUP_SKIPPED reason=DISABLED");
			return;
		}
		run();
	}

	/**
	 * 실제 실행. {@link #runScheduled()} 에서 분리해 둔 것은 테스트와, 필요하면 운영 콘솔에서
	 * 수동으로 한 번 돌리는 경로가 {@code enabled} 플래그와 cron 트리거를 거치지 않고 바로
	 * 부를 수 있게 하기 위해서다.
	 *
	 * <h2>🔴 이 메서드 자체에는 {@code @Transactional} 을 붙이지 않는다</h2>
	 *
	 * {@link PrivacyCleanupService#cleanup()} 은 그 자체로 {@code @Transactional} 이다. 만약 이
	 * 메서드까지 트랜잭션을 걸면(REQUIRED 전파로 안쪽 호출이 같은 트랜잭션에 합류한다) cleanup()
	 * 이 던지는 예외가 그 공유 트랜잭션을 rollback-only 로 표시해 버리고, 아래 catch 에서 잡아도
	 * 이미 늦다 — 이 메서드가 예외 없이 정상 반환해도 스프링이 커밋 시점에
	 * {@code UnexpectedRollbackException} 을 던지면서 <b>RUNNING/FAILED 기록 저장 자체가 함께
	 * 롤백된다.</b> 실패를 기록하려던 행이 실패로 인해 사라지는 것이다.
	 *
	 * <p>대신 저장(RUNNING 시작 기록) · 정리(cleanup, 독립 트랜잭션) · 저장(최종 상태 갱신) 을
	 * 서로 다른 트랜잭션 셋으로 나눈다. {@code runRepository.save} 호출 각각이 자기 트랜잭션을
	 * 새로 연다({@code SimpleJpaRepository} 기본 동작). {@code cleanupRun} 은 각 저장 사이에는
	 * detached 상태이지만, 다음 {@code save} 가 그 상태를 그대로 다시 반영(merge)한다.
	 */
	public PrivacyCleanupRun run() {
		Instant startedAt = this.clock.instant();
		PrivacyCleanupRun cleanupRun = PrivacyCleanupRun.start(startedAt);
		this.runRepository.save(cleanupRun);

		try {
			PrivacyCleanupResult result = this.cleanupService.cleanup();
			cleanupRun.succeed(this.clock.instant(), result);
			// 🔴 S15P21E201-1216 — 로그에 카테고리를 하나 더할 때 이 줄만 고치고 succeed() 를
			// 잊는(또는 그 반대의) 일이 없게, 건수를 낱낱이 넘기지 않고 결과 객체째 넘긴다.
			log.info("event=PRIVACY_CLEANUP_SUCCEEDED sessionsDeleted={} refreshTokensDeleted={} "
					+ "eventsDeleted={} storyViewsDeleted={} storyLinkCopiesDeleted={}",
					result.sessionsDeleted(), result.refreshTokensDeleted(), result.eventsDeleted(),
					result.storyViewsDeleted(), result.storyLinkCopiesDeleted());
		}
		catch (RuntimeException exception) {
			cleanupRun.fail(this.clock.instant(), summarize(exception));
			log.error("event=PRIVACY_CLEANUP_FAILED reason={}", exception.getClass().getSimpleName(), exception);
			alertFailure(exception);
			// 🔴 여기서 다시 던지지 않는다. run 행을 FAILED 로 남기는 것 자체가 이 배치가 실패를
			// 알리는 방법이다 — 다시 던져도 스케줄러 프레임워크가 로그 한 줄을 더 남기는 것 말고는
			// 아무도 더 보지 않는다.
		}
		this.runRepository.save(cleanupRun);
		return cleanupRun;
	}

	private String summarize(RuntimeException exception) {
		String message = exception.getMessage();
		String summary = exception.getClass().getSimpleName() + (message != null ? ": " + message : "");
		// text 칼럼이라 길이 제한은 없지만, 알림·로그에서 보기 좋게 과도한 길이는 자른다.
		return summary.length() > 2000 ? summary.substring(0, 2000) : summary;
	}

	private void alertFailure(RuntimeException exception) {
		String webhookUrl = this.properties.getAlertWebhookUrl();
		if (webhookUrl == null || webhookUrl.isBlank()) {
			log.warn("event=PRIVACY_CLEANUP_ALERT_SUPPRESSED reason=NO_WEBHOOK_CONFIGURED");
			return;
		}
		try {
			this.restClient.post().uri(webhookUrl).contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("text", "[GABOLLE] 개인정보 자동 정리 배치가 실패했습니다: "
							+ exception.getClass().getSimpleName() + " — privacy_cleanup_run 표를 확인하세요."))
					.retrieve().toBodilessEntity();
		}
		catch (RestClientException sendFailure) {
			log.error("event=PRIVACY_CLEANUP_ALERT_SEND_FAILED reason={}", sendFailure.getClass().getSimpleName());
		}
	}
}
