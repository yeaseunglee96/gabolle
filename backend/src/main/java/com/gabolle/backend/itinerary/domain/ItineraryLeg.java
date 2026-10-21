package com.gabolle.backend.itinerary.domain;

import java.time.Instant;
import java.util.List;

/**
 * 일정 구간 하나 — 항목과 항목 사이의 이동.
 * {@link ItineraryItem} 과 같은 이유로 {@link ItineraryVersion} 에 매달린다(판마다 복사).
 * JPA·Spring 을 import 하지 않는다.
 */
public class ItineraryLeg {

    private final String itineraryLegId;
    private final String itineraryVersionId;

    private final int dayIndex;
    private final int sequence;

    /** {@code null} 이면 그 날의 첫 구간 — 여행 출발지에서 출발한다. */
    private final String fromPlaceId;
    private final String toPlaceId;

    private final String travelMode;

    /** 일반적인 거리 추정치(m) — 어느 이동수단이든 잴 수 있으면 채운다. */
    private final Integer distanceM;
    private final Integer durationMin;

    /**
     * 이동수단이 {@code WALK} 일 때만 채운다. 대중교통 구간에 직선거리를 넣으면 "지하철로 이만큼
     * 걸었다" 처럼 읽혀서 틀린 답이 된다.
     */
    private final Integer walkingMeters;

    /**
     * 오르막 합(m)·계단 칸 수. 둘 다 아직 아무도 채우지 않아 전부 {@code null} 이다 — 보행 그래프는 계단이 「있는지」만
     * 알고 몇 칸인지는 모른다. 길의 경사·계단은 {@link #pieces} 가 조각 단위로 싣는다.
     */
    private final Integer ascentM;
    private final Integer stairSteps;

    /**
     * 길을 경사·계단이 같은 조각으로 나눈 것 — 번호는 {@link #path} 의 자리다({@code path[from]..path[to]}, 둘 다 포함).
     * 경로 API({@code /routes})의 {@code pieces} 와 같은 뜻이다.
     * <p>
     * 🔴 선형이 있을 때만 있다. 선형이 없으면 가리킬 자리가 없으므로 {@code null} 이다. 우리 보행 그래프가 찾은 걷기만
     * 조각을 내고, 자동차·대중교통·어림 구간은 {@code null} 이다 — 모르는 경사를 0(평지)으로 지어내지 않는다.
     * 이 칸이 생기기 전(2026-09-29)에 만든 판도 {@code null} 이다.
     */
    private final List<Piece> pieces;

    /**
     * 조각 하나. {@code slopePercent} 는 방향 없는 기울기(%)이고 모르면 {@code null} — 0(평지)과 다르다.
     * {@code shade} 는 그늘(0~1, 1 이 하루 종일 그늘, 0.1 단위)이고 모르면 {@code null} — 0(볕)과 다르다.
     * 그늘 칸이 생기기 전(2026-09-30)에 만든 판은 조각이 있어도 {@code shade} 가 {@code null} 이다.
     */
    public record Piece(int from, int to, Double slopePercent, boolean stairs, Double shade) {

        /** 그늘 칸이 생기기 전의 모양 — 그늘을 모르는 조각이다. */
        public Piece(int from, int to, Double slopePercent, boolean stairs) {
            this(from, to, slopePercent, stairs, null);
        }
    }

    /**
     * 이 구간이 지나는 길의 좌표 목록 — 「어느 길로 가는지」. {@code [경도, 위도]} 순서다
     * (GeoJSON·지도 라이브러리와 같은 순서이고 {@code RouteLeg.path} 가 그렇게 정해 뒀다).
     * <p>
     * 🔴 <b>모르면 {@code null} 이고, 직선을 대신 넣지 않는다.</b> 출발·도착 두 점을 이으면
     * 「선형」 모양이 되긴 하지만 그것은 길이 아니라 직선이다. 한 칸에 섞으면 실제로 잰 길과
     * 구분이 사라져 화면이 직선을 실선으로 그린다 — 지도에 직선이 그려지던 것이 바로 그
     * 문제였다(S15P21E201-1251, 프론트는 S15P21E201-1234).
     * <p>
     * 이 칸이 생기기 전에 만들어진 판은 {@code null} 이다. 그때 어느 길로 갔는지는 알 수 없다.
     */
    private final List<double[]> path;

