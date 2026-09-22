package com.gabolle.backend.batch.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 옛 행동 신호를 얼마나 빨리 옅게 만들 것인가 (S15P21E201-1501).
 *
 * <h2>왜 감쇠가 필요한가</h2>
 *
 * 행동 무게는 {@code weight = raw/(|raw|+K)} 이고 {@code raw} 는 기여값의 합인데,
 * <b>{@code raw} 는 시간을 모른다.</b>
 *
 * <pre>
 * 3개월 전 카페 좋아요 스무 번   → raw +20
 * 최근    카페 계속 빼기 스무 번 → raw -20
 *                                 ─────
 *                                 raw   0  → 「모름」
 * </pre>
 *
 * 실제로는 「이제 카페를 싫어한다」가 맞다. 지금 이게 안 터지는 이유는 <b>90일 지난 이벤트가
 * 표에서 사라지면서 {@code raw} 에서도 빠지기</b> 때문이다 — 배치가 매번 전 이력을 다시 읽으므로
 * <b>삭제가 시간 가중 노릇</b>을 하고 있다. 그래서 감쇠를 따로 안 넣어 뒀다.
 *
 * <p>배치를 걷어내면 그 고리가 끊긴다. 소비자는 저장된 숫자에 더하므로 원본이 지워져도 남는다.
 * <b>감쇠가 그 자리를 대신한다.</b>
 */
@ConfigurationProperties(prefix = "gabolle.taste.decay")
public class TasteDecayProperties {

	/**
	 * 🔴 기본값이 <b>꺼짐</b>이다. 카프카 스위치 셋과 <b>함께</b> 켠다.
	 *
	 * <p>배치가 아직 도는 동안 이것만 켜면 손해는 없다 — 배치가 접을 때마다 새 판을 만들어
	 * 덮으므로 감쇠분이 사라진다. 그래도 켜진 채로 두면 「왜 취향이 옅어지지」를 설명할 자리가
	 * 둘이 된다. 같이 켜는 편이 되짚기 쉽다.
	 */
	private boolean enabled = false;

	/**
	 * 하루에 한 번 {@code raw} 에 곱하는 값.
	 *
	 * <p>🔴 <b>{@code weight} 가 아니라 {@code raw} 에 곱한다.</b> {@code weight} 는 이미 눌러
	 * 담은 값이라 거기 곱하면 뜻이 없다.
	 *
	 * <p>0.97 이면 90일 뒤 약 6% 가 남는다 ({@code 0.97^90 ≈ 0.064}). 지금 이벤트 보관 기간이
	 * 90일이라 <b>「삭제가 하던 일」과 대강 같은 속도</b>다. 더 빨리 잊게 하려면 내리고, 더 오래
	 * 기억하려면 올린다.
	 *
	 * <p>1.0 이면 감쇠가 없는 것과 같다. 0 이하나 1 초과는 뜻이 없어 서비스가 거부한다.
	 */
	private double dailyFactor = 0.97;

	/**
	 * 언제 도는가. 기본은 매일 04:30 UTC (한국 13:30).
	 *
	 * <p>🔴 이 값을 바꿀 때 {@code TasteDecayScheduler} 의 <b>인라인 기본값</b>도 같이 맞춘다.
	 * 그쪽이 자리표시자를 푸는 실제 값이다.
	 */
	private String cron = "0 30 4 * * *";

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public double getDailyFactor() {
		return this.dailyFactor;
	}

	public void setDailyFactor(double dailyFactor) {
		this.dailyFactor = dailyFactor;
	}

	public String getCron() {
		return this.cron;
	}

	public void setCron(String cron) {
		this.cron = cron;
	}

}
