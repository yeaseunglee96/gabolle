package com.gabolle.backend.trip.domain;

/**
 * 여행에 걸린 조건 — ERD 의 {@code TRIP_CONSTRAINTS} 표.
 *
 * <p>🔴 {@link Severity#HARD} 는 점수로 되살릴 수 없다. 알레르기·필수 식단·검증된
 * 접근 불가는 점수 계산 <b>이전에</b> 걸러지고, 아무리 좋은 후보여도 다시 올라오지 않는다
 * (요구사항 3장 · 온톨로지 1장).
 *
 * <h2>🔴 2026-09-03 — answerStatus·scope 추가 (고지혁 님 실측)</h2>
 * 이 필드 둘이 없으면 "알레르기 없음"(NONE)과 "안 물어봄"(UNKNOWN)을 구분할 수
 * 없었다 — 둘 다 그냥 행이 없는 것으로 보였다. 알레르기에서 이건 분석 문제가
 * 아니라 안전 문제다 — 안 물어본 것을 "없다" 로 읽으면 위반 장소가 통과한다.
 * constraint_answer.answer_status 가 이미 이 셋을 요구하고 있었다.
 *
 * <h2>🔴 2026-09-04 — dietRequirement 추가, HEALTH_DIET 판정 구멍 수정 (고지혁 님 실측)</h2>
 * HEALTH_DIET 는 DB 에 존재한 적이 없는 타입이었다 — 명세(-542 5.2)는 그
 * 구분을 type 이 아니라 diet_requirement 로 한다(DIET + REQUIRED = 의료·종교상
 * 필수, DIET + PREFERRED = 선호). isSensitive(type, dietRequirement) 가 그 둘을
 * 함께 본다.
 *
 * <h2>🔴 2026-09-04 — constraintKey 추가, 알레르기를 아예 못 적던 문제 수정 (고지혁 님 리뷰)</h2>
 * {@code constraintKey} 없이는 -542 10장("일반 로그와 분리하고 접근을 통제한다")을
 * "값을 아예 안 받는다" 로 잘못 읽어서, 코드로 된 알레르기(예: {@code PEANUT})까지
 * 자유 입력과 똑같이 막고 있었다. 그 결과 알레르기를 "있다" 로 저장할 방법이 없어
 * -539(알레르기 위반 장소 배제, M1)가 애초에 동작할 수 없었다. {@code constraintKey}
 * 가 {@code "OTHER"}(자유 입력)인 것만 막는다 — 코드로 된 값은 구조화된 정보라
 * 암호화 없이도 안전하다.
 */
public class TripConstraint {

    private final String constraintId;
    private final String tripId;

    /** {@code ALLERGY} · {@code DIET} · {@code MOBILITY} · {@code BUDGET} */
    private final String type;

    /**
     * 🔴 종류 안에서 무엇에 대한 사실인가. {@code ALLERGY} 라면 알레르기 코드
     * ({@code PEANUT} 등, 자유 입력이면 {@code "OTHER"}), {@code DIET} 라면 식단
     * 코드({@code HALAL} 등), {@code MOBILITY} 라면 이동 조건 이름
     * ({@code MAX_WALKING_METERS} 등). {@code constraint_answer.constraint_key}
     * (V120000)와 짝이다 — 값 목록 자체는 그 마이그레이션이 정한다.
     */
    private final String constraintKey;

    private final Severity severity;

    /** {@code EXCLUDES} · {@code LTE} · {@code GTE} */
    private final String operator;

    /**
     * 🔴 자유 입력(민감 종류의 {@code constraintKey == "OTHER"})은 여기 넣지 않는다.
     *
     * <p>코드로 된 민감 값(예: {@code ALLERGY}+{@code PEANUT})은 <b>구조화된 정보라
     * 여기 담을 것이 없다</b> — 코드 자체가 정보 전부다. {@code value} 는 자유 입력
     * 원문에만 필요한데, 그 자리는 암호화 전용 칸({@code other_allergy_ciphertext})
     * 이고 M1 에는 그 경로가 없다. {@link #isSensitive(String, DietRequirement)} 참고.
     */
    private final String value;

