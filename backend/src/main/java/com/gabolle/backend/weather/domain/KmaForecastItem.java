package com.gabolle.backend.weather.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 기상청 단기예보 응답의 항목 하나. 3시간 간격으로 여러 category 가 오고, 여기서 쓰는 것은
 * 기온(TMP·TMN·TMX) · 강수확률(POP) · 하늘상태(SKY) 다.
 */
public record KmaForecastItem(String category, LocalDate fcstDate, LocalTime fcstTime, String value) {
}
