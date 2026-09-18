package com.gabolle.backend.weather.domain;

import java.time.LocalDate;

/**
 * 하루치 예보 요약 — S15P21E201-366.
 *
 * @param date 대상 날짜
 * @param minTemperature 최저기온(섭씨). 기상청이 그 날의 TMN 을 안 줬으면 그 날 TMP 값들의
 *        최솟값으로 대신한다
 * @param maxTemperature 최고기온(섭씨). TMX 가 없으면 TMP 값들의 최댓값으로 대신한다
 * @param precipitationProbability 강수확률(%) — 그 날 여러 시간대 중 최댓값
 * @param skyCondition 하늘상태 — 정오에 가장 가까운 시간대 값을 대표로 쓴다
 */
public record DailyForecast(LocalDate date, Double minTemperature, Double maxTemperature,
		Integer precipitationProbability, SkyCondition skyCondition) {
}
