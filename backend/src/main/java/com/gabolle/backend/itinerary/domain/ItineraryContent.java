package com.gabolle.backend.itinerary.domain;

import java.util.List;

/**
 * 판 하나의 전체 내용 — 판 + 항목 목록 + 구간 목록 + 제외 목록.
 *
 * <p>{@link ItineraryRepository#findContent(String, int)} 가 돌려주는 모양이다. 판만
 * 따로 읽는 {@link ItineraryRepository#findVersion(String, int)} 과 달리, 화면이 일정을
 * 통째로 그리려면 항목·구간까지 한 번에 필요하다.
 *
 * <h2>🔴 2026-09-06 (S15P21E201-249) — exclusions 를 더했다</h2>
 * 3-인자 생성자는 일부러 남기지 않는다. 이 칸을 빼먹은 호출부가 컴파일 오류로 드러나야
 * "제외 목록을 안 물려주는 판이 조용히 생기는" 사고를 막을 수 있다 — {@link ItineraryRevision}
 * 클래스 javadoc의 "고를 수 없게 만드는 것이 기억해서 지키는 것보다 낫다"와 같은 판단이다.
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
