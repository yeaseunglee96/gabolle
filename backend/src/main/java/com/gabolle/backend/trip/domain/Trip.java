package com.gabolle.backend.trip.domain;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 여행 — 사용자가 입력한 조건 묶음 (TRIP-01).
 *
 * <p>🔴 조건을 저장하지 않으면 계산이 끝난 뒤 <b>무슨 조건으로 만든 일정인지</b>를
 * 되짚을 수 없다. 조건이 없으면 결과를 고칠 수도, 다시 만들 수도 없다.
 *
 * <p>ERD 의 {@code TRIPS} 표에 대응한다. 컬럼을 발명하지 않았다.
 */
public class Trip {

    private final String tripId;
    private final String createdBy;

    private final LocalDate startDate;
    private final LocalDate finishDate;

    /** 출발지. 매일 여기서 일정이 시작된다. */
    private final Double originLat;
    private final Double originLng;

    private final Integer budgetKrw;
    private final int partySize;

    /** 하루 활동 시간대. 예: {@code MORNING_TO_EVENING} */
    private final String timeWindow;

    /** 🔴 API-03 — 시간대를 공통 사전으로 고정한다. 안 맞추면 일정이 통째로 밀린다. */
    private final String timezone;

    private Status status;
    private final Instant createdAt;

    public Trip(String tripId, String createdBy,
                LocalDate startDate, LocalDate finishDate,
                Double originLat, Double originLng,
                Integer budgetKrw, int partySize,
                String timeWindow, String timezone,
                Instant createdAt) {

        if (startDate == null || finishDate == null) {
            throw new IllegalArgumentException("여행 시작일과 종료일은 필수다");
        }
        if (finishDate.isBefore(startDate)) {
            // 끝나는 날이 시작하는 날보다 앞일 수는 없다.
            throw new IllegalArgumentException(
                    "종료일(" + finishDate + ")이 시작일(" + startDate + ")보다 앞이다");
        }
        if (partySize < 1) {
            throw new IllegalArgumentException("인원은 1명 이상이어야 한다: " + partySize);
        }
        if (budgetKrw != null && budgetKrw < 0) {
            throw new IllegalArgumentException("예산은 음수일 수 없다: " + budgetKrw);
        }
        if (originLat != null && (originLat < -90 || originLat > 90)) {
            throw new IllegalArgumentException("위도 범위를 벗어났다: " + originLat);
        }
        if (originLng != null && (originLng < -180 || originLng > 180)) {
            throw new IllegalArgumentException("경도 범위를 벗어났다: " + originLng);
        }

        this.tripId = tripId;
        this.createdBy = createdBy;
        this.startDate = startDate;
        this.finishDate = finishDate;
        this.originLat = originLat;
        this.originLng = originLng;
        this.budgetKrw = budgetKrw;
        this.partySize = partySize;
        this.timeWindow = timeWindow;
        this.timezone = timezone != null ? timezone : "Asia/Seoul";
        this.status = Status.PLANNING;
        this.createdAt = createdAt;
    }

    /** 며칠짜리 여행인가. 당일치기는 1이다. */
    public int nights() {
        return (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, finishDate);
    }

    public int days() {
        return nights() + 1;
    }

    public enum Status {
        /** 조건만 저장된 상태. 아직 일정이 없다 */
        PLANNING,
        /** 일정이 만들어졌다 */
        READY,
        /** 여행 중 */
        IN_PROGRESS,
        COMPLETED,
        /** 🔴 TRIP-05 는 204 soft delete 다. 행을 지우지 않는다 */
        DELETED;

        public boolean isTerminal() {
            return this == COMPLETED || this == DELETED;
        }
    }

    /** 🔴 TRIP-05 — soft delete. 행을 지우지 않는 이유는 일정·이벤트가 이 여행을 가리키기 때문이다. */
    public void markDeleted() {
        if (status == Status.DELETED) {
            return;
        }
        this.status = Status.DELETED;
    }

    public void markReady() {
        if (status.isTerminal()) {
            throw new IllegalStateException("끝난 여행은 상태를 바꿀 수 없다: " + status);
        }
        this.status = Status.READY;
    }

    public String tripId()       { return tripId; }
    public String createdBy()    { return createdBy; }
    public LocalDate startDate() { return startDate; }
    public LocalDate finishDate(){ return finishDate; }
    public Double originLat()    { return originLat; }
    public Double originLng()    { return originLng; }
    public Integer budgetKrw()   { return budgetKrw; }
    public int partySize()       { return partySize; }
    public String timeWindow()   { return timeWindow; }
    public String timezone()     { return timezone; }
    public Status status()       { return status; }
    public Instant createdAt()   { return createdAt; }
}
