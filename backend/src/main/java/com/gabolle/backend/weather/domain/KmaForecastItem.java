package com.gabolle.backend.weather.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 기상청 단기예보 응답의 항목 하나 — S15P21E201-366.
 *
 * <p>기상청은 3시간 간격으로 여러 항목({@code category})을 준다. 여기서 쓰는 것은 기온
 * ({@code TMP}·{@code TMN}·{@code TMX}), 강수확률({@code POP}), 하늘상태({@code SKY}) 다.
 */
public record KmaForecastItem(String category, LocalDate fcstDate, LocalTime fcstTime, String value) {
}
