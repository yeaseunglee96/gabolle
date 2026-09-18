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
 * 축제 수집본을 읽어 회차를 적재한다 — S15P21E201-863.
 *
 * <pre>
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=dev \
 *   --gabolle.place.loader.festival=/load/tourapi-festival-busan.ndjson
 * </pre>
 *
 * <p>장소 적재를 먼저 돌려야 한다. 축제도 관광공사 수집본 안의 장소이므로 장소 적재가 끝났으면
 * 붙을 자리가 다 있다 — "장소가 없어 넘긴" 수가 0 이 정상이다. 그 수가 0 이 아니면 장소 적재를
 * 안 돌렸거나 수집분이 어긋난 것이다.
 *
 * <p>🔴 {@code dataset-version} 을 요구하지 않는다. 접근성·영업시간 적재는 <b>장소에 붙는 표식</b>을
 * 만들고 그 표식 행에 어느 수집분에서 나왔는지 적는 칸이 있다. 이쪽이 만드는 것은 표식이 아니라
 * <b>회차 그 자체</b>이고 그 표에는 그 칸이 없다 — 없는 칸을 위해 인자를 요구하면 돌리는 사람이
 * 무엇을 적어야 할지 알 수 없다.
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
