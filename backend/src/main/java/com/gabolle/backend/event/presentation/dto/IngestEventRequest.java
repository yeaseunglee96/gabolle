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

		/**
		 * 이 행동이 어느 추천 요청에서 비롯됐는가.
		 *
		 * <h3>🔴 2026-09-07 — {@code @NotNull} 을 뗐다 (S15P21E201-735)</h3>
		 * 모든 이벤트에 필수였다. 그런데 <b>그 값을 앱에 알려주는 응답이 하나도 없었다</b> —
		 * 추천 작업 응답도 결과 응답도 {@code jobId} 만 줬다. 즉 서버가 앱에게 <b>줄 수 없는 값을
		 * 요구하고</b> 있었고, 그래서 앱의 저장·제외·방문 이벤트가 전부 400 으로 튕겼다.
		 *
		 * <p>고친 방향은 둘이다. ① 추천 작업·결과 응답이 이제 {@code requestId} 를 싣는다
		 * (그쪽 DTO 참고). ② 여기서는 <b>구조적으로 필요한 이벤트에서만</b> 요구한다 —
		 * aggregate 축이 추천 요청인 이벤트는 {@code aggregate_id} 가 {@code UUID NOT NULL} 이라
		 * 이 값 없이는 애초에 적을 수 없다. 그 판정은 이벤트 종류를 아는
		 * {@code EventIngestService} 가 한다.
		 *
		 * <p>🔴 <b>앱이 아무 UUID 나 채워 넣는 것으로 때울 수 없다.</b> 지어낸 값은 어느 추천
		 * 요청과도 안 맞아서 조인이 되는 척하면서 <b>틀린 짝</b>을 만든다. 비어 있으면 비는 것이
		 * 보이지만 지어내면 아무도 못 알아챈다. 그래서 "모르면 비운다" 가 맞다.
		 */
		UUID requestId,

		/** ISO-8601 + 시간대. 실제로 일어난 시각. 수신 시각은 서버가 찍는다. */
		@NotNull OffsetDateTime occurredAt,

		Map<String, Object> payload) {

	public int eventVersionOrDefault() {
		return this.eventVersion == null ? 1 : this.eventVersion;
	}
}
