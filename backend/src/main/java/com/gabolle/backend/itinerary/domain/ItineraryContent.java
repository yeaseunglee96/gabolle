package com.gabolle.backend.itinerary.domain;

import java.util.List;

/**
 * 판 하나의 전체 내용 — 판 + 항목 목록 + 구간 목록 + 제외 목록.
 * {@link ItineraryRepository#findContent(String, int)} 가 돌려주는 모양이다. 판만 따로 읽는
 * {@link ItineraryRepository#findVersion(String, int)} 과 달리, 화면이 일정을 통째로 그리려면
 * 항목·구간까지 한 번에 필요하다.
 * exclusions 를 뺀 3-인자 생성자는 일부러 두지 않는다. 그 칸을 빼먹은 호출부가 컴파일 오류로
 * 드러나야 제외 목록을 안 물려주는 판이 조용히 생기는 것을 막을 수 있다.
 */
public record ItineraryContent(
        ItineraryVersion version,
        List<ItineraryItem> items,
        List<ItineraryLeg> legs,
        List<ItineraryExclusion> exclusions) {

    public ItineraryContent {
        items = items == null ? List.of() : List.copyOf(items);
        legs = legs == null ? List.of() : List.copyOf(legs);
        exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
    }
}