    /**
     * 위의 거리·시간을 얼마나 믿을 수 있는가 — {@code VERIFIED} 길찾기 실제 응답 ·
     * {@code ESTIMATED} 직선거리 어림값 · {@code UNKNOWN} 은 좌표가 없어 못 쟀다. 이 칸이 생기기
     * 전에 만들어진 판은 {@code null} 이다 — 그때 값이 무엇이었는지 알 수 없고, 모르는 것을
     * UNKNOWN 으로 적는 것도 하나의 주장이라 아예 비워 둔다.
     * 참·거짓이 아닌 이유는 "어림잡았다" 와 "아무것도 못 쟀다" 가 화면에 서로 다르게 그려져야
     * 하기 때문이다 — 앞은 "예상 25분", 뒤는 아무것도 안 띄운다.
     */
    private final ItineraryItem.DataStatus dataStatus;

    /**
     * 이 구간의 이동 요금(원).
     * {@code null} 은 "얼마인지 모른다" 이고 {@code 0} 은 "공짜다" 다. 둘을 같게 다루면 요금 출처가
     * 없는 이동수단이 화면에서 전부 「무료」가 된다.
     * 지금 값이 있는 것은 자동차 계열(택시·자가용·렌터카)뿐이다. 도보에는 요금이라는 것이 없고,
     * 대중교통은 업체가 주지 않는다.
     * 입장료와 합치지 않는다. 요금은 구간의 성질이고 입장료는 장소의 성질이라, 합치면 입장료가
     * 없는 지금 "교통비만 낸 합계" 가 "총비용" 으로 읽힌다.
     */
    private final Integer fareKrw;

    /**
     * 엔진이 처음 어림한 이동 분 — 실제 이동으로 고친 배율을 적용하기 전(S15P21E201-1700). {@link #durationMin} 은 고친
     * 값이다. 보정 계산이 「실제 ÷ 이 값」을 재므로 판을 옮길 때 이 칸을 흘리면 고친 값이 어림으로 읽혀 배율이 겹쳐
     * 곱해진다. {@code null} 이면 이 칸 전의 구간이고, 그때는 {@link #durationMin} 이 곧 어림이다.
     */
    private final Integer uncalibratedDurationMin;

    private final Instant createdAt;

    /**
     * {@code dataStatus} 이전의 생성자를 그대로 남긴다 — 부르는 곳이 여럿이고, 그때 만들어진
     * 구간은 거리·시간의 출처를 알 수 없으므로 {@code dataStatus} 가 {@code null} 이다.
     */
    public ItineraryLeg(String itineraryLegId, String itineraryVersionId, int dayIndex, int sequence,
                        String fromPlaceId, String toPlaceId, String travelMode,
                        Integer distanceM, Integer durationMin, Integer walkingMeters,
                        Integer ascentM, Integer stairSteps, Instant createdAt) {
        this(itineraryLegId, itineraryVersionId, dayIndex, sequence, fromPlaceId, toPlaceId, travelMode,
                distanceM, durationMin, walkingMeters, ascentM, stairSteps, null, null, createdAt);
    }

    /**
     * 요금 없이 만든다 — 요금 칸 이전의 생성자를 그대로 남긴다. 부르는 곳이 여럿이라 한 번에
     * 안 고친다. 요금을 모르는 것이 기본값이고, 그것이 지금 대부분의 구간에서 맞는 값이다.
     */
    public ItineraryLeg(String itineraryLegId, String itineraryVersionId, int dayIndex, int sequence,
                        String fromPlaceId, String toPlaceId, String travelMode,
                        Integer distanceM, Integer durationMin, Integer walkingMeters,
                        Integer ascentM, Integer stairSteps, ItineraryItem.DataStatus dataStatus,
                        Instant createdAt) {
        this(itineraryLegId, itineraryVersionId, dayIndex, sequence, fromPlaceId, toPlaceId, travelMode,
                distanceM, durationMin, walkingMeters, ascentM, stairSteps, dataStatus, null, createdAt);
    }

    /**
     * 선형 없이 만든다 — 선형 칸 이전의 생성자를 그대로 남긴다. 부르는 곳이 여럿이라 한 번에
     * 안 고친다. 어느 길로 가는지 모르는 것이 기본값이고, 실제 길찾기 응답을 받은 구간에서만
     * 값이 들어온다.
     */
    public ItineraryLeg(String itineraryLegId, String itineraryVersionId, int dayIndex, int sequence,
                        String fromPlaceId, String toPlaceId, String travelMode,
                        Integer distanceM, Integer durationMin, Integer walkingMeters,
                        Integer ascentM, Integer stairSteps, ItineraryItem.DataStatus dataStatus,
                        Integer fareKrw, Instant createdAt) {
        this(itineraryLegId, itineraryVersionId, dayIndex, sequence, fromPlaceId, toPlaceId, travelMode,
                distanceM, durationMin, walkingMeters, ascentM, stairSteps, dataStatus, fareKrw, null,
                createdAt);
    }

