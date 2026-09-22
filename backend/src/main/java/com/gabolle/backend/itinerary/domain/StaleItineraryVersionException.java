package com.gabolle.backend.itinerary.domain;

/**
 * 클라이언트가 보고 있던 판이 이미 낡았을 때 — 409 Conflict 로 응답한다.
 * 이 예외의 목적은 막는 것이 아니라 알리는 것이다. 그래서 최신 판 번호를 함께 담는다 —
 * 화면이 그것으로 다시 불러와 "다른 사람이 먼저 수정했습니다" 를 보여주고 사용자에게
 * 선택권을 준다.
 */
public class StaleItineraryVersionException extends RuntimeException {

    private final String itineraryId;
    private final int attemptedBaseVersion;
    private final int latestVersion;

    public StaleItineraryVersionException(String itineraryId, int attemptedBaseVersion, int latestVersion) {
        super("일정 " + itineraryId + " 의 최신 판은 " + latestVersion
                + " 인데 " + attemptedBaseVersion + " 을 바탕으로 수정하려 했습니다");
        this.itineraryId = itineraryId;
        this.attemptedBaseVersion = attemptedBaseVersion;
        this.latestVersion = latestVersion;
    }

    public String itineraryId()      { return itineraryId; }
    public int attemptedBaseVersion() { return attemptedBaseVersion; }
    /** 응답에 반드시 담는다. 이게 없으면 화면이 무엇으로 갱신할지 모른다. */
    public int latestVersion()        { return latestVersion; }
}
