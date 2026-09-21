package com.gabolle.backend.event.presentation.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 클라이언트가 보내는 이벤트 한 건. 공통 필드는 칸으로 받고 종류마다 다른 것은
 * {@code payload} 로 받는다.
 *
 * <p>ID 를 문자열이 아니라 {@link UUID} 로 받는 것은 형식 오류를 입구에서 잡기 위해서다 —
 * 저장 직전에 변환하다 실패하면 400 이 아니라 500 이 된다. {@code occurredAt} 도 마찬가지로
 * {@link OffsetDateTime} 이다. 문자열로 받으면 시간대 없는 값이 조용히 UTC 로 해석된다.
 */
public record IngestEventRequest(

		/** 멱등 키. 클라이언트가 만든다 — 재전송 때 같은 값을 보내야 중복이 안 쌓인다. */
		@NotNull UUID eventId,

		/** 소문자 이름. 예: {@code recommendation_impression} */
		@NotBlank String eventType,

		Integer eventVersion,

		UUID userId,

		UUID tripId,

		/**
		 * 이 행동이 어느 추천 요청에서 비롯됐는가. 여기에 {@code @NotNull} 이 없는 것은 의도다 —
		 * 필수 여부는 이벤트 종류를 아는 {@code EventIngestService} 가 판정한다.
		 *
		 * <p>모르면 비운다. 아무 UUID 나 채우면 조인이 되는 척하면서 틀린 짝을 만들고,
		 * 비어 있는 것과 달리 아무도 못 알아챈다.
		 */
		UUID requestId,

		/** ISO-8601 + 시간대. 실제로 일어난 시각. 수신 시각은 서버가 찍는다. */
		@NotNull OffsetDateTime occurredAt,

		Map<String, Object> payload) {

	public int eventVersionOrDefault() {
		return this.eventVersion == null ? 1 : this.eventVersion;
	}
}
