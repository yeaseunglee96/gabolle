package com.gabolle.backend.trip.domain;

/**
 * 여행에 걸린 조건. {@link Severity#HARD} 는 점수로 되살릴 수 없다 — 알레르기·필수 식단·검증된
 * 접근 불가는 점수 계산 이전에 걸러지고 아무리 좋은 후보여도 다시 올라오지 않는다.
 *
 * <p>{@link AnswerStatus} 로 "없다고 답했다"(NONE)와 "안 물어봤다"(UNKNOWN)를 가른다. 알레르기에서
 * 이건 안전 문제다 — 안 물어본 것을 "없다" 로 읽으면 위반 장소가 통과한다. 민감 여부는 type 이
 * 아니라 {@link #isSensitive(String, DietRequirement)} 가 type 과 {@link DietRequirement} 를
 * 함께 보고 정한다.
 */
public class TripConstraint {

    private final String constraintId;
    private final String tripId;

    /** {@code ALLERGY} · {@code DIET} · {@code MOBILITY} · {@code BUDGET} */
    private final String type;

    /**
     * 종류 안에서 무엇에 대한 사실인가 — 알레르기 코드({@code PEANUT}), 식단 코드({@code HALAL}),
     * 이동 조건 이름({@code MAX_WALKING_METERS}). 자유 입력이면 {@code "OTHER"} 다. 값 목록은
     * {@code constraint_answer.constraint_key} 마이그레이션이 정한다.
     */
    private final String constraintKey;

    private final Severity severity;

    /** {@code EXCLUDES} · {@code LTE} · {@code GTE} */
    private final String operator;

    /**
     * 민감 종류의 자유 입력은 여기 넣지 않는다. 그 원문의 자리는 암호화 전용 칸
     * ({@code other_allergy_ciphertext})이고 아직 그 경로가 없다. 코드로 된 민감 값은 코드 자체가
     * 정보 전부라 여기 담을 것이 없다.
     */
    private final String value;

    private final Double threshold;

    /** 추정·미확인·검증됨을 구분한다. 모르는 것을 안다고 하지 않는다. */
    private final EvidenceStatus evidenceStatus;

    /** 골랐다(SELECTED) · 없다고 답했다(NONE) · 안 물어봤다(UNKNOWN). */
    private final AnswerStatus answerStatus;

    private final PersonalizationScope scope;

    /** type == DIET 일 때만 뜻이 있다. ALLERGY·MOBILITY 에는 항상 null 이다. */
    private final DietRequirement dietRequirement;

    public TripConstraint(String constraintId, String tripId, String type, String constraintKey,
                          Severity severity, String operator, String value, Double threshold,
                          EvidenceStatus evidenceStatus, AnswerStatus answerStatus,
                          PersonalizationScope scope, DietRequirement dietRequirement) {

        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("제약 종류는 필수다");
        }
        if (constraintKey == null || constraintKey.isBlank()) {
            throw new IllegalArgumentException("constraintKey 는 필수다: " + type);
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
        if (!"DIET".equalsIgnoreCase(type) && dietRequirement != null) {
            throw new IllegalArgumentException("dietRequirement 는 DIET 에만 있을 수 있다: " + type);
        }
        // 자유 입력(OTHER)만 막는다. 코드로 된 민감 값은 구조화된 정보라 그대로 통과한다.
        if (isSensitive(type, dietRequirement) && "OTHER".equalsIgnoreCase(constraintKey)) {
            throw new SensitiveConstraintNotSupportedException(type);
        }
        if (isSensitive(type, dietRequirement) && severity != Severity.HARD) {
            throw new IllegalArgumentException("알레르기·필수 식단은 항상 HARD 여야 한다: " + type);
        }
        if (severity == Severity.HARD && operator == null) {
            throw new IllegalArgumentException("HARD 제약에는 비교 방법(operator)이 필요하다: " + type);
        }
        boolean hasAnyValue = (value != null && !value.isBlank()) || threshold != null;
        if (answerStatus != AnswerStatus.SELECTED && hasAnyValue) {
            // 안 고른 답은 어느 종류든 값을 실을 수 없다.
            throw new IllegalArgumentException(
                    "answerStatus=" + answerStatus + " 인데 값이 있다"
                            + "(value=" + value + ", threshold=" + threshold + "): " + type);
        }
        if ("MOBILITY".equalsIgnoreCase(type) && answerStatus == AnswerStatus.SELECTED && !hasAnyValue) {
            // 값 요구는 MOBILITY 에만 있다. ALLERGY·DIET 는 constraintKey 가 정보 전부라
            // value 를 요구하면 억지로 채우게 된다.
            throw new IllegalArgumentException(
                    "MOBILITY 는 SELECTED 면 값(또는 임계치)이 있어야 한다: " + type);
        }

        this.constraintId = constraintId;
        this.tripId = tripId;
        this.type = type;
        this.constraintKey = constraintKey;
        this.severity = severity;
        this.operator = operator;
        this.value = value;
        this.threshold = threshold;
        this.evidenceStatus = evidenceStatus != null ? evidenceStatus : EvidenceStatus.NEEDS_REVIEW;
        this.answerStatus = answerStatus;
        this.scope = scope;
        this.dietRequirement = dietRequirement;
    }

