package com.gabolle.backend.privacy.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 개인정보 자동 정리 배치가 쓰는 설정.
 *
 * <p>TODO 보관 기간 기본값은 잠정값이다. {@code eventRetentionDays=90} 은 분기 단위 리포트가
 * 지난 분기를 다시 볼 수 있는 최소 기간, {@code sessionGraceDays=7} 은 세션 만료 뒤에도 부정
 * 사용 조사가 흔적을 볼 수 있는 유예로 잡은 것이다. 설계서의 확정 값이 확인되면 이 기본값만
 * 바꾸면 된다.
 */
@Component
@ConfigurationProperties(prefix = "gabolle.privacy.cleanup")
public class PrivacyCleanupProperties {

	/** 배치 자체를 켜고 끈다. 기본은 꺼짐 — 운영에 올릴 때 값을 확인하고 켠다(아래 cron 주석 참고). */
	private boolean enabled = false;

	/**
	 * 매일 도는 시각(cron, UTC 기준 — {@link PrivacyCleanupScheduler} 가 {@code zone = "UTC"} 로
	 * 고정해서 돌린다. {@link com.gabolle.backend.common.config.TimeConfiguration} 이 서버 전체
	 * 시각을 UTC 로 통일한 것과 같은 이유다).
	 *
	 * <p>기본값 {@code 19:00 UTC} 는 한국 시각(UTC+9)으로 다음 날 새벽 4시다 — 사용자 트래픽이
	 * 가장 적은 시간대에 삭제 부하를 준다.
	 */
	private String cron = "0 0 19 * * *";

	/**
	 * {@code auth_session}·{@code auth_refresh_token} 이 만료된 뒤(expiresAt 기준) 실제로
	 * 지워지기까지 남겨 두는 유예 일수.
	 */
	private int sessionGraceDays = 7;

	/** {@code event_outbox} 에서 이미 발행된(publishedAt IS NOT NULL) 행을 며칠 동안 보관하는가. */
	private int eventRetentionDays = 90;

	/**
	 * 조회 낱개({@code story_view})와 링크 복사 낱개({@code story_link_copy})를 며칠 보관하는가.
	 *
	 * <p>이 90 은 잠정값이 아니다. {@code StoryView} 도메인 주석과 {@code V20260918010000}
	 * 마이그레이션도 같은 값을 적고 있어, 바꾸려면 그쪽도 함께 고쳐야 한다.
	 */
	private int storyActivityRetentionDays = 90;

	/**
	 * 한 번 실행에서 지우는 상한(안전판). JPQL 벌크 삭제는 이 값을 직접 강제하지 않지만, 이
	 * 값을 이례적으로 낮게 잡아 두면 예상보다 큰 삭제가 조용히 일어나지 않았다는 뜻이 된다 —
	 * {@link PrivacyCleanupService} 가 카테고리별 삭제 건수를 이 값과 비교해 로그 경고를 남긴다.
	 */
	private int largeDeletionWarningThreshold = 100_000;

	/**
	 * 배치가 실패했을 때 알릴 MatterMost 수신 웹훅 URL. 비어 있으면(기본값) 알림을 보내지 않고
	 * ERROR 로그와 {@code privacy_cleanup_run} 행만 남는다 —
	 * {@link com.gabolle.backend.common.security.SecurityAlertProperties#getWebhookUrl()} 과
	 * 같은 "값이 없다고 기동을 막지 않는다" 계약이다. 운영이 원하면 그 값과 같은 웹훅 주소를
	 * 넣어 같은 채널로 모을 수 있다 — 코드가 강제하지는 않는다.
	 */
	private String alertWebhookUrl = "";

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getCron() {
		return cron;
	}

	public void setCron(String cron) {
		this.cron = cron;
	}

	public int getSessionGraceDays() {
		return sessionGraceDays;
	}

	public void setSessionGraceDays(int sessionGraceDays) {
		this.sessionGraceDays = sessionGraceDays;
	}

	public int getEventRetentionDays() {
		return eventRetentionDays;
	}

	public void setEventRetentionDays(int eventRetentionDays) {
		this.eventRetentionDays = eventRetentionDays;
	}

	public int getStoryActivityRetentionDays() {
		return storyActivityRetentionDays;
	}

	public void setStoryActivityRetentionDays(int storyActivityRetentionDays) {
		this.storyActivityRetentionDays = storyActivityRetentionDays;
	}

	public int getLargeDeletionWarningThreshold() {
		return largeDeletionWarningThreshold;
	}

	public void setLargeDeletionWarningThreshold(int largeDeletionWarningThreshold) {
		this.largeDeletionWarningThreshold = largeDeletionWarningThreshold;
	}

	/**
	 * 익명 세션(비회원 출입증)과 그 세션이 만든 여행을 언제 지우는가. 세 값이 함께 정한다.
	 *
	 * <ul>
	 * <li>{@code anonymousIdleDays} — 마지막 접속에서 이만큼 지나면 지운다. 출입증은 기기에만 있어서
	 * 이 기간 동안 아무 요청이 없으면 앱을 지웠거나 저장소를 비웠다고 본다.</li>
	 * <li>{@code anonymousTripGraceDays} — 다만 끝나지 않은 여행이 있으면 그 종료일에서 이만큼 지날 때까지
	 * 기다린다. 두 달 뒤 여행을 미리 짜 두고 앱을 안 연 사람이 출발 전에 일정을 잃지 않게.</li>
	 * <li>{@code anonymousMaxAgeDays} — 발급에서 이만큼 지나면 위 둘과 상관없이 지운다. 종료일을 먼
	 * 미래로 적어 무기한 남기는 것을 막는 상한이다.</li>
	 * </ul>
	 */
	private int anonymousIdleDays = 30;

	private int anonymousTripGraceDays = 7;

	private int anonymousMaxAgeDays = 180;

	/** 한 번 실행에서 지우는 익명 세션 수. 넘치면 다음 날 이어서 지운다 — 한 트랜잭션을 짧게 둔다. */
	private int anonymousBatchSize = 500;

	public int getAnonymousIdleDays() {
		return anonymousIdleDays;
	}

	public void setAnonymousIdleDays(int anonymousIdleDays) {
		this.anonymousIdleDays = anonymousIdleDays;
	}

	public int getAnonymousTripGraceDays() {
		return anonymousTripGraceDays;
	}

	public void setAnonymousTripGraceDays(int anonymousTripGraceDays) {
		this.anonymousTripGraceDays = anonymousTripGraceDays;
	}

	public int getAnonymousMaxAgeDays() {
		return anonymousMaxAgeDays;
	}

	public void setAnonymousMaxAgeDays(int anonymousMaxAgeDays) {
		this.anonymousMaxAgeDays = anonymousMaxAgeDays;
	}

	public int getAnonymousBatchSize() {
		return anonymousBatchSize;
	}

	public void setAnonymousBatchSize(int anonymousBatchSize) {
		this.anonymousBatchSize = anonymousBatchSize;
	}

	public String getAlertWebhookUrl() {
		return alertWebhookUrl;
	}

	public void setAlertWebhookUrl(String alertWebhookUrl) {
		this.alertWebhookUrl = alertWebhookUrl == null ? "" : alertWebhookUrl;
	}
}
