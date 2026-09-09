package com.gabolle.backend.event.presentation.dto;

import java.util.UUID;

/**
 * 이벤트 적재 결과.
 *
 * <p>🔴 이미 받은 이벤트여도 <b>성공으로 응답한다.</b> 재전송은 오류가 아니다 —
 * 앱이 400 을 받으면 사용자에게 오류를 띄우고, 그건 잘못된 화면이다.
 * 대신 {@code duplicate} 로 사실을 알린다.
 */
public record IngestEventResponse(UUID eventId, boolean accepted, boolean duplicate) {

	public static IngestEventResponse stored(UUID eventId) {
		return new IngestEventResponse(eventId, true, false);
	}

	public static IngestEventResponse alreadyStored(UUID eventId) {
		return new IngestEventResponse(eventId, true, true);
	}
}
