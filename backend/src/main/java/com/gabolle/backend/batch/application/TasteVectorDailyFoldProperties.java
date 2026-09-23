package com.gabolle.backend.batch.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 새벽 취향 접기를 백엔드가 직접 돌릴 것인가 (S15P21E201-1516).
 *
 * <p>지금은 Airflow DAG {@code taste_vector_daily} 가 같은 일을 한다 — 백엔드의
 * {@code /stale} 과 {@code /rebuild} 를 부르는 것이 전부다. 그 두 주소 뒤의 일은 이미 백엔드 안에
 * 있으므로, 부르는 역할만 여기로 옮기면 Airflow 없이도 판이 갱신된다.
 */
@ConfigurationProperties(prefix = "gabolle.taste-vector.daily-fold")
public class TasteVectorDailyFoldProperties {

	/**
	 * 🔴 기본값이 <b>꺼짐</b>이다. 켤 때 Airflow DAG {@code taste_vector_daily} 를 <b>일시정지</b>한다.
	 *
	 * <p>둘 다 켜 두면 같은 시각에 같은 사람을 두 번 접는다. 결과는 같다 — 두 번째는 표시가 이미
	 * 지나 있어 {@code UNCHANGED} 로 끝나거나, 동시에 접으면 「현재 판은 하나」 색인이 한쪽을 거부한다.
	 * 망가지지는 않지만 실패 로그가 쌓이고, 「누가 접었나」를 되짚을 자리가 둘이 된다.
	 */
	private boolean enabled = false;

	/**
	 * 언제 도는가. 기본은 매일 19:00 UTC — <b>한국 04:00</b>, DAG 와 같은 시각이다.
	 *
	 * <p>🔴 이 값을 바꿀 때 {@code TasteVectorDailyFoldScheduler} 의 <b>인라인 기본값</b>도 같이
	 * 맞춘다. 그쪽이 자리표시자를 푸는 실제 값이다.
	 */
	private String cron = "0 0 19 * * *";

	/**
	 * 한 번에 접을 최대 인원. DAG 의 {@code STALE_LIMIT} 과 같은 값이다.
	 *
	 * <p>넘치면 오늘 다 못 접을 뿐 실패가 아니다 — 남은 사람은 내일도 뒤처져 있으므로 다시 걸린다.
	 * {@link TasteVectorBatchService#MAX_BATCH} 보다 크면 그 값으로 잘린다.
	 */
	private int limit = 500;

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

	public int getLimit() {
		return this.limit;
	}

	public void setLimit(int limit) {
		this.limit = limit;
	}

}
