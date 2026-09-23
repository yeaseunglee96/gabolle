package com.gabolle.backend.weather.domain;

import java.time.LocalTime;

/**
 * 한 시각의 예보. 기상청 원문에 그 시각·그 항목이 있을 때만 값이 있고, 없으면 null 이다 —
 * 0 은 「없다」(강수확률 0%, 강수 없음)이고 null 은 「모른다」라서 서로 채우지 않는다.
 *
 * 기온은 섭씨(TMP), 강수확률은 %(POP), 하늘상태는 SKY, 강수형태는 PTY 에서 온다.
 */
public record HourlyForecast(LocalTime time, Double temperature, SkyCondition skyCondition,
		Integer precipitationProbability, PrecipitationType precipitationType) {
}
