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

    /**
     * 어떻게 고정했나 — S15P21E201-2017. 고정이 아니면 {@code null}.
     * 🔴 이 칸이 생기기 전의 고정(표의 {@code lock_mode} 가 NULL)은 {@link LockMode#ORDER} 로 읽는다 — 그때의 고정이
     * 실제로 하던 일(재계산이 빼지 않고 자리를 지킨다)이 지금의 순서 고정과 같다.
     */
    private final LockMode lockMode;

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
        this(itineraryItemId, itineraryVersionId, itemKey, dayIndex, visitDate, sequence, placeId, startTime,
                endTime, stayMinutes, locked, estimatedCostKrw, dataStatus, reasonCodes, warningCodes,
                sourceRequestId, createdAt, null);
    }

    /**
     * 고정 방식까지 받는 생성자. {@code locked} 인데 {@code lockMode} 가 {@code null} 이면 순서 고정이다.
     *
     * @throws IllegalArgumentException 고정이 아닌데 방식이 있다
     */
    public ItineraryItem(String itineraryItemId, String itineraryVersionId, String itemKey,
                         int dayIndex, LocalDate visitDate, int sequence, String placeId,
                         LocalTime startTime, LocalTime endTime, Integer stayMinutes,
                         boolean locked, Integer estimatedCostKrw, DataStatus dataStatus,
                         List<String> reasonCodes, List<String> warningCodes,
                         String sourceRequestId, Instant createdAt, LockMode lockMode) {

        if (!locked && lockMode != null) {
            throw new IllegalArgumentException("고정이 아닌 항목에 고정 방식(" + lockMode + ")이 있다");
        }
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
        this.lockMode = locked ? (lockMode == null ? LockMode.ORDER : lockMode) : null;
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

    /**
     * 고정 방식.
     * <ul>
     * <li>{@link #ORDER} 순서 고정 — 재계산이 이 곳을 빼지 않고 자리(순번)를 지킨다. 시각은 앞이 바뀌면 함께 움직인다
     * <li>{@link #TIME} 시각 고정 — 위에 더해 시작 시각도 지킨다. 앞 곳이 빠지거나 짧아지면 그 앞이 빈 시각이 된다
     * </ul>
     */
    public enum LockMode {
        ORDER,
        TIME
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
    public LockMode lockMode()         { return lockMode; }
    /** 시작 시각을 지켜야 하는 곳인가. */
    public boolean timeFixed()         { return lockMode == LockMode.TIME; }
    public Integer estimatedCostKrw()  { return estimatedCostKrw; }
    public DataStatus dataStatus()     { return dataStatus; }
    public List<String> reasonCodes()  { return reasonCodes; }
    public List<String> warningCodes() { return warningCodes; }
    public String sourceRequestId()    { return sourceRequestId; }
    public Instant createdAt()         { return createdAt; }
}
