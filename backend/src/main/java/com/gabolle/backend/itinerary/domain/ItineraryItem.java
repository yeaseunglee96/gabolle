package com.gabolle.backend.itinerary.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 일정 항목 하나 — 어느 판의 어느 날, 몇 번째로 어느 장소를 들르는가.
 * 부모는 {@link Itinerary} 가 아니라 {@link ItineraryVersion} 이다. 판이 덮어쓰지 않는
 * 스냅샷이라 항목도 판마다 복사된다 — {@code itineraryId} 에 매달면 v2 를 만들며 항목을 고치는
 * 순간 v1 이 가리키던 내용이 소급해서 바뀐다.
 * JPA·Spring 을 import 하지 않는다.
 */
public class ItineraryItem {

    private final String itineraryItemId;
    private final String itineraryVersionId;

    /**
     * 판을 건너 같은 항목을 가리키는 열쇠. 컨트롤러가 URL 로 받는 {@code itemId} 가 이 값이다.
     * PK({@code itineraryItemId})는 판마다 새로 생겨 그 자리를 대신할 수 없다 — 판을 복사할 때는
     * 이 값을 그대로 물려준다.
     */
    private final String itemKey;

    /** 첫날이 0. */
    private final int dayIndex;
    private final LocalDate visitDate;
    /** 그 날 안에서 1부터. */
    private final int sequence;

    private final String placeId;

    /**
     * 전부 NULL 일 수 있다. 프리셋(여행의 활동 시간대)을 실제 시각으로 바꾸는 규칙이 아직
     * 확정되지 않았다 — 지어내지 않는다.
     */
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final Integer stayMinutes;

    private final boolean locked;

    /** NULL 이면 "모른다" 다. {@code place} 표에 비용 칸이 없다 — 0 은 "공짜" 라는 다른 사실이다. */
    private final Integer estimatedCostKrw;

    private final DataStatus dataStatus;

    private final List<String> reasonCodes;
    private final List<String> warningCodes;

    /** 사용자가 손으로 넣은 항목은 {@code null} 이다. */
    private final String sourceRequestId;

    private final Instant createdAt;

    public ItineraryItem(String itineraryItemId, String itineraryVersionId, String itemKey,
                         int dayIndex, LocalDate visitDate, int sequence, String placeId,
                         LocalTime startTime, LocalTime endTime, Integer stayMinutes,
                         boolean locked, Integer estimatedCostKrw, DataStatus dataStatus,
                         List<String> reasonCodes, List<String> warningCodes,
                         String sourceRequestId, Instant createdAt) {

        if (dayIndex < 0) {
            throw new IllegalArgumentException("dayIndex 는 0 이상이어야 한다: " + dayIndex);
        }
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence 는 1 이상이어야 한다: " + sequence);
        }
        if (stayMinutes != null && stayMinutes <= 0) {
            throw new IllegalArgumentException("stayMinutes 는 양수여야 한다: " + stayMinutes);
        }
        if (estimatedCostKrw != null && estimatedCostKrw < 0) {
            throw new IllegalArgumentException("estimatedCostKrw 는 음수일 수 없다: " + estimatedCostKrw);
        }
        // 반쪽 시간은 시간이 아니다 — place.ck_place_origin_pair 와 같은 논리다.
        if ((startTime == null) != (endTime == null)) {
            throw new IllegalArgumentException("startTime 과 endTime 은 함께 있거나 함께 없어야 한다");
        }
        if (startTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime(" + endTime + ") 은 startTime(" + startTime + ") 보다 뒤여야 한다");
        }
        if (dataStatus == null) {
            throw new IllegalArgumentException("dataStatus 는 필수다");
        }

        this.itineraryItemId = itineraryItemId;
        this.itineraryVersionId = itineraryVersionId;
        this.itemKey = itemKey;
        this.dayIndex = dayIndex;
        this.visitDate = visitDate;
        this.sequence = sequence;
        this.placeId = placeId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.stayMinutes = stayMinutes;
        this.locked = locked;
        this.estimatedCostKrw = estimatedCostKrw;
        this.dataStatus = dataStatus;
        this.reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        this.warningCodes = warningCodes == null ? List.of() : List.copyOf(warningCodes);
        this.sourceRequestId = sourceRequestId;
        this.createdAt = createdAt;
    }

    /** 이 항목의 시각 정보를 얼마나 믿을 수 있는가. */
    public enum DataStatus {
        VERIFIED,
        ESTIMATED,
        /** 지어낸 값이 아니라 "모른다" 를 그대로 남긴 상태. */
        UNKNOWN
    }

    public String itineraryItemId()    { return itineraryItemId; }
    public String itineraryVersionId() { return itineraryVersionId; }
    public String itemKey()            { return itemKey; }
    public int dayIndex()              { return dayIndex; }
    public LocalDate visitDate()       { return visitDate; }
    public int sequence()              { return sequence; }
    public String placeId()            { return placeId; }
    public LocalTime startTime()       { return startTime; }
    public LocalTime endTime()         { return endTime; }
    public Integer stayMinutes()       { return stayMinutes; }
    public boolean locked()            { return locked; }
    public Integer estimatedCostKrw()  { return estimatedCostKrw; }
    public DataStatus dataStatus()     { return dataStatus; }
    public List<String> reasonCodes()  { return reasonCodes; }
    public List<String> warningCodes() { return warningCodes; }
    public String sourceRequestId()    { return sourceRequestId; }
    public Instant createdAt()         { return createdAt; }
}