    /** 보정 전 칸 이전의 생성자 — 보정 없이 만든 구간이다. 부르는 곳이 여럿이라 한 번에 안 고친다. */
    public ItineraryLeg(String itineraryLegId, String itineraryVersionId, int dayIndex, int sequence,
                        String fromPlaceId, String toPlaceId, String travelMode,
                        Integer distanceM, Integer durationMin, Integer walkingMeters,
                        Integer ascentM, Integer stairSteps, ItineraryItem.DataStatus dataStatus,
                        Integer fareKrw, List<double[]> path, Instant createdAt) {
        this(itineraryLegId, itineraryVersionId, dayIndex, sequence, fromPlaceId, toPlaceId, travelMode,
                distanceM, durationMin, walkingMeters, ascentM, stairSteps, dataStatus, fareKrw, path, null,
                createdAt);
    }

    /** 경사 조각 칸 이전의 생성자 — 조각 없이 만든 구간이다. 부르는 곳이 여럿이라 한 번에 안 고친다. */
    public ItineraryLeg(String itineraryLegId, String itineraryVersionId, int dayIndex, int sequence,
                        String fromPlaceId, String toPlaceId, String travelMode,
                        Integer distanceM, Integer durationMin, Integer walkingMeters,
                        Integer ascentM, Integer stairSteps, ItineraryItem.DataStatus dataStatus,
                        Integer fareKrw, List<double[]> path, Integer uncalibratedDurationMin, Instant createdAt) {
        this(itineraryLegId, itineraryVersionId, dayIndex, sequence, fromPlaceId, toPlaceId, travelMode,
                distanceM, durationMin, walkingMeters, ascentM, stairSteps, dataStatus, fareKrw, path, null,
                uncalibratedDurationMin, createdAt);
    }

    public ItineraryLeg(String itineraryLegId, String itineraryVersionId, int dayIndex, int sequence,
                        String fromPlaceId, String toPlaceId, String travelMode,
                        Integer distanceM, Integer durationMin, Integer walkingMeters,
                        Integer ascentM, Integer stairSteps, ItineraryItem.DataStatus dataStatus,
                        Integer fareKrw, List<double[]> path, List<Piece> pieces, Integer uncalibratedDurationMin,
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
        if (fareKrw != null && fareKrw < 0) {
            // 음수 요금은 "할인" 이 아니라 자료가 어긋난 것이다. 조용히 통과시키면 하루 합계가
            //    줄어들고, 사람은 그것을 실제 금액으로 읽는다.
            throw new IllegalArgumentException("fareKrw 는 0 이상이어야 한다: " + fareKrw);
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
        this.fareKrw = fareKrw;
        this.path = normalizePath(path);
        // 선형이 없으면 조각이 가리킬 자리가 없다. 빈 목록도 「없다」로 눕힌다 — 선형과 같은 규칙이다.
        this.pieces = (this.path == null || pieces == null || pieces.isEmpty()) ? null : List.copyOf(pieces);
        this.uncalibratedDurationMin = uncalibratedDurationMin;
        this.createdAt = createdAt;
    }

    /**
     * 점이 둘 미만인 선형은 없는 것으로 친다.
     * <p>
     * 점 하나는 선이 아니고, 빈 목록은 뜻이 「없다」인데 저장해 두면 나중에 읽는 쪽이
     * 「길을 재 봤는데 결과가 비었다」로 읽는다. 둘은 다른 사실이라 아예 {@code null} 로
     * 눕힌다 — DB 의 {@code ck_itinerary_leg_path} 도 같은 것을 막는다.
     */
    private static List<double[]> normalizePath(List<double[]> path) {
        return (path == null || path.size() < 2) ? null : List.copyOf(path);
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
    public Integer fareKrw()           { return fareKrw; }
    public Integer uncalibratedDurationMin() { return uncalibratedDurationMin; }

    /** 「어느 길로 가는지」. 모르면 {@code null} — 직선을 대신 넣지 않는다. */
    public List<double[]> path()       { return path; }

    /** 그릴 수 있는 선형이 있는가. */
    public boolean hasPath()           { return path != null; }

    /** 길의 경사·계단 조각. 선형이 없거나 조각을 모르면 {@code null}. */
    public List<Piece> pieces()        { return pieces; }

    public Instant createdAt()         { return createdAt; }
}
