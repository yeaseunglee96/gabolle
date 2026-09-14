package com.gabolle.backend.itinerary;

import com.gabolle.backend.trip.infra.InMemoryTripRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gabolle.backend.itinerary.application.ItineraryEditService;
import com.gabolle.backend.itinerary.application.port.PlaceEventSchedule;
import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryExclusion;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.domain.StaleItineraryVersionException;
import com.gabolle.backend.itinerary.infra.InMemoryItineraryRepository;
import com.gabolle.backend.itinerary.support.FakeItineraryItemActualRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 두 편집이 얽혀 들어올 때의 불변식 — S15P21E201-313 · S15P21E201-154.
 *
 * <h2>이 테스트가 증명하는 것</h2>
 * 저장 지점에 관문을 두어 <b>A 가 저장 중일 때 B 가 끼어드는 순서를 결정론적으로</b>
 * 만든다. 그 상태에서 셋을 단정한다.
 * <ol>
 *   <li>정확히 하나만 성공한다</li>
 *   <li>실패한 쪽은 409({@link StaleItineraryVersionException}) 를 받는다</li>
 *   <li>🔴 판이 건너뛰어지지 않는다 — 6번만 있고 7번은 없다</li>
 * </ol>
 *
 * <p>승자가 누구인지는 단정하지 않는다. 관문의 위치에 따라 달라지고,
 * 실제 서비스에서도 누가 먼저 커밋하느냐는 우연이다.
 *
 * <h2>🔴 이 테스트가 증명하지 못하는 것 — 정직하게 적는다</h2>
 * 마지막 방어선은 {@code putIfAbsent}(DB 에서는 {@code UNIQUE (itinerary_id, version)})
 * 이고 그것은 여기서 검증된다. 그런데 <b>그 앞단의 좁은 창은 검증하지 못한다.</b>
 *
 * <p>{@code Itinerary.nextVersionFrom} 은 검증과 번호 계산을 한 메서드로 합쳐
 * "검증 통과 후 latestVersion 이 바뀌는" 창을 없앴다. 남은 창은 바이트코드 두 개
 * 사이라, 결정론적으로 그 틈에 다른 스레드를 끼워 넣을 방법이 없다.
 *
 * <p>즉 그 수정은 <b>테스트로 증명된 것이 아니라 창을 구조적으로 없앤 것</b>이다.
 * 대신 그 결과인 불변식 {@code version == baseVersion + 1} 은
 * {@code ItineraryVersionConflictTest} 에서 값으로 확인한다.
 */
class ItineraryVersionRaceTest {

    private static final ItineraryVersion.Versions VERSIONS = new ItineraryVersion.Versions(
            "model-1", "feature-1", "onto-1", "policy-1", "dataset-1");

    private static final String ITEM_KEY = "11111111-1111-4111-8111-111111111111";

    /**
     * 저장이 시작되면 신호를 보내고, 놓아 줄 때까지 기다리는 저장소.
     *
     * <p>이것으로 "A 가 저장 중인 바로 그 순간" 에 B 를 진행시킬 수 있다.
     */
    private static final class GatedRepository implements ItineraryRepository {

        private final InMemoryItineraryRepository delegate = new InMemoryItineraryRepository();
        private final CountDownLatch appendEntered = new CountDownLatch(1);
        private final CountDownLatch releaseAppend = new CountDownLatch(1);
        private volatile boolean gateArmed = true;

        @Override
        public Optional<Itinerary> findById(String itineraryId) {
            return delegate.findById(itineraryId);
        }

