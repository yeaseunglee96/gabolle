package com.gabolle.backend.weather.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 기상청 단기예보 응답의 항목 하나. 시각마다 여러 category 가 오고, 여기서 쓰는 것은
 * 기온(TMP·TMN·TMX) · 강수확률(POP) · 하늘상태(SKY) · 강수형태(PTY) 다.
 *
 * 정정(2026-09-23): 여기 「3시간 간격으로」라고 적혀 있었다. 운영 캐시를 재 보니 가까운
 * 날은 1시간 간격이고 먼 날만 3시간 간격이다 — 자세한 것은 KmaForecastAggregator 머리말.
 */
public record KmaForecastItem(String category, LocalDate fcstDate, LocalTime fcstTime, String value) {
}
