package com.gabolle.backend.itinerary;

import com.gabolle.backend.trip.infra.InMemoryTripRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gabolle.backend.itinerary.application.ItineraryEditService;
import com.gabolle.backend.itinerary.application.port.PlaceEventSchedule;
import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.domain.StaleItineraryVersionException;
import com.gabolle.backend.itinerary.infra.InMemoryItineraryRepository;
import com.gabolle.backend.itinerary.support.FakeItineraryItemActualRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 일정 판 번호와 409 충돌. 충돌을 설명하는 것이 아니라 실제로 재현한다.
 *
 * <p>Spring 컨텍스트를 띄우지 않는다 — 도메인 규칙과 저장소 경쟁을 보는 것이라 컨텍스트가
 * 필요 없다. 판 복사가 실제 표에 남는지는 {@link ItineraryLockPersistenceIntegrationTest}
 * 가 진짜 PostgreSQL 로 본다.
 */
class ItineraryVersionConflictTest {

    private static final ItineraryVersion.Versions VERSIONS = new ItineraryVersion.Versions(
            "model-1", "feature-1", "onto-1", "policy-1", "dataset-1");

    private static final String ITEM_KEY = "11111111-1111-4111-8111-111111111111";
    private static final String OTHER_ITEM_KEY = "22222222-2222-4222-8222-222222222222";

    private InMemoryItineraryRepository repository;
    private ItineraryEditService service;

    /** 5번 판까지 와 있고 그 판에 항목 둘이 들어 있는 일정. */
    private void seedAtVersion5() {
        repository = new InMemoryItineraryRepository();
        // 기간이 정해진 장소가 아니라고 답하는 문. 이 테스트가 보는 것은 판 번호와 409 이고,
        // 축제 날짜 검사는 그 판정에 끼어들지 않아야 한다(ItineraryAddItemIntegrationTest 가 본다).
        // 구간 계산기는 안 넘긴다. 여행 저장소가 비어 있어서 순서 바꾸기가 여행을 못 찾고,
        // 그때 구간 다시 만들기는 시작조차 안 한다(ItineraryEditService.withRebuiltDayLegs).
        // 이 테스트가 보는 것은 판 번호와 409 뿐이다.
        service = new ItineraryEditService(repository,
                (placeId, from, to) -> PlaceEventSchedule.unscheduled(),
                // 구간 계획기와 영업시간 검사기는 순서 바꾸기에서만 쓰인다. 이 검사는 고정만
                // 부르고, 여행 저장소도 비어 있어 그 갈래에 닿지 않는다.
                null, new InMemoryTripRepository(), null, Clock.systemUTC(),
                // 실제 시각 저장소는 재계획만 읽는다. null 대신 빈 대역을 주어 나중에 이
                // 자리를 쓰게 되더라도 NPE 가 아니라 기록 없는 일정이 되게 한다.
                new FakeItineraryItemActualRepository(),
                // 알림은 이 검사의 관심이 아니다 — 사건을 버리는 자리를 준다 (S15P21E201-1391).
                (event) -> { });
        repository.seed("itn_1", "trp_1", 5);

        String versionId = UUID.randomUUID().toString();
        ItineraryVersion v5 = new ItineraryVersion(versionId, "itn_1", 5, 4,
                ItineraryVersion.Operation.REGENERATE, "usr_seed", "req_seed", VERSIONS, Instant.now());
        repository.seedVersion(v5,
                List.of(item(versionId, ITEM_KEY, 1), item(versionId, OTHER_ITEM_KEY, 2)),
                List.of());
    }

    private static ItineraryItem item(String versionId, String itemKey, int sequence) {
        return new ItineraryItem(UUID.randomUUID().toString(), versionId, itemKey,
                0, LocalDate.of(2026, 9, 10), sequence, UUID.randomUUID().toString(),
                null, null, null, false, null, ItineraryItem.DataStatus.UNKNOWN,
                List.of(), List.of(), null, Instant.now());
    }

