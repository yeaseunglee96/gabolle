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
 * 축제 수집본을 읽어 회차를 적재한다.
 *
 * <pre>
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=dev \
 *   --gabolle.place.loader.festival=/load/tourapi-festival-busan.ndjson
 * </pre>
 *
 * <p>장소 적재를 먼저 돌려야 한다. 축제도 관광공사 수집본 안의 장소이므로 "장소가 없어 넘긴"
 * 수가 0 이 정상이다.
 *
 * <p>다른 적재와 달리 {@code dataset-version} 을 요구하지 않는다. 이쪽이 만드는 것은 표식이
 * 아니라 회차 그 자체이고, 그 표에는 수집분을 적는 칸이 없다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnProperty(name = "gabolle.place.loader.festival")
public class FestivalPeriodLoaderRunner implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(FestivalPeriodLoaderRunner.class);

	private static final int CHUNK = 200;

	private final FestivalPeriodLoader loader;

	private final String filePath;

	public FestivalPeriodLoaderRunner(FestivalPeriodLoader loader,
			@Value("${gabolle.place.loader.festival}") String filePath) {
		this.loader = loader;
		this.filePath = filePath;
	}

	@Override
	public void run(ApplicationArguments args) {
		Path file = Path.of(this.filePath);
		if (!Files.isRegularFile(file)) {
			throw new IllegalStateException("축제 수집본을 찾을 수 없다: " + file.toAbsolutePath());
		}

		OffsetDateTime collectedAt = OffsetDateTime.now();
		long startedAt = System.nanoTime();
		LOGGER.info("축제 회차 적재를 시작한다 — 파일={}", file.toAbsolutePath());

		FestivalPeriodReader.Loaded loaded = FestivalPeriodReader.read(file);
		List<FestivalPeriodRow> rows = loaded.rows();
		FestivalPeriodLoader.Result result = new FestivalPeriodLoader.Result(0, 0, 0);
		for (int from = 0; from < rows.size(); from += CHUNK) {
			int to = Math.min(from + CHUNK, rows.size());
			result = result.plus(this.loader.saveChunk(rows.subList(from, to), collectedAt));
		}
		long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

		LOGGER.info("축제 회차 적재를 마쳤다 — {} · {} · {}ms", loaded.counts(), result, elapsedMs);
		if (result.noPlace() > 0) {
			LOGGER.warn("장소가 없어 넘긴 줄이 {}개다 — 장소 적재(--gabolle.place.loader.tourapi)를 먼저 "
					+ "돌렸는지 확인해야 한다", result.noPlace());
		}
		if (loaded.counts().skippedNoPeriod() > 0) {
			LOGGER.warn("기간이 비어 있어 버린 축제가 {}개다. 날짜를 지어내 채우지 않는다 — 그러면 사람이 "
					+ "안 열리는 축제를 보러 간다", loaded.counts().skippedNoPeriod());
		}
	}
}
