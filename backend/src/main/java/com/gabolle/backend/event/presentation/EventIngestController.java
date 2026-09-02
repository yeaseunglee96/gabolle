package com.gabolle.backend.event.presentation;

import com.gabolle.backend.event.application.EventIngestService;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.event.presentation.dto.IngestEventRequest;
import com.gabolle.backend.event.presentation.dto.IngestEventResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 행동 이벤트 수집 — S15P21E201-352.
 *
 * <p>🔴 완료 기준이 <b>"중계 서버가 꺼져 있어도 정상 응답한다"</b> 다.
 * 그래서 이 컨트롤러는 Kafka 를 부르지 않는다. DB 에 적고 202 로 끝낸다.
 */
@RestController
@RequestMapping("/api/v1/events")
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

        EventType type = parseType(req.eventType());

        boolean stored = service.ingestFromClient(
                req.eventId(),
                type,
                req.eventVersionOrDefault(),
                req.userId(),
                req.tripId(),
                req.requestId(),
                Instant.parse(req.occurredAt()),
                req.payload());

        IngestEventResponse body = stored
                ? IngestEventResponse.stored(req.eventId())
                : IngestEventResponse.alreadyStored(req.eventId());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    /**
     * 소문자 이름을 열거값으로 바꾼다.
     *
     * <p>🔴 모르는 종류는 거부한다. 받아서 적어 두면 분석 단계에서
     * "이건 뭐지" 가 되고, 그때는 보낸 클라이언트가 이미 배포돼 있다.
     */
    private EventType parseType(String wireName) {
        try {
            return EventType.valueOf(wireName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("모르는 이벤트 종류: " + wireName, e);
        }
    }
}
