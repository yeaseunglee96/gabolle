package com.gabolle.backend.event.domain;

/** Outbox 행이 브로커로 나갔는가. */
public enum OutboxPublishStatus {
	PENDING,
	PUBLISHED,
	/** 전송을 시도했지만 실패했다. 이유는 last_error 에 남는다. */
	FAILED
}
