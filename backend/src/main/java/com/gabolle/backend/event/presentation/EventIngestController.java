package com.gabolle.backend.event.presentation;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.event.application.EventIngestService;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.event.presentation.dto.IngestEventRequest;
import com.gabolle.backend.event.presentation.dto.IngestEventResponse;

import jakarta.validation.Valid;

/**
 * 행동 이벤트 수집 — S15P21E201-352.
 *
 * <p>🔴 완료 기준이 <b>"중계 서버가 꺼져 있어도 정상 응답한다"</b> 다.
 * 그래서 이 컨트롤러는 Kafka 를 부르지 않는다. DB 에 적고 202 로 끝낸다.
 *
 * <p>🔴 <b>{@code no-db} 프로필에는 이 엔드포인트가 없다.</b> DB 없이 띄우면
 * {@code POST /api/v1/events} 는 404 다. 있는 척하면서 메모리에 적던 것이
 * 2026-09-03 에 고친 고장이다 — {@code EventIngestService} 주석 참고.
 */
@RestController
@RequestMapping("/api/v1/events")
@Profile({ "db", "dev" })
public class EventIngestController {

	private final EventIngestService service;

	public EventIngestController(EventIngestService service) {
		this.service = service;
	}

	/**
	 * 이벤트 한 건을 받는다.
	 *
	 * <p>202 Accepted 로 답한다 — "받아서 적었고, 분석 시스템 전달은 나중에" 라는 뜻이다.
	 * 200 으로 답하면 "처리가 끝났다" 는 오해를 준다.
	 */
	@PostMapping
	public ResponseEntity<IngestEventResponse> ingest(@Valid @RequestBody IngestEventRequest req) {

		EventType type = EventType.fromWireName(req.eventType());

		boolean stored = this.service.ingestFromClient(
				req.eventId(),
				type,
				req.eventVersionOrDefault(),
				req.userId(),
				req.tripId(),
				req.requestId(),
				req.occurredAt(),
				req.payload());

		IngestEventResponse body = stored ? IngestEventResponse.stored(req.eventId())
				: IngestEventResponse.alreadyStored(req.eventId());

		return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
	}
}
