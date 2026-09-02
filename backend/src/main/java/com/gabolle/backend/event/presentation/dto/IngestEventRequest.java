package com.gabolle.backend.event.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * 클라이언트가 보내는 이벤트 한 건 — POST /api/v1/events (DR-01).
 *
 * <p>공통 필드 일곱은 칸으로 받고, 종류마다 다른 것은 {@code payload} 로 받는다.
 */
public record IngestEventRequest(

        /** 🔴 멱등 키. 클라이언트가 만든다 — 재전송 때 같은 값을 보내야 중복이 안 쌓인다. */
        @NotBlank String eventId,

        /** 소문자 이름. 예: {@code recommendation_impression} */
        @NotBlank String eventType,

        Integer eventVersion,

        String userId,
        String tripId,

        /** 🔴 없으면 거부된다 (API-07). 노출과 행동을 이을 수 없다. */
        @NotBlank String requestId,

        /** ISO-8601. 실제로 일어난 시각. 수신 시각은 서버가 찍는다. */
        @NotNull String occurredAt,

        Map<String, Object> payload) {

    public int eventVersionOrDefault() {
        return eventVersion == null ? 1 : eventVersion;
    }
}
