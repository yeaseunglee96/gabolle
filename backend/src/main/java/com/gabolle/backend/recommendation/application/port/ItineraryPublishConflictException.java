package com.gabolle.backend.recommendation.application.port;

/**
 * 계산은 끝났는데 그 사이 다른 사람이 판을 올렸다 — 결과를 버렸고 이전 판이 그대로 최신이다.
 *
 * 일정 쪽 {@code StaleItineraryVersionException} 을 그대로 던지지 않고 포트 예외로 바꾸는 것은
 * 추천 계층이 일정 도메인 타입을 import 하지 않게 하기 위해서다.
 * {@code RecommendationJobWorker} 는 이것을 잡아 Job 을 {@code ITINERARY_VERSION_CONFLICT} 로
 * 실패시키고 {@code retryable=true} 로 둔다 — 최신 일정을 불러와 다시 요청하면 되는 실패다.
 */
public class ItineraryPublishConflictException extends RuntimeException {

    private final String itineraryId;
    private final int attemptedBaseVersion;
    private final int latestVersion;

    public ItineraryPublishConflictException(String itineraryId, int attemptedBaseVersion, int latestVersion) {
        super("일정 " + itineraryId + " 의 최신 판은 " + latestVersion + " 인데 " + attemptedBaseVersion
                + " 을 바탕으로 계산한 결과라 버렸습니다");
        this.itineraryId = itineraryId;
        this.attemptedBaseVersion = attemptedBaseVersion;
        this.latestVersion = latestVersion;
    }

    public String itineraryId() { return itineraryId; }
    public int attemptedBaseVersion() { return attemptedBaseVersion; }
    public int latestVersion() { return latestVersion; }
}
