package com.gabolle.backend.itinerary.application;

import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.domain.StaleItineraryVersionException;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일정 편집 — 응용 계층.
 *
 * <p>이 계층이 하는 일은 <b>순서를 조율하고 트랜잭션 경계를 긋는 것</b>이다.
 * 업무 규칙은 여기 쓰지 않는다 — {@link Itinerary#assertEditableFrom(int)} 와
 * {@link ItineraryVersion} 의 생성자에 있다.
 */
@Service
public class ItineraryEditService {

    private final ItineraryRepository repository;

    public ItineraryEditService(ItineraryRepository repository) {
        this.repository = repository;
    }

    /**
     * 편집해서 새 판을 만든다.
     *
     * <p>🔴 확인·생성·포인터 이동이 <b>하나의 트랜잭션</b>이어야 한다.
     * 나뉘면 "판은 저장됐는데 최신 포인터는 안 옮겨진" 상태가 생기고,
     * 그때부터 아무도 그 판을 못 찾는다.
     *
     * @param baseVersion 클라이언트가 화면에서 보고 있던 판 번호
     * @throws StaleItineraryVersionException 그 사이에 누가 고쳤을 때 → 409
     */
    @Transactional
    public ItineraryVersion edit(String itineraryId,
                                 int baseVersion,
                                 ItineraryVersion.Operation operation,
                                 String editorUserId,
                                 String requestId,
                                 ItineraryVersion.Versions versions) {

        Itinerary itinerary = repository.findById(itineraryId)
                .orElseThrow(() -> new NoSuchElementException("일정을 찾을 수 없습니다: " + itineraryId));

        // ① 흔한 경우를 빨리 걸러낸다. 진짜 보장은 ③ 의 DB UNIQUE 제약이 한다.
        itinerary.assertEditableFrom(baseVersion);

        // ② 새 판을 만든다. 기존 판은 건드리지 않는다.
        int next = itinerary.nextVersion();
        ItineraryVersion candidate = new ItineraryVersion(
                UUID.randomUUID().toString(),
                itineraryId,
                next,
                baseVersion,
                operation,
                editorUserId,
                requestId,
                versions,
                Instant.now());

        // ③ 저장. UNIQUE (itinerary_id, version) 위반이면 구현체가
        //    StaleItineraryVersionException 으로 바꿔 던진다 — 그게 409 가 된다.
        ItineraryVersion saved = repository.append(candidate);

        // ④ 최신 포인터를 옮긴다.
        itinerary.moveTo(next);

        return saved;
    }
}