    private final Double threshold;

    /** 🔴 NFR-09 — 추정·미확인·검증됨을 구분한다. 모르는 것을 안다고 하지 않는다. */
    private final EvidenceStatus evidenceStatus;

    /** 골랐다(SELECTED) · 없다고 답했다(NONE) · 안 물어봤다(UNKNOWN). */
    private final AnswerStatus answerStatus;

    /** 계정 기본값인가 이번 여행 전용인가 (S15P21E201-542 2.2). */
    private final PersonalizationScope scope;

    /**
     * 🔴 type == DIET 일 때만 뜻이 있다. 의료·종교상 반드시 지켜야 하는
     * 것(REQUIRED)인지 선호(PREFERRED)인지 — 이 값이 민감 여부를 가른다(위 클래스
     * 주석 참고). ALLERGY·MOBILITY 에는 항상 null 이다.
     */
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
        // 🔴 2026-09-04 — 자유 입력(OTHER)만 막는다. 코드로 된 민감 값은 구조화된
        //    정보라 그대로 통과한다(고지혁 님 리뷰, 위 클래스 주석 참고).
        if (isSensitive(type, dietRequirement) && "OTHER".equalsIgnoreCase(constraintKey)) {
            throw new SensitiveConstraintNotSupportedException(type);
        }
        if (isSensitive(type, dietRequirement) && severity != Severity.HARD) {
            // 🔴 알레르기·필수 식단은 소프트 취향이 아니라 하드 제약이다 (2026-09-03 진미리 합의).
            throw new IllegalArgumentException("알레르기·필수 식단은 항상 HARD 여야 한다: " + type);
        }
        if (severity == Severity.HARD && operator == null) {
            throw new IllegalArgumentException("HARD 제약에는 비교 방법(operator)이 필요하다: " + type);
        }
        boolean hasAnyValue = (value != null && !value.isBlank()) || threshold != null;
        if (answerStatus != AnswerStatus.SELECTED && hasAnyValue) {
            // 🔴 !150(고지혁 님)과 맞춘다 — 안 고른 답은 어느 종류든 값을 실을 수 없다.
            throw new IllegalArgumentException(
                    "answerStatus=" + answerStatus + " 인데 값이 있다"
                            + "(value=" + value + ", threshold=" + threshold + "): " + type);
        }
        if ("MOBILITY".equalsIgnoreCase(type) && answerStatus == AnswerStatus.SELECTED && !hasAnyValue) {
            // 🔴 2026-09-04 — 값 요구를 MOBILITY 로 좁혔다. ALLERGY·DIET 는 코드
            //    (constraintKey)가 정보 전부라 value 를 요구하면 억지로 채우게 된다
            //    (고지혁 님 리뷰 — !150 의 ck_constraint_answer_mobility_key 와 짝).
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
     * 민감정보를 담는 제약인가.
     *
     * <p>🔴 2026-09-04 이전에는 type 문자열만 보고 "HEALTH_DIET" 를 찾았는데, DB 에는
     * 그런 타입이 없다 — 실제로는 DIET 인데 diet_requirement=REQUIRED 인 것이 그
     * 자리다(고지혁 님 실측). type 만 보면 의료·종교상 필수 식단이 그대로 걸러지지
     * 않고 저장된다.
     *
     * <p>여기 걸리는 것은 constraintKey 가 자유 입력("OTHER")일 때만 실제로
     * 저장을 막힌다 — 코드로 된 값은 구조화된 정보라 안전하다(생성자 참고).
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

    /**
     * type == DIET 일 때만 뜻이 있다 — 의료·종교상 반드시 지켜야 하는가,
     * 선호인가 (-542 5.2). 같은 코드(예: VEGETARIAN)가 사람에 따라 둘 다 될
     * 수 있어서, 민감 여부는 코드가 아니라 이 값이 정한다(고지혁 님 실측).
     */
    public enum DietRequirement {
        REQUIRED, PREFERRED
    }

    /** M1 에서 민감한 자유 입력을 받으려 할 때. 400 으로 응답한다. */
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
