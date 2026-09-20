package com.gabolle.backend.trip.domain;

import java.time.Instant;
import java.util.List;

/**
 * 여행이 만들어질 때 복사해 둔 명시 선호. 불변이다 — 계정 취향은 계속 바뀌지만 이미 만든 일정은
 * 그때의 취향으로 만들어진 것이라, 복사해 두지 않으면 나중에 "왜 이 일정이 나왔는지" 를 답할 수 없다.
 *
 * <p>{@code snapshotId}(UUID)는 DB PK·분석 조인용이고 {@code version}(정수)은 API 가 주고받는
 * 값이다. "내가 본 판이 최신인가" 는 {@code version} 으로만 판정할 수 있다 — UUID 에는 순서가 없다.
 */
public class PreferenceSnapshot {

    private final String snapshotId;

    private final String tripId;

    /** 여행 안에서 1부터 증가한다. */
    private final int version;

    /** 차원마다 하나. 순서는 의미가 없다. */
    private final List<PreferenceAnswer> answers;

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
        // 밖에서 넘긴 List 를 그대로 들고 있으면 나중에 바뀐다.
        this.answers = answers == null ? List.of() : List.copyOf(answers);
        this.scope = scope;
        this.constraintIds = constraintIds == null ? List.of() : List.copyOf(constraintIds);
        this.createdAt = createdAt;
    }

    /** 기존 판을 고치지 않는다. 고치면 그 판으로 만든 일정의 근거가 사라진다. */
    public PreferenceSnapshot next(String newSnapshotId, List<PreferenceAnswer> newAnswers,
                                   List<String> newConstraintIds, Instant at) {
        return new PreferenceSnapshot(newSnapshotId, tripId, version + 1,
                newAnswers, scope, newConstraintIds, at);
    }

    /**
     * 취향 답 하나. {@code valueJson} 은 {@code status == SELECTED} 일 때만 있다. 구조 대신 JSON
     * 문자열로 두는 것은 차원마다 값의 모양이 달라서다(단일 코드·다중 선택·척도값) — 파싱은 값을
     * 소비하는 쪽이 차원별로 안다.
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
            // ck_preference_answer_value_matches_status 를 그대로 옮긴 제약이다.
            if ((status == AnswerStatus.SELECTED) != hasValue) {
                throw new IllegalArgumentException(
                        "status=" + status + " 인데 값 유무가 안 맞는다(valueJson=" + valueJson + "): " + dimension);
            }
        }
    }

    /** 취향은 제약과 다르게 건너뛰기(SKIPPED)를 허용한다 — {@link TripConstraint.AnswerStatus} 에는 없는 값이다. */
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
