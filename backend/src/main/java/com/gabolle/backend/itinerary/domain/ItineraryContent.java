package com.gabolle.backend.itinerary.domain;

import java.util.List;

/**
 * 판 하나의 전체 내용 — 판 + 항목 목록 + 구간 목록.
 *
 * <p>{@link ItineraryRepository#findContent(String, int)} 가 돌려주는 모양이다. 판만
 * 따로 읽는 {@link ItineraryRepository#findVersion(String, int)} 과 달리, 화면이 일정을
 * 통째로 그리려면 항목·구간까지 한 번에 필요하다.
 */
public record ItineraryContent(
        ItineraryVersion version,
        List<ItineraryItem> items,
        List<ItineraryLeg> legs) {

    public ItineraryContent {
        items = items == null ? List.of() : List.copyOf(items);
        legs = legs == null ? List.of() : List.copyOf(legs);
    }
}