        @Override
        public ItineraryVersion appendVersion(ItineraryVersion version, List<ItineraryItem> items,
                List<ItineraryLeg> legs, List<ItineraryExclusion> exclusions) {
            if (gateArmed) {
                gateArmed = false;          // 첫 저장만 붙잡는다
                appendEntered.countDown();
                try {
                    releaseAppend.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return delegate.appendVersion(version, items, legs, exclusions);
        }

        @Override
        public Optional<ItineraryVersion> findVersion(String itineraryId, int version) {
            return delegate.findVersion(itineraryId, version);
        }

        @Override
        public Itinerary create(Itinerary itinerary, ItineraryVersion firstVersion,
                List<ItineraryItem> items, List<ItineraryLeg> legs) {
            // 🔴 이 테스트는 appendVersion() 의 경쟁만 본다 — create() 는 관문을 걸지 않는다.
            return delegate.create(itinerary, firstVersion, items, legs);
        }

        @Override
        public Optional<ItineraryContent> findContent(String itineraryId, int version) {
            return delegate.findContent(itineraryId, version);
        }

        @Override
        public List<ItineraryVersion> findVersions(String itineraryId) {
            // 🔴 S15P21E201-284 — 이 테스트는 appendVersion() 의 경쟁만 본다. 목록 조회는
            // 관문을 걸지 않고 그대로 위임한다.
            return delegate.findVersions(itineraryId);
        }

        @Override
        public List<Itinerary> findByTripId(String tripId) {
            return delegate.findByTripId(tripId);
        }

        @Override
        public List<ItineraryVersion> findRecentVersions(Collection<String> itineraryIds, int limit) {
            return delegate.findRecentVersions(itineraryIds, limit);
        }

        Itinerary seed(String id, String tripId, int latest) {
            return delegate.seed(id, tripId, latest);
        }

        void seedVersion(ItineraryVersion version, List<ItineraryItem> items, List<ItineraryLeg> legs) {
            delegate.seedVersion(version, items, legs);
        }
    }

    /**
     * 🔴 A 가 저장하는 중에 B 가 끼어든다 — 판이 건너뛰어지지 않아야 한다.
     *
     * <p>이 테스트는 버그({@code latestVersion + 1})가 있으면 <b>반드시</b> 실패한다.
     * 운에 맡기지 않는다.
     */
    @Test
    @DisplayName("🔴 A 가 저장하는 중에 B 가 끼어들어도 판이 건너뛰어지지 않는다")
    void interleavedEditDoesNotSkipVersion() throws Exception {
        GatedRepository repo = new GatedRepository();
        // 기간이 정해진 장소가 아니라고 답하는 문 — 이 테스트가 보는 것은 판 번호 경쟁이다.
        // 위와 같은 이유로 구간 계산기는 안 넘긴다 — 여행 저장소가 비어 있어 그 자리에 닿지 않는다.
        ItineraryEditService service = new ItineraryEditService(repo,
                (placeId, from, to) -> PlaceEventSchedule.unscheduled(),
                // 구간 계획기와 영업시간 검사기는 순서 바꾸기에서만 쓰인다. 이 검사는 고정만
                // 부르고, 여행 저장소도 비어 있어 그 갈래에 닿지 않는다.
                null, new InMemoryTripRepository(), null, Clock.systemUTC(),
                // 실제 시각 저장소는 재계획(S15P21E201-308)만 읽는다. 이 검사가 보는 것은
                // 판 번호 경쟁이라 그 자리에 닿지 않지만, 빈 대역을 줘서 나중에 닿게 되더라도
                // NPE 가 아니라 "기록이 없는 일정" 으로 이어지게 한다.
                new FakeItineraryItemActualRepository());
        repo.seed("itn_1", "trp_1", 5);

        // 🔴 바탕 판에 내용이 있어야 한다 — 편집은 그것을 새 판으로 복사한다(S15P21E201-662).
        String seedVersionId = UUID.randomUUID().toString();
        repo.seedVersion(
                new ItineraryVersion(seedVersionId, "itn_1", 5, 4,
                        ItineraryVersion.Operation.REGENERATE, "usr_seed", "req_seed", VERSIONS, Instant.now()),
                List.of(new ItineraryItem(UUID.randomUUID().toString(), seedVersionId, ITEM_KEY,
                        0, LocalDate.of(2026, 9, 10), 1, UUID.randomUUID().toString(),
                        null, null, null, false, null, ItineraryItem.DataStatus.UNKNOWN,
                        List.of(), List.of(), null, Instant.now())),
                List.of());

        AtomicReference<Throwable> aError = new AtomicReference<>();
        AtomicReference<Throwable> bError = new AtomicReference<>();
        AtomicReference<Integer> aVersion = new AtomicReference<>();
        AtomicReference<Integer> bVersion = new AtomicReference<>();

        Thread a = new Thread(() -> {
            try {
                ItineraryVersion v = service.setItemLocked("itn_1", ITEM_KEY, true, 5, "usr_a");
                aVersion.set(v.version());
            } catch (Throwable t) {
                aError.set(t);
            }
        }, "editor-A");

        Thread b = new Thread(() -> {
            try {
                ItineraryVersion v = service.setItemLocked("itn_1", ITEM_KEY, false, 5, "usr_b");
                bVersion.set(v.version());
            } catch (Throwable t) {
                bError.set(t);
            }
        }, "editor-B");

        a.start();
        // A 가 저장 안으로 들어간 것을 확인한 뒤 B 를 출발시킨다.
        assertTrue(repo.appendEntered.await(5, TimeUnit.SECONDS), "A 가 저장에 진입해야 한다");

        b.start();
        // B 가 검증을 지나 저장까지 시도할 시간을 준다. 그 뒤 A 를 놓아 준다.
        Thread.sleep(200);
        repo.releaseAppend.countDown();

        a.join(5000);
        b.join(5000);

        // 🔴 지켜야 하는 불변식은 "누가 이기는가" 가 아니다.
        //
        //    관문이 A 를 실제 저장 직전에 붙잡으므로, B 가 그 사이에 먼저 저장하고
        //    A 가 나중에 409 를 받는다. 즉 승자는 관문의 위치에 따라 달라진다.
        //    실제 서비스에서도 누가 먼저 커밋하느냐는 우연이다.
        //
        //    그래서 단정하는 것은 셋이다.
        //      ① 정확히 하나만 성공한다
        //      ② 실패한 쪽은 409(StaleItineraryVersionException) 를 받는다
        //      ③ 🔴 판이 건너뛰어지지 않는다 — 6번만 있고 7번은 없다
        //
        //    ③ 이 이 테스트의 존재 이유다. 버그(latestVersion + 1)가 있으면
        //    두 번째 요청이 7번을 만들고 ③ 이 깨진다.

        int succeeded = (aVersion.get() != null ? 1 : 0) + (bVersion.get() != null ? 1 : 0);
        assertEquals(1, succeeded, "① 정확히 하나만 성공해야 한다");

        Throwable loserError = aVersion.get() == null ? aError.get() : bError.get();
        assertTrue(loserError instanceof StaleItineraryVersionException,
                "② 실패한 쪽은 409 여야 한다. 실제: " + loserError);

        Integer winnerVersion = aVersion.get() != null ? aVersion.get() : bVersion.get();
        assertEquals(6, winnerVersion, "이긴 쪽은 6번을 만들어야 한다");

        assertTrue(repo.findVersion("itn_1", 7).isEmpty(),
                "③ 🔴 7번 판이 있으면 baseVersion 5 에서 두 칸 뛴 것이다");
        assertEquals(6, repo.findById("itn_1").orElseThrow().latestVersion());
    }
}
