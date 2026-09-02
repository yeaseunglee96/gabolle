package com.gabolle.backend.event.domain;

/** Outbox 행이 브로커로 나갔는가. 🔴 실제 전송은 S15P21E201-543 범위가 아니다. */
public enum OutboxPublishStatus {
	/** 아직 아무도 가져가지 않았다. */
	PENDING,
	/** 브로커로 나갔다. */
	PUBLISHED,
	/** 전송을 시도했지만 실패했다. last_error 에 이유가 남는다. */
	FAILED
}
