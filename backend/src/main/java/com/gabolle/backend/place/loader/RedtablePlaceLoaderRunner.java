package com.gabolle.backend.place.loader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 레드테이블 식당 NDJSON 을 한 번 읽어 {@code place} 에 넣는다 (S15P21E201-1897).
 *
 * <pre>
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=dev \
 *   --gabolle.place.loader.redtable-places=/load/survey-redtable-places.ndjson \
 *   --gabolle.place.loader.dataset-version=survey-redtable-20260930
 * </pre>
 *
 * <p>프로퍼티를 안 주면 이 빈은 안 올라간다. 파일이 없거나, 수집분 버전이 없거나, 한 줄이라도 틀리면 기동을 세운다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnProperty(name = "gabolle.place.loader.redtable-places")
public class RedtablePlaceLoaderRunner implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(RedtablePlaceLoaderRunner.class);

	private static final int CHUNK = 500;

	private final RedtablePlaceLoader loader;

	private final String filePath;

	private final String datasetVersion;

	public RedtablePlaceLoaderRunner(RedtablePlaceLoader loader,
			@Value("${gabolle.place.loader.redtable-places}") String filePath,
			@Value("${gabolle.place.loader.dataset-version:}") String datasetVersion) {
		this.loader = loader;
		this.filePath = filePath;
		this.datasetVersion = datasetVersion;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (this.datasetVersion == null || this.datasetVersion.isBlank()) {
			throw new IllegalStateException(
					"gabolle.place.loader.dataset-version 을 함께 줘야 한다 — 어느 수집분인지 적지 않으면 "
							+ "이 장소로 만든 추천을 나중에 되짚을 수 없다");
		}
		Path file = Path.of(this.filePath);
		if (!Files.isRegularFile(file)) {
			throw new IllegalStateException("레드테이블 NDJSON 을 찾을 수 없다: " + file.toAbsolutePath());
		}
		OffsetDateTime collectedAt = OffsetDateTime.now();
		long startedAt = System.nanoTime();
		LOGGER.info("레드테이블 장소 적재를 시작한다 — 파일={} 수집분={}", file.toAbsolutePath(), this.datasetVersion);

		// 파일 전체를 먼저 검사한다 — 틀린 줄이 있으면 여기서 멈추고 한 행도 안 들어간다.
		List<RedtablePlaceRow> rows = RedtablePlaceReader.read(file);
		SamePlaceReport samePlaces = new SamePlaceReport();
		int inserted = 0;
		for (int from = 0; from < rows.size(); from += CHUNK) {
			inserted += this.loader.saveChunk(rows.subList(from, Math.min(from + CHUNK, rows.size())),
					this.datasetVersion, collectedAt, samePlaces);
		}
		long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
		// 넣은 것 · 이미 있어 건너뛴 것 · 같은 곳이라 막힌 것을 갈라 남긴다.
		LOGGER.info("레드테이블 장소 적재를 마쳤다 — 읽은 곳 {} · 새로 넣은 장소 {}곳 · 이미 있어 건너뛴 {}곳 · 갈래별 {} · {}ms",
				rows.size(), inserted, rows.size() - inserted - samePlaces.notInserted(), byCategory(rows), elapsedMs);
		samePlaces.finish(file, RedtablePlaceLoader.SOURCE_TYPE, this.datasetVersion, LOGGER);
	}

	private static Map<String, Integer> byCategory(List<RedtablePlaceRow> rows) {
		Map<String, Integer> counts = new TreeMap<>();
		rows.forEach(row -> counts.merge(RedtablePlaceLoader.categoryOf(row), 1, Integer::sum));
		return counts;
	}
}
