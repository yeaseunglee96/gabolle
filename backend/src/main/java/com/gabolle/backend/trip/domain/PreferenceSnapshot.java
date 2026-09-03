package com.gabolle.backend.trip.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 여행이 만들어질 때 복사해 둔 명시 선호 — 🔴 <b>불변</b>이다.
 *
 * <h2>왜 복사하나</h2>
 * 계정 취향은 계속 바뀐다. 그런데 <b>이미 만든 일정은 그때의 취향으로 만들어진 것</b>이다.
 * 복사해 두지 않으면 3주 뒤에 "왜 이 일정이 나왔지" 를 물었을 때 답할 수 없다.
 *
 * <pre>
 * 계정 취향  "조용한 곳"
 *      │ 여행 생성 시 복사
 *      ▼
 * 스냅샷 v1  "조용한 곳"   ← 이 여행은 영원히 이걸로 만들어졌다
 *      │
 *      │ 나중에 계정 취향을 "북적이는 곳" 으로 바꿔도
 *      ▼
 * 스냅샷은 안 바뀐다
 * </pre>
 *
 * 온톨로지가 {@code PreferenceSnapshot} 을 "계정 기본값을 여행 생성 시 복사한 명시 선호"
 * 로 정의한다. NFR-08 재현성의 뿌리다.
 *
 * <h2>🔴 왜 UUID 와 version 을 둘 다 두나</h2>
 * <table>
 *   <tr><th>쓰이는 곳</th><th>무엇으로</th></tr>
 *   <tr><td>DB PK · 분석 마트 조인 (S15P21E201-542 14장)</td><td>{@code snapshotId} (UUID)</td></tr>
 *   <tr><td>API (REC-01 {@code preferenceSnapshotVersion} · TRIP-07)</td><td>{@code version} (정수)</td></tr>
 *   <tr><td>"내가 본 판이 최신인가" 판정</td><td>{@code version} — UUID 에는 순서가 없다</td></tr>
 * </table>
 *
 * <p>이미 머지된 {@code itinerary_versions} 와 같은 패턴이다 —
 * UUID PK + 여행 안에서 유일한 정수 버전.
 */
public class PreferenceSnapshot {

    /** 내부 참조·분석 조인용. */
    private final String snapshotId;

    private final String tripId;

    /** 🔴 API 가 주고받는 값. 여행 안에서 1부터 증가한다. */
    private final int version;

    /** 취향 차원 → 값. 예: {@code {"pace":"RELAXED","theme":"NATURE"}} */
    private final Map<String, String> dimensions;

    /** 이 스냅샷을 만들 때 함께 굳힌 제약 식별자들. */
    private final List<String> constraintIds;

    private final Instant createdAt;

    public PreferenceSnapshot(String snapshotId, String tripId, int version,
                              Map<String, String> dimensions, List<String> constraintIds,
                              Instant createdAt) {
        if (version < 1) {
            throw new IllegalArgumentException("스냅샷 판 번호는 1 이상이어야 한다: " + version);
        }
        this.snapshotId = snapshotId;
        this.tripId = tripId;
        this.version = version;
        // 🔴 복사본을 만든다. 밖에서 넘긴 Map 을 그대로 들고 있으면 나중에 바뀐다.
        this.dimensions = dimensions == null ? Map.of() : Map.copyOf(dimensions);
        this.constraintIds = constraintIds == null ? List.of() : List.copyOf(constraintIds);
        this.createdAt = createdAt;
    }

    /**
     * 다음 판을 만든다 (TRIP-07).
     *
     * <p>🔴 기존 판을 고치지 않는다. 고치면 그 판으로 만든 일정의 근거가 사라진다.
     */
    public PreferenceSnapshot next(String newSnapshotId, Map<String, String> newDimensions,
                                   List<String> newConstraintIds, Instant at) {
        return new PreferenceSnapshot(newSnapshotId, tripId, version + 1,
                newDimensions, newConstraintIds, at);
    }

    public String snapshotId()              { return snapshotId; }
    public String tripId()                  { return tripId; }
    public int version()                    { return version; }
    public Map<String, String> dimensions() { return dimensions; }
    public List<String> constraintIds()     { return constraintIds; }
    public Instant createdAt()              { return createdAt; }
}
