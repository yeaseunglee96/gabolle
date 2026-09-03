package com.gabolle.backend.trip.domain;

/**
 * 여행에 걸린 조건 — ERD 의 {@code TRIP_CONSTRAINTS} 표.
 *
 * <p>🔴 {@link Severity#HARD} 는 점수로 되살릴 수 없다. 알레르기·필수 식단·검증된
 * 접근 불가는 점수 계산 <b>이전에</b> 걸러지고, 아무리 좋은 후보여도 다시 올라오지 않는다
 * (요구사항 3장 · 온톨로지 1장).
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

    public TripConstraint(String constraintId, String tripId, String type, Severity severity,
                          String operator, String value, Double threshold,
                          EvidenceStatus evidenceStatus) {

        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("제약 종류는 필수다");
        }
        if (severity == null) {
            throw new IllegalArgumentException("HARD 인지 SOFT 인지 정해야 한다: " + type);
        }
        if (isSensitiveType(type) && value != null && !value.isBlank()) {
            // 🔴 M1 에서는 암호화 경로가 없다. 평문으로 받으면 그대로 남는다.
            throw new SensitiveConstraintNotSupportedException(type);
        }
        if (severity == Severity.HARD && operator == null) {
            // 반드시 지켜야 하는 조건인데 비교 방법이 없으면 판정할 수 없다.
            throw new IllegalArgumentException("HARD 제약에는 비교 방법(operator)이 필요하다: " + type);
        }

        this.constraintId = constraintId;
        this.tripId = tripId;
        this.type = type;
        this.severity = severity;
        this.operator = operator;
        this.value = value;
        this.threshold = threshold;
        this.evidenceStatus = evidenceStatus != null ? evidenceStatus : EvidenceStatus.NEEDS_REVIEW;
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

    public String constraintId()          { return constraintId; }
    public String tripId()                { return tripId; }
    public String type()                  { return type; }
    public Severity severity()            { return severity; }
    public String operator()              { return operator; }
    public String value()                 { return value; }
    public Double threshold()             { return threshold; }
    public EvidenceStatus evidenceStatus(){ return evidenceStatus; }
}
