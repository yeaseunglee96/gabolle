package com.gabolle.backend.itinerary.presentation.dto;

/**
 * 409 Conflict 응답 본문.
 *
 * <p>🔴 최신 판 번호를 반드시 담는다. 이게 없으면 화면이 무엇으로 갱신할지 모르고,
 * 사용자는 "저장이 안 됨" 만 보고 이유를 모른다.
 *
 * <p>코드는 enum 계약이고 표시 문구는 클라이언트 i18n 이 만든다 (API-05).
 */
public record ItineraryConflictResponse(
        /** 항상 "STALE_ITINERARY_VERSION" */
        String code,
        String itineraryId,
        /** 클라이언트가 보내온 값 — 무엇이 낡았는지 알려주기 위해 되돌려 준다 */
        int attemptedBaseVersion,
        /** 🔴 지금 최신. 화면이 이걸로 다시 불러온다 */
        int latestVersion) {

    public static final String CODE = "STALE_ITINERARY_VERSION";

    public static ItineraryConflictResponse of(String itineraryId, int attempted, int latest) {
        return new ItineraryConflictResponse(CODE, itineraryId, attempted, latest);
    }
}
