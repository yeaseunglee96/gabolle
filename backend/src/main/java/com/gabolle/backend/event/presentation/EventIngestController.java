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
 * 행동 이벤트 수집 — S15P21E201-352.
 *
 * <p>🔴 완료 기준이 <b>"중계 서버가 꺼져 있어도 정상 응답한다"</b> 다.
 * 그래서 이 컨트롤러는 Kafka 를 부르지 않는다. DB 에 적고 202 로 끝낸다.
 *
 * <p>🔴 <b>{@code no-db} 프로필에는 이 엔드포인트가 없다.</b> DB 없이 띄우면
 * {@code POST /api/v1/events} 는 404 다. 있는 척하면서 메모리에 적던 것이
 * 2026-09-03 에 고친 고장이다 — {@code EventIngestService} 주석 참고.
 *
 * <h2>🔴 이벤트의 주체는 인증에서만 읽는다 (S15P21E201-705)</h2>
 * 2026-09-07 인가 점검(`-672`)까지 이 컨트롤러는 {@code Authentication} 을 받지 않고 본문의
 * {@code userId} 를 그대로 저장했다. 즉 <b>로그인한 사람 누구나 임의의 사용자 ID 로 이벤트를
 * 넣을 수 있었다.</b> 행동 이벤트는 개인화 입력으로 들어가므로 남의 추천 결과를 오염시키거나
 * 자기 행동을 남의 것으로 돌릴 수 있었다.
 *
 * <p>이 저장소에서 같은 종류가 세 번째다. 여행·일정 API 가 {@code X-User-Id} 헤더로 사용자를
 * 정하던 것(`-607`·`-610`)과 뿌리가 같다 — 헤더 대신 본문이라는 점만 다르다. 그때 세운 원칙이
 * <b>"신원은 인증 주체에서만 읽는다"</b> 이고 이제 이 경로도 그것을 지킨다.
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
	 * <p>본문에 {@code userId} 가 없으면 인증 주체로 적는다 — 로그인한 요청에서 일어난 행동은
	 * 그 사람의 것이다. 값이 있으면 인증 주체와 같아야 하고, 다르면 거부한다.
	 *
	 * <p>🔴 <b>다른 값을 조용히 인증 주체로 덮어쓰지 않는다.</b> 그렇게 하면 클라이언트가 잘못된
	 * ID 를 보내고 있어도 202 를 받아 아무도 모르고, 남의 이름으로 넣으려는 시도와 단순한 버그를
	 * 구분할 수도 없다. 거부하면 양쪽 다 드러난다.
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
