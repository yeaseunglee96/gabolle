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
 * 목록 근거 파일을 읽어 인기도 점수를 넣는다 — S15P21E201-826.
 *
 * <p>{@link ResearchPlaceLoaderRunner} 와 같은 모양이다. 적재는 사람이 한 번 하는 일이지
 * 서비스가 제공하는 기능이 아니므로 API 가 아니라 실행 인자로 켠다. 프로퍼티를 안 주면 이
 * 빈은 만들어지지도 않아 평소 기동에 아무 영향이 없다.
 *
 * <pre>
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=dev \
 *   --gabolle.place.loader.truth-signals=/data/truth-linked.ndjson \
 *   --gabolle.place.loader.dataset-version=truth-busan-202609
 * </pre>
 *
 * <p>장소를 먼저 넣어야 한다. 목록 근거는 장소에 붙는 값이라, 장소가 없으면 그 줄은 조용히
 * 건너뛴다({@link PopularityScoreLoader}).
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnProperty(name = "gabolle.place.loader.truth-signals")
public class PopularityLoaderRunner implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(PopularityLoaderRunner.class);

	private static final int CHUNK = 500;

	private final PopularityScoreLoader loader;

	private final String filePath;

	private final String datasetVersion;

	public PopularityLoaderRunner(PopularityScoreLoader loader,
			@Value("${gabolle.place.loader.truth-signals}") String filePath,
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
							+ "이 점수로 만든 추천을 나중에 되짚을 수 없다");
		}
		Path file = Path.of(this.filePath);
		if (!Files.isRegularFile(file)) {
			throw new IllegalStateException("목록 근거 파일을 찾을 수 없다: " + file.toAbsolutePath());
		}

		OffsetDateTime collectedAt = OffsetDateTime.now();
		AtomicInteger inserted = new AtomicInteger();
		long startedAt = System.nanoTime();
		LOGGER.info("목록 근거 적재를 시작한다 — 파일={} 수집분={}", file.toAbsolutePath(), this.datasetVersion);

		TruthSignalReader.Loaded loaded = TruthSignalReader.read(file);
		// 목록의 무게는 이 파일 전체의 분포에서 나온다 — 한 줄만 봐서는 못 정한다.
		ListRarity rarity = ListRarity.from(loaded.rows());
		for (int from = 0; from < loaded.rows().size(); from += CHUNK) {
			int to = Math.min(from + CHUNK, loaded.rows().size());
			inserted.addAndGet(this.loader.saveChunk(loaded.rows().subList(from, to), rarity,
					this.datasetVersion, collectedAt));
		}
		TruthSignalReader.Counts counts = loaded.counts();

		long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
		// 넣은 것과 건너뛴 것을 갈라 남긴다. 건너뛴 것은 두 가지가 섞여 있다 — 이미 있는
		// 것과, 아직 장소가 없어 붙일 데가 없는 것. 합계만 남기면 그 둘을 못 가른다.
		LOGGER.info("목록 근거 적재를 마쳤다 — {} · 새로 넣은 점수 {}곳 · 건너뛴 {}곳 · {}ms",
				counts, inserted.get(), counts.usable() - inserted.get(), elapsedMs);
	}
}
