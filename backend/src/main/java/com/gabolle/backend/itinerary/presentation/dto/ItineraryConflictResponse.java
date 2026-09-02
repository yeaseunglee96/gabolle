package com.gabolle.backend.itinerary.presentation.dto;

/**
 * 409 응답의 error 부분 — API 명세 2.1 공통 envelope.
 *
 * <p>🔴 코드는 명세가 지정한 {@code ITINERARY_VERSION_CONFLICT} 를 쓴다.
 * 처음에 {@code STALE_ITINERARY_VERSION} 이라고 지어냈다가 명세를 확인하고 고쳤다 —
 * <b>오류 코드는 enum 계약</b>이라 클라이언트가 그 문자열로 분기한다.
 * 서버가 임의로 정하면 FE·APP 이 못 받는다.
 *
 * <p>🔴 문구는 서버가 만들지 않는다. {@code messageKey} 만 주고 KO/EN 번역은
 * 클라이언트가 한다(API-05 · 2.1절 — "서버 내부 예외 메시지를 그대로 노출하지 않는다").
 */
public record ItineraryConflictResponse(
        /** 항상 {@code ITINERARY_VERSION_CONFLICT} */
        String code,
        /** 항상 {@code error.itinerary.conflict} */
        String messageKey,
        Details details) {

    public static final String CODE = "ITINERARY_VERSION_CONFLICT";
    public static final String MESSAGE_KEY = "error.itinerary.conflict";

    /**
     * 🔴 {@code latestVersion} 을 반드시 담는다.
     * 없으면 화면이 무엇으로 다시 불러올지 모르고, 사용자는 "저장이 안 됨" 만 보고
     * 이유를 모른다.
     */
    public record Details(String itineraryId, int attemptedBaseVersion, int latestVersion) {}

    public static ItineraryConflictResponse of(String itineraryId, int attempted, int latest) {
        return new ItineraryConflictResponse(CODE, MESSAGE_KEY,
                new Details(itineraryId, attempted, latest));
    }
}
