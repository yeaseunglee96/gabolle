package com.gabolle.backend.trip.domain;

/**
 * 여행에 걸린 조건 — ERD 의 {@code TRIP_CONSTRAINTS} 표.
 *
 * <p>🔴 {@link Severity#HARD} 는 점수로 되살릴 수 없다. 알레르기·필수 식단·검증된
 * 접근 불가는 점수 계산 <b>이전에</b> 걸러지고, 아무리 좋은 후보여도 다시 올라오지 않는다
 * (요구사항 3장 · 온톨로지 1장).
 *
 * <h2>🔴 2026-09-03 — answerStatus·scope 추가 (고지혁 님 실측)</h2>
 * 이 필드 둘이 없으면 <b>"알레르기 없음"(NONE)과 "안 물어봄"(UNKNOWN)을 구분할 수
 * 없었다</b> — 둘 다 그냥 행이 없는 것으로 보였다. 알레르기에서 이건 분석 문제가
 * 아니라 안전 문제다 — 안 물어본 것을 "없다" 로 읽으면 위반 장소가 통과한다.
 * {@code constraint_answer.answer_status} 가 이미 이 셋을 요구하고 있었다.
 */
public class TripConstraint {

    private final String constraintId;
    private final String tripId;

    /** {@code ALLERGY} · {@code DIET} · {@code MOBILITY} · {@code BUDGET} */
    private final String type;

    private final Severity severity;

    /** {@code EXCLUDES} · {@code LTE} · {@code GTE} */
    private final String operator;

    /**
     * 🔴 민감한 값은 여기 넣지 않는다.
     *
     * <p>ERD 는 {@code encrypted_value} 컬럼을 두었다. 알레르기·건강 식단은
     * "일반 행동 로그와 분리하고 접근을 통제한다"(요구사항 3장 · S15P21E201-542 10장).
     *
     * <p>🔴 <b>M1 에서는 암호화 키 관리가 정해지지 않았으므로 민감 종류를 아예 받지 않는다.</b>
     * 평문으로 한 번 저장하면 그 데이터가 남는다. {@link #isSensitiveType(String)} 참고.
     */
    private final String value;

    private final Double threshold;

    /** 🔴 NFR-09 — 추정·미확인·검증됨을 구분한다. 모르는 것을 안다고 하지 않는다. */
    private final EvidenceStatus evidenceStatus;

    /** 골랐다(SELECTED) · 없다고 답했다(NONE) · 안 물어봤다(UNKNOWN). */
    private final AnswerStatus answerStatus;

    /** 계정 기본값인가 이번 여행 전용인가 (S15P21E201-542 2.2). */
    private final PersonalizationScope scope;

    public TripConstraint(String constraintId, String tripId, String type, Severity severity,
                          String operator, String value, Double threshold,
                          EvidenceStatus evidenceStatus, AnswerStatus answerStatus,
                          PersonalizationScope scope) {

        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("제약 종류는 필수다");
        }
        if (severity == null) {
            throw new IllegalArgumentException("HARD 인지 SOFT 인지 정해야 한다: " + type);
        }
        if (answerStatus == null) {
            throw new IllegalArgumentException("답변 상태(SELECTED/NONE/UNKNOWN)는 필수다: " + type);
        }
        if (scope == null) {
            throw new IllegalArgumentException("scope(USER/TRIP)는 필수다: " + type);
        }
        if (isSensitiveType(type) && value != null && !value.isBlank()) {
            // 🔴 M1 에서는 암호화 경로가 없다. 평문으로 받으면 그대로 남는다.
            throw new SensitiveConstraintNotSupportedException(type);
        }
        // 🔴 알레르기는 소프트 취향이 아니라 하드 제약이다 (2026-09-03 진미리 합의).
        //    hard=false 인 알레르기를 허용하면 점수 계산에 섞여 "덜 좋아함" 으로
        //    취급된 땅콩이 결과에 남는다. 안전 문제라 여기서 막는다.
        if (isSensitiveType(type) && severity != Severity.HARD) {
            throw new IllegalArgumentException("알레르기·건강 식단은 항상 HARD 여야 한다: " + type);
        }
        if (severity == Severity.HARD && operator == null) {
            // 반드시 지켜야 하는 조건인데 비교 방법이 없으면 판정할 수 없다.
            throw new IllegalArgumentException("HARD 제약에는 비교 방법(operator)이 필요하다: " + type);
        }
        // 🔴 골랐다면(SELECTED) 값이나 임계치 중 하나는 있어야 하고, 안 골랐다면
        //    (NONE/UNKNOWN) 값도 임계치도 없어야 한다. constraint_answer 의
        //    ck_constraint_answer_value_matches_status 를 그대로 옮긴 것이다.
        //    "값" 은 문자열(value)뿐 아니라 숫자 임계치(threshold)로도 실릴 수 있어서
        //    (MOBILITY 의 LTE 5000 처럼) 둘 중 하나만 있어도 SELECTED 로 본다.
        boolean hasAnyValue = (value != null && !value.isBlank()) || threshold != null;
        if ((answerStatus == AnswerStatus.SELECTED) != hasAnyValue) {
            throw new IllegalArgumentException(
                    "answerStatus=" + answerStatus + " 인데 값 유무가 안 맞는다"
                            + "(value=" + value + ", threshold=" + threshold + "): " + type);
        }

