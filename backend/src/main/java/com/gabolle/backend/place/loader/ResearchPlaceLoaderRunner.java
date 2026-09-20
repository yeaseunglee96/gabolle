package com.gabolle.backend.place.loader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 조사를 마친 대기열의 장소를 장소 표에 넣는다.
 *
 * <p>{@link SbizLoaderRunner} 가 읽는 상가정보 원본 CSV 는 이 저장소에 없고 서버에 있다.
 * 조사 대기열 파일에는 우리가 쓰는 칸이 전부 들어 있어, 원본을 기다리지 않고 이쪽부터 넣는다.
 *
 * <p>두 적재기는 서로를 밀어내지 않는다. 둘 다 상가업소번호를 열쇠로 쓰고 장소 id 를 그
 * 번호에서 계산하므로, 나중에 원본 CSV 로 전체를 적재해도 여기 넣은 것은 이미 있는 것으로
 * 인식돼 건너뛰어진다. 저장 경로도 {@link SbizPlaceLoader#saveChunk} 하나를 그대로 쓴다 —
 * 넣는 규칙이 두 벌이 되면 언젠가 한쪽만 고쳐진다.
 *
 * <pre>
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=dev \
 *   --gabolle.place.loader.research-queue=/data/queue.ndjson \
 *   --gabolle.place.loader.dataset-version=research-busan-2355-202609
 * </pre>
 *
 * <p>API 가 아니라 실행 인자인 것은 적재가 사람이 한 번 하는 일이지 서비스가 제공하는 기능이
 * 아니어서다. 프로퍼티를 안 주면 이 빈은 만들어지지도 않아 평소 기동에 영향이 없다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnProperty(name = "gabolle.place.loader.research-queue")
public class ResearchPlaceLoaderRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResearchPlaceLoaderRunner.class);

    /** 한 트랜잭션에 넣는 장소 수. 파일이 작아 한 번에 넣어도 되지만 다른 적재와 규칙을 같게 둔다. */
    private static final int CHUNK = 500;

    private final SbizPlaceLoader loader;

    private final String queuePath;

    private final String datasetVersion;

    public ResearchPlaceLoaderRunner(SbizPlaceLoader loader,
            @Value("${gabolle.place.loader.research-queue}") String queuePath,
            @Value("${gabolle.place.loader.dataset-version:}") String datasetVersion) {
        this.loader = loader;
        this.queuePath = queuePath;
        this.datasetVersion = datasetVersion;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (this.datasetVersion == null || this.datasetVersion.isBlank()) {
            // 기본값을 지어내지 않는다. 이 값이 없으면 이 장소로 만든 추천이 나중에
            // VERSION_UNRESOLVED 로 실패하고, 그때는 어느 수집분이었는지 알 방법이 없다.
            throw new IllegalStateException(
                    "gabolle.place.loader.dataset-version 을 함께 줘야 한다 — 어느 수집분인지 적지 않으면 "
                            + "이 장소로 만든 추천을 나중에 되짚을 수 없다");
        }
        Path queue = Path.of(this.queuePath);
        if (!Files.isRegularFile(queue)) {
            throw new IllegalStateException("조사 대기열 파일을 찾을 수 없다: " + queue.toAbsolutePath());
        }

        OffsetDateTime collectedAt = OffsetDateTime.now();
        AtomicInteger inserted = new AtomicInteger();
        long startedAt = System.nanoTime();
        LOGGER.info("조사 대기열 적재를 시작한다 — 파일={} 수집분={}", queue.toAbsolutePath(), this.datasetVersion);

        ResearchQueueReader.Counts counts = ResearchQueueReader.read(queue, CHUNK,
                chunk -> inserted.addAndGet(this.loader.saveChunk(chunk, this.datasetVersion, collectedAt)));

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
        // "넣은 것" 과 "이미 있어 건너뛴 것" 을 갈라 남긴다. 합계만 남기면 두 번째 실행이
        // 성공인지 아무것도 안 한 것인지 구분할 수 없다.
        LOGGER.info("조사 대기열 적재를 마쳤다 — {} · 새로 넣은 장소 {}곳 · 이미 있어 건너뛴 {}곳 · {}ms",
                counts, inserted.get(), counts.usable() - inserted.get(), elapsedMs);
    }
}
