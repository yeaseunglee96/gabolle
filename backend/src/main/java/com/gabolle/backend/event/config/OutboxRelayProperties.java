package com.gabolle.backend.event.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 아웃박스 릴레이의 설정 (S15P21E201-561). <b>브로커가 무엇이든 성립하는 값만</b> 둔다.
 *
 * <p>🔴 {@code cron} 값이 여기 있지만, {@code @Scheduled} 는 이 자바 기본값을 <b>안 본다.</b>
 * 그 애너테이션의 {@code ${...}} 자리표시자는 환경(properties)에서만 풀린다. 그래서
 * {@code OutboxRelayScheduler} 쪽에 인라인 기본값을 반드시 같이 적고, 이 값과 같게 유지한다 —
 * 2026-09-08 에 개인정보 정리 배치가 이것 때문에 기동을 통째로 못 했다.
 */
@ConfigurationProperties(prefix = "gabolle.event.outbox-relay")
public class OutboxRelayProperties {

	/** 릴레이를 주기적으로 돌릴 것인가. 꺼도 {@code relayOnce()} 를 손으로 부를 수는 있다. */
	private boolean enabled = false;

	/**
	 * 얼마나 자주 돌 것인가. 기본은 1분마다.
	 *
	 * <p>초 단위로 내리지 않는 이유 — 이벤트는 배치·지표가 쓰는 것이라 초 단위 지연이 필요한
	 * 소비자가 아직 없다. 필요해지면 그때 내린다. 지금 내리면 보낼 것이 없는데도 1초마다
	 * DB 를 두드린다.
	 */
	private String cron = "0 * * * * *";

	/**
	 * 한 건을 몇 번까지 다시 보내 볼 것인가.
	 *
	 * <p>🔴 이 값이 없으면 <b>독약 메시지</b>(영원히 실패하는 한 건)가 그 뒤의 모든 이벤트를
	 * 막는다. 릴레이는 순서를 지키려고 실패한 자리에서 멈추기 때문이다. 상한을 넘긴 행은
	 * 조회에서 빠지므로 줄이 다시 흐른다. 그 행을 <b>지우지는 않는다.</b>
	 */
	private int maxAttempts = 5;

	/** 한 차례에 몇 건씩 볼 것인가. 너무 크면 한 건 실패에 전체가 늦어진다. */
	private int batchSize = 100;

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getCron() {
		return this.cron;
	}

	public void setCron(String cron) {
		this.cron = cron;
	}

	public int getMaxAttempts() {
		return this.maxAttempts;
	}

	public void setMaxAttempts(int maxAttempts) {
		this.maxAttempts = maxAttempts;
	}

	public int getBatchSize() {
		return this.batchSize;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}
}
