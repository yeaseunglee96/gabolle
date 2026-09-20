package com.gabolle.backend.event.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.event.application.EventIngestService;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.event.presentation.dto.IngestEventRequest;
import com.gabolle.backend.event.presentation.dto.IngestEventResponse;

import jakarta.validation.Valid;

/**
 * 행동 이벤트 수집. 중계 서버가 꺼져 있어도 정상 응답해야 하므로 브로커를 부르지 않고
 * DB 에 적고 202 로 끝낸다. {@code no-db} 프로필에는 이 엔드포인트가 없어 404 다.
 *
 * <p>이벤트의 주체는 인증에서만 읽는다. 본문의 {@code userId} 를 그대로 믿으면 로그인한
 * 누구나 임의의 사용자 ID 로 이벤트를 넣을 수 있고, 행동 이벤트는 개인화 입력이라 남의
 * 추천 결과를 오염시킬 수 있다.
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
	 * 이벤트 한 건을 받는다. 202 로 답하는 것은 "받아서 적었고 전달은 나중" 이라는 뜻이다 —
	 * 200 은 처리가 끝났다는 오해를 준다.
	 */
	@PostMapping
	public ResponseEntity<IngestEventResponse> ingest(@Valid @RequestBody IngestEventRequest req,
			Authentication authentication) {

		EventType type = EventType.fromWireName(req.eventType());
		UUID subject = resolveSubject(req.userId(), authentication);

		EventIngestService.Outcome outcome = this.service.ingestFromClient(
				req.eventId(),
				type,
				req.eventVersionOrDefault(),
				subject,
				req.tripId(),
				req.requestId(),
				req.occurredAt(),
				req.payload());

		return ResponseEntity.status(HttpStatus.ACCEPTED).body(IngestEventResponse.of(req.eventId(), outcome));
	}

	/**
	 * 이 이벤트를 누구의 것으로 적을지 정한다.
	 *
	 * <p>본문에 {@code userId} 가 없으면 인증 주체로 적고, 있으면 인증 주체와 같아야 한다.
	 * 다른 값을 조용히 덮어쓰지 않고 거부한다 — 덮어쓰면 잘못된 ID 를 보내는 클라이언트가
	 * 202 를 받아 아무도 모르고, 남의 이름으로 넣으려는 시도와 버그를 구분할 수 없다.
	 */
	private static UUID resolveSubject(UUID claimed, Authentication authentication) {
		UUID subject = AuthenticatedUsers.requireId(authentication);
		if (claimed != null && !claimed.equals(subject)) {
			throw new ForeignEventSubjectException();
		}
		return subject;
	}

	/** 본문의 {@code userId} 가 인증 주체와 다르다 — 403. */
	public static class ForeignEventSubjectException extends RuntimeException {

		public ForeignEventSubjectException() {
			super("이벤트의 주체는 요청한 사용자여야 합니다.");
		}
	}
}