    @Test
    @DisplayName("최신 판을 바탕으로 편집하면 다음 판이 만들어진다")
    void editFromLatestCreatesNextVersion() {
        seedAtVersion5();

        ItineraryVersion saved = service.setItemLocked("itn_1", ITEM_KEY, true, 5, "usr_a");

        assertEquals(6, saved.version());
        assertEquals(5, saved.baseVersion());
        assertEquals(6, repository.findById("itn_1").orElseThrow().latestVersion());
    }

    /**
     * 판을 더할 때 내용을 복사하지 않으면 새 판이 비고, 조회는 최신 판을 읽으므로
     * 사용자 눈에는 일정이 통째로 사라진 것으로 보인다.
     */
    @Test
    @DisplayName("🔴 편집으로 만든 새 판에 바탕 판의 항목이 그대로 있다")
    void newVersionKeepsContent() {
        seedAtVersion5();

        service.setItemLocked("itn_1", ITEM_KEY, true, 5, "usr_a");

        ItineraryContent v6 = repository.findContent("itn_1", 6).orElseThrow();
        assertEquals(2, v6.items().size(), "🔴 항목이 새 판으로 안 옮겨지면 화면이 빈 일정이 된다");
        assertTrue(v6.items().stream().anyMatch(i -> i.itemKey().equals(ITEM_KEY)));
        assertTrue(v6.items().stream().anyMatch(i -> i.itemKey().equals(OTHER_ITEM_KEY)));
    }

    /**
     * {@code itemKey} 를 물려주지 않으면 방금 고정한 항목을 다음 요청에서 못 찾는다.
     * DB 의 {@code uq_itinerary_item_key} 는 같은 판 안의 중복만 막으므로 못 잡는다.
     */
    @Test
    @DisplayName("🔴 같은 itemKey 로 연속 두 번 편집할 수 있다 — 키가 판을 건너 살아남는다")
    void itemKeySurvivesAcrossVersions() {
        seedAtVersion5();

        service.setItemLocked("itn_1", ITEM_KEY, true, 5, "usr_a");
        service.setItemLocked("itn_1", ITEM_KEY, false, 6, "usr_a");

        ItineraryContent v7 = repository.findContent("itn_1", 7).orElseThrow();
        ItineraryItem edited = v7.items().stream()
                .filter(i -> i.itemKey().equals(ITEM_KEY)).findFirst().orElseThrow();
        assertFalse(edited.locked(), "두 번째 요청이 해제였다");
    }

    @Test
    @DisplayName("고정한 항목만 locked 가 바뀌고 나머지는 그대로다")
    void onlyTargetItemChanges() {
        seedAtVersion5();

        service.setItemLocked("itn_1", ITEM_KEY, true, 5, "usr_a");

        ItineraryContent v6 = repository.findContent("itn_1", 6).orElseThrow();
        assertTrue(v6.items().stream().filter(i -> i.itemKey().equals(ITEM_KEY))
                .findFirst().orElseThrow().locked());
        assertFalse(v6.items().stream().filter(i -> i.itemKey().equals(OTHER_ITEM_KEY))
                .findFirst().orElseThrow().locked());
    }

    @Test
    @DisplayName("🔴 낡은 판 번호로 편집하면 거절되고 최신 번호를 알려준다")
    void editFromStaleVersionIsRejected() {
        seedAtVersion5();
        service.setItemLocked("itn_1", ITEM_KEY, true, 5, "usr_a");

        // 이제 최신은 6. 화면이 아직 5 를 보고 있다고 가정한다.
        StaleItineraryVersionException e = assertThrows(StaleItineraryVersionException.class,
                () -> service.setItemLocked("itn_1", OTHER_ITEM_KEY, true, 5, "usr_b"));

        assertEquals(5, e.attemptedBaseVersion());
        // 최신 번호가 응답에 들어 있어야 한다. 없으면 화면이 무엇으로 갱신할지 모른다.
        assertEquals(6, e.latestVersion());
    }

