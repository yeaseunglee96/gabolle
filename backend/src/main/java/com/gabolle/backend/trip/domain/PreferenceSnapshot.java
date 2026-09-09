package com.gabolle.backend.trip.domain;

import java.time.Instant;
import java.util.List;

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
 *
 * <h2>🔴 2026-09-03 — dimensions(Map)를 answers(List)로 (고지혁 님 실측)</h2>
 * {@code Map<String, String>} 은 "이 차원에 이 값이 있다" 와 "키가 없다" 둘만
 * 말할 수 있었다. 그런데 -542 2.1 이 구분하라는 것은 셋이다 — 골랐다(SELECTED) ·
 * 봤지만 건너뜀(SKIPPED) · 아예 안 물어봄(UNKNOWN). 뒤의 둘이 똑같이 "키 없음" 으로
 * 뭉개지면 <b>"몇 명이 건너뛰었나" 를 영영 못 센다.</b> {@link PreferenceAnswer} 가
 * 그 상태를 차원마다 따로 갖는다.
 */
public class PreferenceSnapshot {

    /** 내부 참조·분석 조인용. */
    private final String snapshotId;

    private final String tripId;

    /** 🔴 API 가 주고받는 값. 여행 안에서 1부터 증가한다. */
    private final int version;

    /** 차원마다 하나. 순서는 의미가 없다. */
    private final List<PreferenceAnswer> answers;

    /** 계정 기본값인가 이번 여행 전용인가 (S15P21E201-542 2.2). */
    private final PersonalizationScope scope;

    /** 이 스냅샷을 만들 때 함께 굳힌 제약 식별자들. */
    private final List<String> constraintIds;

    private final Instant createdAt;

    public PreferenceSnapshot(String snapshotId, String tripId, int version,
                              List<PreferenceAnswer> answers, PersonalizationScope scope,
                              List<String> constraintIds, Instant createdAt) {
        if (version < 1) {
            throw new IllegalArgumentException("스냅샷 판 번호는 1 이상이어야 한다: " + version);
        }
        if (scope == null) {
            throw new IllegalArgumentException("scope(USER/TRIP)는 필수다");
        }
        this.snapshotId = snapshotId;
        this.tripId = tripId;
        this.version = version;
        // 🔴 복사본을 만든다. 밖에서 넘긴 List 를 그대로 들고 있으면 나중에 바뀐다.
        this.answers = answers == null ? List.of() : List.copyOf(answers);
        this.scope = scope;
        this.constraintIds = constraintIds == null ? List.of() : List.copyOf(constraintIds);
        this.createdAt = createdAt;
    }

    /**
     * 다음 판을 만든다 (TRIP-07).
     *
     * <p>🔴 기존 판을 고치지 않는다. 고치면 그 판으로 만든 일정의 근거가 사라진다.
     */
    public PreferenceSnapshot next(String newSnapshotId, List<PreferenceAnswer> newAnswers,
                                   List<String> newConstraintIds, Instant at) {
        return new PreferenceSnapshot(newSnapshotId, tripId, version + 1,
                newAnswers, scope, newConstraintIds, at);
    }

    /**
     * 취향 답 하나 — {@code preference_answer} 표의 한 행.
     *
     * <p>🔴 {@code valueJson} 은 {@code (status == SELECTED) 일 때만} 있다. 값을
     * {@code Map<String,Object>} 가 아니라 JSON 문자열로 두는 이유 — 차원마다 값의
     * 모양이 다르다(단일 코드·다중 선택·척도값). 여기서 구조를 강제하면 그중 하나만
     * 편해지고 나머지가 억지로 끼워 맞춰진다. 실제 파싱은 값을 소비하는 쪽(추천 엔진)
     * 이 차원별로 안다.
     */
    public record PreferenceAnswer(String dimension, String valueJson, AnswerStatus status) {

        public PreferenceAnswer {
            if (dimension == null || dimension.isBlank()) {
                throw new IllegalArgumentException("차원 이름은 필수다");
            }
            if (status == null) {
                throw new IllegalArgumentException("답변 상태(SELECTED/SKIPPED/UNKNOWN)는 필수다: " + dimension);
            }
            boolean hasValue = valueJson != null && !valueJson.isBlank();
            // constraint_answer 와 같은 모양의 제약 —
            // ck_preference_answer_value_matches_status 를 그대로 옮겼다.
            if ((status == AnswerStatus.SELECTED) != hasValue) {
                throw new IllegalArgumentException(
                        "status=" + status + " 인데 값 유무가 안 맞는다(valueJson=" + valueJson + "): " + dimension);
            }
        }
    }

    /**
     * 취향은 제약과 다르게 건너뛰기(SKIPPED)를 허용한다 (-542 2.1) —
     * {@link TripConstraint.AnswerStatus} 에는 없는 값이다.
     */
    public enum AnswerStatus {
        /** valueJson 이 있다 */
        SELECTED,
        /** 화면에 나왔지만 사용자가 건너뛰었다. valueJson 없음 */
        SKIPPED,
        /** 아예 안 물어봤다. valueJson 없음 */
        UNKNOWN
    }

    public String snapshotId()                  { return snapshotId; }
    public String tripId()                      { return tripId; }
    public int version()                        { return version; }
    public List<PreferenceAnswer> answers()     { return answers; }
    public PersonalizationScope scope()         { return scope; }
    public List<String> constraintIds()         { return constraintIds; }
    public Instant createdAt()                  { return createdAt; }
}