        this.constraintId = constraintId;
        this.tripId = tripId;
        this.type = type;
        this.severity = severity;
        this.operator = operator;
        this.value = value;
        this.threshold = threshold;
        this.evidenceStatus = evidenceStatus != null ? evidenceStatus : EvidenceStatus.NEEDS_REVIEW;
        this.answerStatus = answerStatus;
        this.scope = scope;
    }

    /**
     * 민감정보를 담는 제약 종류인가.
     *
     * <p>🔴 여기 걸린 종류는 M1 에서 값을 받지 않는다. 암호화 키 관리가 정해지면
     * {@code encrypted_value} 로 저장하도록 연다.
     */
    public static boolean isSensitiveType(String type) {
        return "ALLERGY".equalsIgnoreCase(type) || "HEALTH_DIET".equalsIgnoreCase(type);
    }

    public enum Severity {
        /** 반드시 지킨다. 위반 후보는 점수 계산 전에 제거된다 */
        HARD,
        /** 가급적 지킨다. 점수에 반영된다 */
        SOFT
    }

    /** 온톨로지 2장이 정한 증거 상태. */
    public enum EvidenceStatus {
        VERIFIED, PARTIAL, ESTIMATED, NEEDS_REVIEW, UNAVAILABLE
    }

    /**
     * 이 제약에 실제로 답했는가.
     *
     * <p>🔴 제약은 건너뛰기(SKIPPED)를 허용하지 않는다 — "봤지만 건너뜀" 은 알레르기에서
     * 허용할 수 없는 상태다. 대신 명시적으로 "없다"(NONE)를 요구한다.
     */
    public enum AnswerStatus {
        /** 값(또는 임계치)이 있다 */
        SELECTED,
        /** 물어봤고, 없다고 답했다. value·threshold 없음 */
        NONE,
        /** 안 물어봤다. value·threshold 없음 */
        UNKNOWN
    }

    /** M1 에서 민감 제약을 받으려 할 때. 400 으로 응답한다. */
    public static class SensitiveConstraintNotSupportedException extends RuntimeException {
        private final String type;

        public SensitiveConstraintNotSupportedException(String type) {
            super("민감 제약(" + type + ")은 암호화 경로가 준비될 때까지 받지 않는다. "
                    + "평문으로 저장하면 그 데이터가 남는다");
            this.type = type;
        }

        public String type() { return type; }
    }

    public String constraintId()               { return constraintId; }
    public String tripId()                     { return tripId; }
    public String type()                       { return type; }
    public Severity severity()                 { return severity; }
    public String operator()                   { return operator; }
    public String value()                      { return value; }
    public Double threshold()                  { return threshold; }
    public EvidenceStatus evidenceStatus()     { return evidenceStatus; }
    public AnswerStatus answerStatus()         { return answerStatus; }
    public PersonalizationScope scope()        { return scope; }
}
