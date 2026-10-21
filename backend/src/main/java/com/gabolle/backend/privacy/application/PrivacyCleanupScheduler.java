package com.gabolle.backend.privacy.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.gabolle.backend.auth.service.AnonymousSessionCleanupService;
import com.gabolle.backend.privacy.domain.PrivacyCleanupResult;
import com.gabolle.backend.privacy.domain.PrivacyCleanupRun;
import com.gabolle.backend.privacy.repository.PrivacyCleanupRunRepository;

/**
 * 개인정보 자동 정리 배치를 매일 돌린다.
 *
 * <p>실행마다 {@link PrivacyCleanupRun} 행을 하나 남긴다 — 성공/실패와 카테고리별 삭제 건수.
 * 실패하면 ERROR 로그와, {@code alertWebhookUrl} 이 설정돼 있으면 MatterMost 알림을 보낸다.
 *
 * <p>{@link com.gabolle.backend.common.security.SecurityAlertNotifier} 를 재사용하지 않는다.
 * 그쪽은 짧은 시간의 급증을 판정하는 슬라이딩 윈도가 핵심인데, 이 배치는 하루 한 번 돌고
 * 실패하면 바로 알려야 한다. 웹훅이 없으면 로그만 남기고 전송이 실패해도 예외를 삼킨다 —
 * 그 실패 때문에 실행 기록 저장이 막히면 안 된다.
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

	/** 익명 세션 정리. 인증 빈을 안 올리는 시험 슬라이스에서는 없다 — 그때는 건너뛴다. */
	private final ObjectProvider<AnonymousSessionCleanupService> anonymousCleanup;

	public PrivacyCleanupScheduler(PrivacyCleanupService cleanupService, PrivacyCleanupRunRepository runRepository,
			PrivacyCleanupProperties properties, Clock clock, RestClient.Builder restClientBuilder,
			ObjectProvider<AnonymousSessionCleanupService> anonymousCleanup) {
		this.anonymousCleanup = anonymousCleanup;
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

	// 인라인 기본값(:0 0 19 * * *)을 반드시 남긴다. application.properties 의
	// gabolle.privacy.cleanup.cron 에 기대면 그 줄이 지워지는 순간 플레이스홀더가 안 풀려
	// BeanCreationException 으로 애플리케이션 전체가 기동을 못 한다 —
	// PrivacyCleanupProperties 의 자바 기본값은 @ConfigurationProperties 바인딩에만 쓰이고
	// @Scheduled 의 플레이스홀더 해석에는 관여하지 않는다.
	// 이 값은 PrivacyCleanupProperties 의 자바 기본값과 같게 유지한다.
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
	 * <p>이 메서드에는 {@code @Transactional} 을 붙이지 않는다.
	 * {@link PrivacyCleanupService#cleanup()} 이 이미 트랜잭션이라, 여기까지 걸면 안쪽 호출이
	 * 같은 트랜잭션에 합류해 cleanup() 의 예외가 그것을 rollback-only 로 표시한다. 아래 catch
	 * 에서 잡아도 커밋 시점에 {@code UnexpectedRollbackException} 이 나면서 실패를 적으려던
	 * 기록 행까지 함께 롤백된다.
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
			// 카테고리를 더할 때 로그와 succeed() 중 한쪽만 고치는 일이 없게 결과 객체째 넘긴다.
			log.info("event=PRIVACY_CLEANUP_SUCCEEDED sessionsDeleted={} refreshTokensDeleted={} "
					+ "eventsDeleted={} storyViewsDeleted={} storyLinkCopiesDeleted={}",
					result.sessionsDeleted(), result.refreshTokensDeleted(), result.eventsDeleted(),
					result.storyViewsDeleted(), result.storyLinkCopiesDeleted());

			// 익명 세션은 자기 트랜잭션에서 따로 지운다. 실행 기록 표에는 칸이 없어 로그로만 남긴다 — 칸을 더하려면
			// privacy_cleanup_run 마이그레이션이 필요한데, 건수는 로그로 충분하다.
			AnonymousSessionCleanupService anonymous = this.anonymousCleanup.getIfAvailable();
			if (anonymous != null) {
				int anonymousSessionsDeleted = anonymous.cleanup(this.properties.getAnonymousIdleDays(),
						this.properties.getAnonymousTripGraceDays(), this.properties.getAnonymousMaxAgeDays(),
						this.properties.getAnonymousBatchSize());
				log.info("event=PRIVACY_CLEANUP_ANONYMOUS_SUCCEEDED anonymousSessionsDeleted={} batchSize={}",
						anonymousSessionsDeleted, this.properties.getAnonymousBatchSize());
			}
		}
		catch (RuntimeException exception) {
			cleanupRun.fail(this.clock.instant(), summarize(exception));
			log.error("event=PRIVACY_CLEANUP_FAILED reason={}", exception.getClass().getSimpleName(), exception);
			alertFailure(exception);
			// 여기서 다시 던지지 않는다. run 행을 FAILED 로 남기는 것 자체가 이 배치가 실패를
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
