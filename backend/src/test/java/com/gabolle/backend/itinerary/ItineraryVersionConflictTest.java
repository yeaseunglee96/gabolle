package com.gabolle.backend.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gabolle.backend.itinerary.application.ItineraryEditService;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.domain.StaleItineraryVersionException;
import com.gabolle.backend.itinerary.infra.InMemoryItineraryRepository;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 일정 판 번호와 409 충돌 — S15P21E201-313 · S15P21E201-154.
 *
 * <p>🔴 M1 완료 조건 ② 가 <b>"409 충돌이 실제로 재현된다"</b> 다.
 * "만들었다" 와 "고장을 일부러 내서 보여준다" 는 다르고, 판정 회의가 요구하는 것은 후자다.
 *
 * <p>Spring 컨텍스트를 띄우지 않는다 — 도메인 규칙과 저장소 경쟁을 보는 것이고,
 * 컨텍스트를 띄우면 느리고 DB 설정에 얽힌다.
 */
class ItineraryVersionConflictTest {

    private static final ItineraryVersion.Versions VERSIONS = new ItineraryVersion.Versions(
            "model-1", "feature-1", "onto-1", "policy-1", "dataset-1");

    private InMemoryItineraryRepository repository;
    private ItineraryEditService service;

    private void seedAtVersion5() {
        repository = new InMemoryItineraryRepository();
        service = new ItineraryEditService(repository);
        repository.seed("itn_1", "trp_1", 5);
    }

    @Test
    @DisplayName("최신 판을 바탕으로 편집하면 다음 판이 만들어진다")
    void editFromLatestCreatesNextVersion() {
        seedAtVersion5();

        ItineraryVersion saved = service.edit("itn_1", 5,
                ItineraryVersion.Operation.REPLACE_ITEM, "usr_a", "req_1", VERSIONS);

        assertEquals(6, saved.version());
        assertEquals(5, saved.baseVersion());
        assertEquals(6, repository.findById("itn_1").orElseThrow().latestVersion());
    }

    @Test
    @DisplayName("🔴 낡은 판 번호로 편집하면 거절되고 최신 번호를 알려준다")
    void editFromStaleVersionIsRejected() {
        seedAtVersion5();
        service.edit("itn_1", 5, ItineraryVersion.Operation.REPLACE_ITEM, "usr_a", "req_1", VERSIONS);

        // 이제 최신은 6. 화면이 아직 5 를 보고 있다고 가정한다.
        StaleItineraryVersionException e = assertThrows(StaleItineraryVersionException.class,
                () -> service.edit("itn_1", 5,
                        ItineraryVersion.Operation.REMOVE_ITEM, "usr_b", "req_2", VERSIONS));

        assertEquals(5, e.attemptedBaseVersion());
        // 🔴 최신 번호가 응답에 들어 있어야 한다. 없으면 화면이 무엇으로 갱신할지 모른다.
        assertEquals(6, e.latestVersion());
    }

    @Test
    @DisplayName("🔴 앞사람의 변경이 남아 있다 — 조용히 덮어쓰지 않는다")
    void firstEditSurvives() {
        seedAtVersion5();
        service.edit("itn_1", 5, ItineraryVersion.Operation.LOCK_ITEM, "usr_a", "req_1", VERSIONS);

        assertThrows(StaleItineraryVersionException.class,
                () -> service.edit("itn_1", 5,
                        ItineraryVersion.Operation.REMOVE_ITEM, "usr_b", "req_2", VERSIONS));

        ItineraryVersion v6 = repository.findVersion("itn_1", 6).orElseThrow();
        assertEquals(ItineraryVersion.Operation.LOCK_ITEM, v6.operation());
        assertEquals("usr_a", v6.createdBy());
    }

    /**
     * 🔴 이 테스트가 이 파일의 존재 이유다.
     *
     * <p>동행자 전원이 EDITOR 라서 두 사람이 <b>같은 순간에</b> 고치는 일이 실제로 생긴다.
     * 응용 계층의 사전 확인만으로는 확인과 저장 사이에 다른 요청이 끼어들 수 있다(경쟁 조건).
     *
     * <p>스레드 8개가 모두 5번을 바탕으로 동시에 편집을 시도한다.
     * <b>정확히 하나만 성공해야 한다.</b>
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
                final String req = "req_" + i;
                tasks.add(() -> {
                    startTogether.await();          // 🔴 동시에 출발시킨다
                    try {
                        service.edit("itn_1", 5,
                                ItineraryVersion.Operation.REPLACE_ITEM, user, req, VERSIONS);
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

        // 🔴 핵심 단정 — 하나만 통과했다
        assertEquals(1, succeeded.get(), "정확히 하나만 성공해야 한다");
        assertEquals(threads - 1, rejected.get(), "나머지는 전부 409 로 거절돼야 한다");

        // 🔴 판이 건너뛰어지지 않았다. 6 만 있고 7 은 없다.
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
            ItineraryVersion v = service.edit("itn_1", base,
                    ItineraryVersion.Operation.REORDER, "usr_a", "req_" + base, VERSIONS);
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
        assertTrue(!missingDataset.isComplete(), "dataset 이 없으면 불완전해야 한다");
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