    @Test
    @DisplayName("🔴 앞사람의 변경이 남아 있다 — 조용히 덮어쓰지 않는다")
    void firstEditSurvives() {
        seedAtVersion5();
        service.setItemLocked("itn_1", ITEM_KEY, true, 5, "usr_a");

        assertThrows(StaleItineraryVersionException.class,
                () -> service.setItemLocked("itn_1", OTHER_ITEM_KEY, true, 5, "usr_b"));

        ItineraryVersion v6 = repository.findVersion("itn_1", 6).orElseThrow();
        assertEquals(ItineraryVersion.Operation.LOCK_ITEM, v6.operation());
        assertEquals("usr_a", v6.createdBy());
        assertTrue(repository.findContent("itn_1", 6).orElseThrow().items().stream()
                .filter(i -> i.itemKey().equals(ITEM_KEY)).findFirst().orElseThrow().locked(),
                "🔴 앞사람이 고정한 결과가 남아 있어야 한다");
    }

    /**
     * 동행자 전원이 EDITOR 라서 두 사람이 같은 순간에 고치는 일이 실제로 생긴다. 응용
     * 계층의 사전 확인만으로는 확인과 저장 사이에 다른 요청이 끼어든다. 스레드 8개가 모두
     * 5번을 바탕으로 동시에 시도해도 정확히 하나만 성공해야 한다.
     */
    @Test
    @DisplayName("🔴 8개 요청이 동시에 들어와도 정확히 하나만 성공한다")
    void onlyOneSucceedsUnderConcurrency() throws Exception {
        seedAtVersion5();

        int threads = 8;
        CountDownLatch startTogether = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Void>> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final String user = "usr_" + i;
                tasks.add(() -> {
                    startTogether.await();          // 동시에 출발시킨다
                    try {
                        service.setItemLocked("itn_1", ITEM_KEY, true, 5, user);
                        succeeded.incrementAndGet();
                    } catch (StaleItineraryVersionException expected) {
                        rejected.incrementAndGet();
                    }
                    return null;
                });
            }

            List<Future<Void>> futures = new java.util.ArrayList<>();
            for (Callable<Void> t : tasks) {
                futures.add(pool.submit(t));
            }
            startTogether.countDown();
            for (Future<Void> f : futures) {
                f.get();                            // 예외가 있으면 여기서 터진다
            }
        } finally {
            pool.shutdownNow();
        }

        // 핵심 단정 — 하나만 통과했다
        assertEquals(1, succeeded.get(), "정확히 하나만 성공해야 한다");
        assertEquals(threads - 1, rejected.get(), "나머지는 전부 409 로 거절돼야 한다");

        // 판이 건너뛰어지지 않았다. 6 만 있고 7 은 없다.
        assertEquals(6, repository.findById("itn_1").orElseThrow().latestVersion());
        assertTrue(repository.findVersion("itn_1", 6).isPresent(), "6번 판이 있어야 한다");
        assertTrue(repository.findVersion("itn_1", 7).isEmpty(),
                "🔴 7번 판이 생기면 baseVersion 5 에서 판을 건너뛴 것이다");
    }

    @Test
    @DisplayName("판 번호는 한 칸씩만 올라간다 — 건너뛰면 이력이 끊긴다")
    void versionMovesOneStepAtATime() {
        seedAtVersion5();

        for (int base = 5; base < 9; base++) {
            ItineraryVersion v = service.setItemLocked("itn_1", ITEM_KEY, base % 2 == 1, base, "usr_a");
            assertEquals(base + 1, v.version());
        }
        assertEquals(9, repository.findById("itn_1").orElseThrow().latestVersion());
    }

    @Test
    @DisplayName("🔴 재현에 필요한 버전 다섯이 다 있어야 완전하다")
    void versionsMustBeComplete() {
        assertTrue(VERSIONS.isComplete());

        // dataset 이 빠지면 장소 데이터를 갱신한 뒤 결과가 달라져도 원인을 못 찾는다.
        ItineraryVersion.Versions missingDataset = new ItineraryVersion.Versions(
                "model-1", "feature-1", "onto-1", "policy-1", null);
        assertFalse(missingDataset.isComplete(), "dataset 이 없으면 불완전해야 한다");
    }

    @Test
    @DisplayName("baseVersion 이 version 보다 뒤면 생성 자체가 거부된다")
    void baseVersionMustPrecedeVersion() {
        assertThrows(IllegalArgumentException.class,
                () -> new ItineraryVersion("iv_1", "itn_1", 5, 6,
                        ItineraryVersion.Operation.REORDER, "usr_a", "req_1", VERSIONS,
                        java.time.Instant.now()));
    }
}
