package com.gabolle.backend.event.presentation.dto;

import java.util.UUID;

import com.gabolle.backend.event.application.EventIngestService;

/**
 * 이벤트 적재 결과. 이미 받은 이벤트여도 성공으로 응답하고 {@code duplicate} 로 사실을 알린다 —
 * 재전송은 오류가 아닌데 앱이 400 을 받으면 사용자에게 오류를 띄운다.
 */
public record IngestEventResponse(UUID eventId, boolean accepted, boolean duplicate, EventIngestService.Outcome outcome) {

	public static IngestEventResponse stored(UUID eventId) {
		return new IngestEventResponse(eventId, true, false, EventIngestService.Outcome.STORED);
	}

	public static IngestEventResponse alreadyStored(UUID eventId) {
		return new IngestEventResponse(eventId, true, true, EventIngestService.Outcome.DUPLICATE);
	}

	/**
	 * 일부러 안 적었다 — 이 사람이 행동 기반 개인화를 껐다. {@code accepted} 가 {@code true} 인
	 * 것이 맞다. 요청은 정상이었고, 실패로 읽으면 개인화를 끈 사람의 화면에만 오류가 뜬다.
	 * 구분이 필요한 쪽은 {@code outcome} 을 본다.
	 */
	public static IngestEventResponse notCollected(UUID eventId) {
		return new IngestEventResponse(eventId, true, false, EventIngestService.Outcome.NOT_COLLECTED);
	}

	public static IngestEventResponse of(UUID eventId, EventIngestService.Outcome outcome) {
		return switch (outcome) {
			case STORED -> stored(eventId);
			case DUPLICATE -> alreadyStored(eventId);
			case NOT_COLLECTED -> notCollected(eventId);
		};
	}
}
