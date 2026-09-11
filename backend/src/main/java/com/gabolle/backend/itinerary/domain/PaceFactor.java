package com.gabolle.backend.itinerary.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 계획 대비 실제 시간의 배수 판 하나 — S15P21E201-304.
 *
 * <p>{@code user_taste_vector}(판 체인의 정본, {@code preference} 모듈)와 같은 이유로 판을
 * 고치지 않고 새로 만든다. 과거에 "20분 늦어집니다" 라고 답한 계산이 <b>어느 계수로</b>
 * 나왔는지 되짚을 수 있어야 하는데, 같은 행을 덮어쓰면 그 답을 오늘의 계수로 설명하게
 * 되고 그건 설명이 아니라 지어내기다.
 *
 * @param version 몇 번째 판인가. UUID 에는 순서가 없어 이 정수가 대신 답한다
 * @param factor 계획 대비 배수. 1.30 이면 계획의 1.3배 걸린다
 * @param sampleCount 이 계수를 만든 표본 수. 계수와 항상 함께 다닌다 — 3건짜리와 30건짜리
 *     1.30 은 같은 확신이 아니다
 * @param observedUntil 이 판이 반영한 기록 중 가장 나중 시각. 다음 판을 계산할 때 여기서부터
 *     이어 붙인다
 * @param supersededAt 비어 있으면 이 판이 지금 쓰는 판이다. 다음 판이 생기면 찍힌다 —
 *     행은 지우지 않는다
 */
public record PaceFactor(String userPaceFactorId, String userId, int version,
        BigDecimal factor, int sampleCount, Instant observedUntil,
        Instant createdAt, Instant supersededAt) {

    /**
     * 이만큼은 모여야 계수를 만든다. 마이그레이션의 {@code ck_user_pace_factor_samples} 와
     * 같은 숫자이고, 자바 쪽이 먼저 막고 표가 최종 방어선이다.
     *
     * <p>계수를 계산하는 쪽이 아니라 이 record 가 이 숫자들을 갖는 이유는, 이것이 계산
     * 방법에 딸린 값이 아니라 <b>계수라는 것이 성립하는 조건</b>이기 때문이다. 중앙값 대신
     * 다른 방법으로 계수를 구하는 날이 와도 "표본 셋 미만은 계수가 아니다" 는 그대로다.
     * 도메인이 응용 계층을 올려다보지 않는다는 이 저장소의 의존 방향과도 맞는다.
     */
    public static final int MIN_SAMPLES = 3;

    /**
     * 배수의 위 한계. 마이그레이션의 {@code ck_user_pace_factor_range} 와 같다.
     *
     * <p>이 값을 넘었다면 그 사람이 다섯 배 느린 것이 아니라 기록이 잘못된 것이다(출발을
     * 다음 날 찍었다든지). 그대로 받아들이면 남은 일정 전체가 무의미해진다.
     */
    public static final BigDecimal MAX_FACTOR = new BigDecimal("5.00");

    /** 같은 이유의 아래쪽. 0 이하는 시간이 거꾸로 간다는 뜻이라 표가 아예 거부한다. */
    public static final BigDecimal MIN_FACTOR = new BigDecimal("0.20");

    /**
     * 표의 CHECK 셋과 같은 판정을 여기서 먼저 한다. DB 제약 위반은 500 으로 나가고 어느 칸이
     * 문제인지 알려주지 못하는데, 이 자리에서 막으면 어느 값이 잘못됐는지와 함께 답할 수 있다
     * ({@code ItineraryItemActual} 의 컴팩트 생성자와 같은 판단).
     *
     * <p>한계값은 이 record 가 소유한다. 계수를 만드는 쪽과 검증하는 쪽이 다른 숫자를
     * 쓰면 계산기가 자른 값이 여기서 다시 걸리는 모순이 생기므로 한 자리에만 둔다.
     */
    public PaceFactor {
        if (version < 1) {
            throw new InvalidVersionException(version);
        }
        if (factor == null
                || factor.compareTo(MIN_FACTOR) < 0
                || factor.compareTo(MAX_FACTOR) > 0) {
            throw new FactorOutOfRangeException(factor);
        }
        if (sampleCount < MIN_SAMPLES) {
            throw new InsufficientSamplesException(sampleCount);
        }
    }

    /** 지금 쓰는 판인가 — supersededAt 이 없다는 뜻이다. */
    public boolean isCurrent() {
        return this.supersededAt == null;
    }

    /** 판 번호가 1보다 작다. UUID 와 달리 이 값은 사람이 순서를 읽는 자리라 0 이하를 허용하지 않는다. */
    public static class InvalidVersionException extends RuntimeException {

        private final int version;

        public InvalidVersionException(int version) {
            super("version 은 1 이상이어야 합니다: " + version);
            this.version = version;
        }

        public int version() {
            return this.version;
        }
    }

    /** 배수가 계산기의 한계 밖이다. 이 값을 그대로 받으면 남은 일정 계산 전체가 무의미해진다. */
    public static class FactorOutOfRangeException extends RuntimeException {

        private final BigDecimal factor;

        public FactorOutOfRangeException(BigDecimal factor) {
            super("factor 는 " + MIN_FACTOR + "~" + MAX_FACTOR
                    + " 범위여야 합니다: " + factor);
            this.factor = factor;
        }

        public BigDecimal factor() {
            return this.factor;
        }
    }

    /** 표본이 계산기의 최소치보다 적다. 이 개수로 만든 계수는 관측이 아니라 우연이다. */
    public static class InsufficientSamplesException extends RuntimeException {

        private final int sampleCount;

        public InsufficientSamplesException(int sampleCount) {
            super("sampleCount 는 " + MIN_SAMPLES + " 이상이어야 합니다: " + sampleCount);
            this.sampleCount = sampleCount;
        }

        public int sampleCount() {
            return this.sampleCount;
        }
    }
}
