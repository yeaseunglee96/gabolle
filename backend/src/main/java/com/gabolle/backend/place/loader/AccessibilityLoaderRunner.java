package com.gabolle.backend.place.loader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 무장애 수집본을 읽어 접근성 표식을 붙인다 — S15P21E201-331.
 *
 * <pre>
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=dev \
 *   --gabolle.place.loader.barrier-free=/load/tourapi-barrier-free-busan.ndjson \
 *   --gabolle.place.loader.dataset-version=tourapi-busan-20260911
 * </pre>
 *
 * <p>장소 적재를 먼저 돌려야 한다. 무장애 자료의 179곳은 전부 관광공사 656곳 안에 있으므로
 * 장소 적재가 끝났으면 붙을 자리가 다 있다 — "붙일 장소가 없어 넘긴" 수가 0 이 정상이다.
 * 그 수가 0 이 아니면 장소 적재를 안 돌렸거나 수집분이 어긋난 것이다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnProperty(name = "gabolle.place.loader.barrier-free")
public class AccessibilityLoaderRunner implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(AccessibilityLoaderRunner.class);

	private static final int CHUNK = 200;

	private final AccessibilityLoader loader;

	private final String filePath;

	private final String datasetVersion;

	public AccessibilityLoaderRunner(AccessibilityLoader loader,
			@Value("${gabolle.place.loader.barrier-free}") String filePath,
			@Value("${gabolle.place.loader.dataset-version:}") String datasetVersion) {
		this.loader = loader;
		this.filePath = filePath;
		this.datasetVersion = datasetVersion;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (this.datasetVersion == null || this.datasetVersion.isBlank()) {
			throw new IllegalStateException(
					"gabolle.place.loader.dataset-version 을 함께 줘야 한다 — 어느 수집분에서 확인한 "
							+ "접근성인지 적지 않으면 나중에 되짚을 수 없다");
		}
		Path file = Path.of(this.filePath);
		if (!Files.isRegularFile(file)) {
			throw new IllegalStateException("무장애 수집본을 찾을 수 없다: " + file.toAbsolutePath());
		}

		OffsetDateTime collectedAt = OffsetDateTime.now();
		long startedAt = System.nanoTime();
		LOGGER.info("접근성 표식 적재를 시작한다 — 파일={} 수집분={}", file.toAbsolutePath(), this.datasetVersion);

		BarrierFreeReader.Loaded loaded = BarrierFreeReader.read(file);
		List<BarrierFreeRow> rows = loaded.rows();
		AccessibilityLoader.Result result = new AccessibilityLoader.Result(0, 0, 0);
		for (int from = 0; from < rows.size(); from += CHUNK) {
			int to = Math.min(from + CHUNK, rows.size());
			result = result.plus(this.loader.saveChunk(rows.subList(from, to), this.datasetVersion, collectedAt));
		}
		long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

		LOGGER.info("접근성 표식 적재를 마쳤다 — {} · {} · {}ms", loaded.counts(), result, elapsedMs);
		if (result.noPlace() > 0) {
			LOGGER.warn("붙일 장소가 없어 넘긴 줄이 {}개다. 무장애 179곳은 전부 관광공사 수집본 안에 있으므로 "
					+ "0 이 정상이다 — 장소 적재(--gabolle.place.loader.tourapi)를 먼저 돌렸는지 확인해야 한다",
					result.noPlace());
		}
	}
}
