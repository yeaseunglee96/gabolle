package com.gabolle.backend.weather.domain;

import java.time.LocalDate;

/**
 * 하루치 예보 요약. 기온은 섭씨이고 TMN·TMX 가 없으면 그 날 TMP 의 최솟값·최댓값이다.
 * 강수확률(%)은 그 날 시간대 중 최댓값, 하늘상태는 정오에 가장 가까운 시간대 값이다.
 */
public record DailyForecast(LocalDate date, Double minTemperature, Double maxTemperature,
		Integer precipitationProbability, SkyCondition skyCondition) {
}
