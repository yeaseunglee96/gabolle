package com.gabolle.backend.event.presentation.dto;

import java.util.UUID;

import com.gabolle.backend.event.application.EventIngestService;

/**
 * 이벤트 적재 결과.
 *
 * <p>🔴 이미 받은 이벤트여도 <b>성공으로 응답한다.</b> 재전송은 오류가 아니다 —
 * 앱이 400 을 받으면 사용자에게 오류를 띄우고, 그건 잘못된 화면이다.
 * 대신 {@code duplicate} 로 사실을 알린다.
 */
public record IngestEventResponse(UUID eventId, boolean accepted, boolean duplicate, EventIngestService.Outcome outcome) {

	public static IngestEventResponse stored(UUID eventId) {
		return new IngestEventResponse(eventId, true, false, EventIngestService.Outcome.STORED);
	}

	public static IngestEventResponse alreadyStored(UUID eventId) {
		return new IngestEventResponse(eventId, true, true, EventIngestService.Outcome.DUPLICATE);
	}

	/**
	 * 🔴 <b>일부러 안 적었다</b> — 이 사람이 행동 기반 개인화를 껐다 (S15P21E201-549).
	 *
	 * <p>{@code accepted} 가 {@code true} 인 것이 이상해 보이지만 맞다. 요청은 정상이었고
	 * 서버는 정상으로 처리했다. 앱이 이것을 실패로 읽으면 <b>개인화를 끈 사람의 화면에만
	 * 오류가 뜬다</b> — 끈 대가로 오류를 보게 되면 사람은 다시 켠다. 그건 동의가 아니다.
	 *
	 * <p>구분이 필요한 쪽(계측 점검·대시보드)은 {@code outcome} 을 본다.
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
