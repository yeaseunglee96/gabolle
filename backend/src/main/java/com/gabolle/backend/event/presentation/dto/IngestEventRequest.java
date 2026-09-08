package com.gabolle.backend.event.presentation.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 클라이언트가 보내는 이벤트 한 건 — POST /api/v1/events (DR-01).
 *
 * <p>공통 필드 일곱은 칸으로 받고, 종류마다 다른 것은 {@code payload} 로 받는다.
 *
 * <p>🔴 ID 넷이 전부 {@link UUID} 다 — {@code String} 이 아니다.
 * {@code event_outbox.event_id} 와 {@code recommendation_job.request_id} 가 DB 에서
 * {@code UUID} 컬럼이라 여기서 문자열로 받으면 저장 직전에 어차피 변환해야 하고,
 * <b>그때 실패하면 400 이 아니라 500 이 된다.</b> 형식 오류는 입구에서 잡는 것이 맞다.
 *
 * <p>{@code occurredAt} 도 {@link OffsetDateTime} 으로 받는다. 문자열로 받고 나중에
 * {@code Instant.parse} 하면 <b>시간대 없는 값이 조용히 UTC 로 해석된다</b> —
 * 부산에서 09:00 에 일어난 일이 18:00 로 적히고 아무도 모른다.
 */
public record IngestEventRequest(

		/** 🔴 멱등 키. 클라이언트가 만든다 — 재전송 때 같은 값을 보내야 중복이 안 쌓인다. */
		@NotNull UUID eventId,

		/** 소문자 이름. 예: {@code recommendation_impression} */
		@NotBlank String eventType,

		Integer eventVersion,

		UUID userId,

		UUID tripId,

		/** 🔴 없으면 거부된다 (API-07). 노출과 행동을 이을 수 없다. */
		@NotNull UUID requestId,

		/** ISO-8601 + 시간대. 실제로 일어난 시각. 수신 시각은 서버가 찍는다. */
		@NotNull OffsetDateTime occurredAt,

		Map<String, Object> payload) {

	public int eventVersionOrDefault() {
		return this.eventVersion == null ? 1 : this.eventVersion;
	}
}
