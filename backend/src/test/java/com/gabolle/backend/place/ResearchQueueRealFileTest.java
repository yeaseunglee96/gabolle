package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gabolle.backend.place.loader.ResearchQueueReader;
import com.gabolle.backend.place.loader.SbizRow;

/**
 * 진짜 대기열 줄로 판독기를 검증한다 — S15P21E201-804.
 *
 * <p>{@code ResearchQueueLoaderIntegrationTest} 는 세 줄만 재므로 "전체가 우리가 생각한 모양인가"
 * 를 알 수 없다. 한 줄만 달라도 조용히 버려지고, 그러면 적재 뒤에 장소가 몇 곳 비는데 아무도
 * 눈치채지 못한다.
 *
 * <h2>표본 200줄 상주</h2>
 * 실제 대기열(2,355줄 · 547KB)에서 앞 200줄을 떠서 {@code src/test/resources} 에 두었다. 전체를
 * 넣기엔 크고, 형식이 우리 가정과 맞는지는 표본으로 충분하다. 이 저장소는 건너뛴 검사가 하나라도
 * 있으면 CI 가 빨개지므로({@code .gitlab-ci.yml} 의 건너뜀 검사) 조건부로 건너뛰지 않는다.
 *
 * <h2>전체 파일 검증</h2>
 * {@code GABOLLE_RESEARCH_QUEUE} 에 전체 파일 경로를 주면 2,355줄을 그대로 잰다. 표본이 통과해도
 * 전체에 이상한 줄이 있을 수 있어, 적재 전에 한 번 돌려 보는 자리로 남긴다.
 *
 * <pre>
 * git show origin/bigData/dev:bigData/research/data/queue.ndjson &gt; /tmp/queue.ndjson
 * GABOLLE_RESEARCH_QUEUE=/tmp/queue.ndjson ./gradlew test --tests '*ResearchQueueRealFileTest'
 * </pre>
 */
class ResearchQueueRealFileTest {

    private static final String SAMPLE = "/research/queue-sample.ndjson";

    private static final int SAMPLE_LINES = 200;

    @TempDir
    Path tempDir;

    /** 상주 표본을 임시 파일로 풀어 준다 — 판독기가 경로를 받기 때문이다. */
    private Path sampleFile() {
        try (InputStream in = getClass().getResourceAsStream(SAMPLE)) {
            assertThat(in).as("표본 파일이 없다: %s", SAMPLE).isNotNull();
            Path file = this.tempDir.resolve("queue-sample.ndjson");
            Files.write(file, in.readAllBytes());
            return file;
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static List<SbizRow> readAll(Path queue, ResearchQueueReader.Counts[] out) {
        List<SbizRow> rows = new ArrayList<>();
        out[0] = ResearchQueueReader.read(queue, 500, rows::addAll);
        return rows;
    }

    @Test
    @DisplayName("표본 200줄이 한 줄도 안 버려지고 읽힌다")
    void everySampleLineIsUsable() {
        ResearchQueueReader.Counts[] counts = new ResearchQueueReader.Counts[1];
        List<SbizRow> rows = readAll(sampleFile(), counts);

        assertThat(counts[0].total()).isEqualTo(SAMPLE_LINES);
        // 버려진 줄이 하나라도 있으면 그만큼 장소가 안 들어가고, 그 사실은 적재 로그를
        // 자세히 읽어야만 보인다.
        assertThat(counts[0].skippedBroken()).isZero();
        assertThat(counts[0].skippedNoCoordinate()).isZero();
        assertThat(rows).hasSize(SAMPLE_LINES);
    }

    @Test
    @DisplayName("표본의 상가업소번호가 전부 다르다")
    void sampleStoreNumbersAreUnique() {
        List<SbizRow> rows = readAll(sampleFile(), new ResearchQueueReader.Counts[1]);

        Set<String> seen = new HashSet<>();
        List<String> duplicated = rows.stream().map(SbizRow::storeId).filter(id -> !seen.add(id)).toList();

        // 적재기는 같은 번호를 건너뛰므로 중복이 있으면 그 수만큼 장소가 덜 들어간다.
        assertThat(duplicated).isEmpty();
    }

    @Test
    @DisplayName("표본 좌표가 전부 부산 안에 있다")
    void sampleCoordinatesAreInsideBusan() {
        List<SbizRow> rows = readAll(sampleFile(), new ResearchQueueReader.Counts[1]);

        assertThat(outsideBusan(rows)).isEmpty();
    }

    @Test
    @DisplayName("전체 파일을 주면 2,355줄을 그대로 읽는다")
    void theWholeFileIsUsableWhenProvided() {
        Path queue = wholeFile();
        if (queue == null) {
            // 전체 파일은 bigData 쪽 브랜치에 있어 대부분의 PC 와 CI 에는 없다. 없으면
            // 표본으로 대신 재고, 이 검사가 조건부로 건너뛰지 않게 한다.
            queue = sampleFile();
        }
        ResearchQueueReader.Counts[] counts = new ResearchQueueReader.Counts[1];
        List<SbizRow> rows = readAll(queue, counts);

        assertThat(counts[0].skippedBroken()).isZero();
        assertThat(counts[0].skippedNoCoordinate()).isZero();
        assertThat(counts[0].usable()).isEqualTo(counts[0].total());
        assertThat(outsideBusan(rows)).isEmpty();
    }

    private static Path wholeFile() {
        String path = System.getenv("GABOLLE_RESEARCH_QUEUE");
        if (path == null || path.isBlank()) {
            return null;
        }
        Path file = Path.of(path);
        return Files.isRegularFile(file) ? file : null;
    }

    /** 부산 경계를 넉넉히 감싼 상자. 정확한 경계 판정이 아니라 완전히 엉뚱한 값을 잡는 그물이다. */
    private static List<SbizRow> outsideBusan(List<SbizRow> rows) {
        return rows.stream()
                .filter(r -> r.lat() < 34.8 || r.lat() > 35.5 || r.lng() < 128.7 || r.lng() > 129.4)
                .toList();
    }
}