    /**
     * 민감정보를 담는 제약인가. 의료·종교상 필수 식단은 별도 type 이 아니라 DIET +
     * {@link DietRequirement#REQUIRED} 이므로 type 만 보면 놓친다. 여기 걸려도 실제로 막히는 것은
     * constraintKey 가 자유 입력("OTHER")일 때뿐이다.
     */
    public static boolean isSensitive(String type, DietRequirement dietRequirement) {
        return "ALLERGY".equalsIgnoreCase(type)
                || ("DIET".equalsIgnoreCase(type) && dietRequirement == DietRequirement.REQUIRED);
    }

    public enum Severity {
        /** 반드시 지킨다. 위반 후보는 점수 계산 전에 제거된다 */
        HARD,
        /** 가급적 지킨다. 점수에 반영된다 */
        SOFT
    }

    public enum EvidenceStatus {
        VERIFIED, PARTIAL, ESTIMATED, NEEDS_REVIEW, UNAVAILABLE
    }

    /**
     * 제약은 건너뛰기(SKIPPED)를 허용하지 않는다 — 알레르기에서 "봤지만 건너뜀" 은 허용할 수 없는
     * 상태다. 대신 명시적으로 "없다"(NONE)를 요구한다.
     */
    public enum AnswerStatus {
        /** 값(또는 임계치)이 있다 */
        SELECTED,
        /** 물어봤고, 없다고 답했다. value·threshold 없음 */
        NONE,
        /** 안 물어봤다. value·threshold 없음 */
        UNKNOWN
    }

    /**
     * 의료·종교상 반드시 지켜야 하는가, 선호인가. 같은 코드(예: VEGETARIAN)가 사람에 따라 둘 다
     * 될 수 있어서 민감 여부는 코드가 아니라 이 값이 정한다.
     */
    public enum DietRequirement {
        REQUIRED, PREFERRED
    }

    public static class SensitiveConstraintNotSupportedException extends RuntimeException {
        private final String type;

        public SensitiveConstraintNotSupportedException(String type) {
            super("민감 제약(" + type + ")의 자유 입력은 암호화 경로가 준비될 때까지 받지 않는다. "
                    + "평문으로 저장하면 그 데이터가 남는다");
            this.type = type;
        }

        public String type() { return type; }
    }

    public String constraintId()               { return constraintId; }
    public String tripId()                     { return tripId; }
    public String type()                       { return type; }
    public String constraintKey()               { return constraintKey; }
    public Severity severity()                 { return severity; }
    public String operator()                   { return operator; }
    public String value()                      { return value; }
    public Double threshold()                  { return threshold; }
    public EvidenceStatus evidenceStatus()     { return evidenceStatus; }
    public AnswerStatus answerStatus()         { return answerStatus; }
    public PersonalizationScope scope()        { return scope; }
    public DietRequirement dietRequirement()   { return dietRequirement; }
}
