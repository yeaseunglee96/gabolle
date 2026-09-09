package com.gabolle.backend.itinerary.domain;

import java.time.Instant;

/**
 * 일정 구간 하나 — 항목과 항목 사이의 이동.
 *
 * <p>🔴 이 클래스도 {@link ItineraryItem} 과 같은 이유로 {@link ItineraryVersion} 에 매달린다
 * (판마다 복사). JPA·Spring 을 import 하지 않는다.
 */
public class ItineraryLeg {

    private final String itineraryLegId;
    private final String itineraryVersionId;

    private final int dayIndex;
    private final int sequence;

    /**
     * 🔴 {@code null} 이면 그 날의 첫 구간 — 여행 출발지에서 출발한다. {@code Trip} 도메인의
     * 주석 그대로 "출발지. 매일 여기서 일정이 시작된다."
     */
    private final String fromPlaceId;
    private final String toPlaceId;

    private final String travelMode;

    /** 일반적인 거리 추정치(m) — 어느 이동수단이든 잴 수 있으면 채운다. */
    private final Integer distanceM;
    private final Integer durationMin;

    /**
     * 🔴 이동수단이 {@code WALK} 일 때만 채운다. 대중교통 구간에 직선거리를 넣으면
     * "지하철로 이만큼 걸었다"처럼 읽혀서 틀린 답이 된다.
     */
    private final Integer walkingMeters;

    /** 🔴 bigData 의 경사·계단 데이터가 채울 자리. 이번 판에서는 전부 {@code null}. */
    private final Integer ascentM;
    private final Integer stairSteps;

    /**
     * 🔴 S15P21E201-179 — 위의 거리·시간을 얼마나 믿을 수 있는가.
     *
     * <p>{@code VERIFIED} 길찾기 실제 응답 · {@code ESTIMATED} 직선거리 어림값 ·
     * {@code UNKNOWN} 좌표가 없어 못 쟀다. 이 기능 이전에 만들어진 판은 {@code null} 이다 —
     * 그때 값이 무엇이었는지 알 수 없고, 모르는 것을 UNKNOWN 으로 적는 것도 하나의 주장이라
     * 아예 비워 둔다.
     *
     * <p>참·거짓이 아닌 이유는 "어림잡았다" 와 "아무것도 못 쟀다" 가 화면에 서로 다르게
     * 그려져야 하기 때문이다 — 앞은 "예상 25분", 뒤는 아무것도 안 띄운다.
     */
    private final ItineraryItem.DataStatus dataStatus;

    private final Instant createdAt;

    /**
     * S15P21E201-179 이전의 생성자를 그대로 남긴다 — 부르는 곳이 여럿이고, 그때 만들어진
     * 구간은 거리·시간의 출처를 알 수 없으므로 {@code dataStatus} 가 {@code null} 이다.
     */
    public ItineraryLeg(String itineraryLegId, String itineraryVersionId, int dayIndex, int sequence,
                        String fromPlaceId, String toPlaceId, String travelMode,
                        Integer distanceM, Integer durationMin, Integer walkingMeters,
                        Integer ascentM, Integer stairSteps, Instant createdAt) {
        this(itineraryLegId, itineraryVersionId, dayIndex, sequence, fromPlaceId, toPlaceId, travelMode,
                distanceM, durationMin, walkingMeters, ascentM, stairSteps, null, createdAt);
    }

    public ItineraryLeg(String itineraryLegId, String itineraryVersionId, int dayIndex, int sequence,
                        String fromPlaceId, String toPlaceId, String travelMode,
                        Integer distanceM, Integer durationMin, Integer walkingMeters,
                        Integer ascentM, Integer stairSteps, ItineraryItem.DataStatus dataStatus,
                        Instant createdAt) {

        if (dayIndex < 0) {
            throw new IllegalArgumentException("dayIndex 는 0 이상이어야 한다: " + dayIndex);
        }
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence 는 1 이상이어야 한다: " + sequence);
        }
        if (toPlaceId == null) {
            throw new IllegalArgumentException("toPlaceId 는 필수다");
        }
        if (fromPlaceId != null && fromPlaceId.equals(toPlaceId)) {
            throw new IllegalArgumentException("같은 장소로의 구간은 만들 수 없다: " + toPlaceId);
        }
        if (travelMode == null || travelMode.isBlank()) {
            throw new IllegalArgumentException("travelMode 는 필수다");
        }

        this.itineraryLegId = itineraryLegId;
        this.itineraryVersionId = itineraryVersionId;
        this.dayIndex = dayIndex;
        this.sequence = sequence;
        this.fromPlaceId = fromPlaceId;
        this.toPlaceId = toPlaceId;
        this.travelMode = travelMode;
        this.distanceM = distanceM;
        this.durationMin = durationMin;
        this.walkingMeters = walkingMeters;
        this.ascentM = ascentM;
        this.stairSteps = stairSteps;
        this.dataStatus = dataStatus;
        this.createdAt = createdAt;
    }

    public String itineraryLegId()     { return itineraryLegId; }
    public String itineraryVersionId() { return itineraryVersionId; }
    public int dayIndex()              { return dayIndex; }
    public int sequence()              { return sequence; }
    public String fromPlaceId()        { return fromPlaceId; }
    public String toPlaceId()          { return toPlaceId; }
    public String travelMode()         { return travelMode; }
    public Integer distanceM()         { return distanceM; }
    public Integer durationMin()       { return durationMin; }
    public Integer walkingMeters()     { return walkingMeters; }
    public Integer ascentM()           { return ascentM; }
    public Integer stairSteps()        { return stairSteps; }
    public ItineraryItem.DataStatus dataStatus() { return dataStatus; }

    public Instant createdAt()         { return createdAt; }
}
