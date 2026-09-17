package com.gabolle.backend.privacy.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 개인정보 자동 정리 배치(S15P21E201-357 · -166)가 쓰는 설정.
 *
 * <h2>🔴 보관 기간 기본값은 확정된 정책이 아니라 잠정값이다</h2>
 *
 * 설계서 F-SYS-07 이 보관 기간의 정확한 숫자를 정하고 있을 가능성이 있지만, 이 구현 시점에는
 * {@code .docx} 원본만 있고 텍스트로 옮겨진 것이 없어 이 자리에서 그 숫자를 확인하지 못했다.
 * 아래 값은 업계에서 흔히 쓰는 보수적인 기본값이다 — {@code eventRetentionDays=90} 은 분기
 * 단위 리포트가 지난 분기 데이터를 다시 볼 수 있게 하는 최소한의 기간이고,
 * {@code sessionGraceDays=7} 은 세션이 만료된 뒤에도 부정 사용 조사(예: 탈취된 리프레시
 * 토큰 재사용 시도)가 그 흔적을 볼 수 있는 짧은 유예다. F-SYS-07 의 실제 값이 확인되면 이
 * 기본값을 바꾸는 것으로 끝난다 — 코드 구조는 바뀌지 않는다.
 *
 * <p>{@link com.gabolle.backend.common.security.SecurityAlertProperties} 와 같은 이유로 이
 * 클래스에 직접 {@code @Component} 를 붙인다.
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
	 * 조회 낱개({@code story_view})와 링크 복사 낱개({@code story_link_copy})를 며칠 보관하는가 —
	 * S15P21E201-1216.
	 *
	 * <h2>🔴 이 90 은 잠정값이 아니라 정해진 것이다</h2>
	 *
	 * 위의 {@link #eventRetentionDays} 와 달리 이 숫자는 <b>표를 만들 때 이미 정해졌다</b> —
	 * {@code story_view} 표 주석·{@code StoryView} 도메인 주석·{@code V20260918010000}
	 * 마이그레이션 주석 셋이 모두 「90일만 보관한다」고 적고 있다. 값을 바꾸려면 그 셋도 함께
	 * 고쳐야 한다. 한 곳만 고치면 문서가 코드와 다른 말을 하게 된다.
	 *
	 * <p>낱개가 있는 이유는 「사람 × 글 × 하루 한 번」을 지키기 위해서다. 그것만 보면 <b>어제
	 * 것만 있어도 된다.</b> 90일은 그보다 훨씬 넉넉하고, 그 이상은 사람이 무엇을 언제 읽었는지가
	 * 필요 이상으로 오래 남는 것이다.
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

	public String getAlertWebhookUrl() {
		return alertWebhookUrl;
	}

	public void setAlertWebhookUrl(String alertWebhookUrl) {
		this.alertWebhookUrl = alertWebhookUrl == null ? "" : alertWebhookUrl;
	}
}
