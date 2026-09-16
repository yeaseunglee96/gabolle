package com.gabolle.backend.place.loader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 관광공사 수집본을 읽어 비음식 장소를 넣는다 — S15P21E201-854.
 *
 * <p>{@link ResearchPlaceLoaderRunner}·{@link PopularityLoaderRunner} 와 같은 모양이다. 적재는
 * 사람이 한 번 하는 일이지 서비스가 제공하는 기능이 아니므로 API 가 아니라 실행 인자로 켠다.
 * 프로퍼티를 안 주면 이 빈은 만들어지지도 않아 평소 기동에 아무 영향이 없다.
 *
 * <pre>
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=dev \
 *   --gabolle.place.loader.tourapi=/data/tourapi-busan.ndjson \
 *   --gabolle.place.loader.dataset-version=tourapi-busan-20260911
 * </pre>
 *
 * <p>🔴 이 적재는 <b>다른 적재를 기다리지 않는다.</b> 관광공사 장소는 상가업소와 겹치지 않는
 * 새 장소이고 id 앞머리가 달라 순서와 무관하다. 인기도 적재와는 다르다 — 그쪽은 장소가 먼저
 * 있어야 붙는다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnProperty(name = "gabolle.place.loader.tourapi")
public class TourApiPlaceLoaderRunner implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(TourApiPlaceLoaderRunner.class);

	private static final int CHUNK = 200;

	private final TourApiPlaceLoader loader;

	private final String filePath;

	private final String datasetVersion;

	public TourApiPlaceLoaderRunner(TourApiPlaceLoader loader,
			@Value("${gabolle.place.loader.tourapi}") String filePath,
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
			throw new IllegalStateException("관광공사 수집본을 찾을 수 없다: " + file.toAbsolutePath());
		}

		OffsetDateTime collectedAt = OffsetDateTime.now();
		long startedAt = System.nanoTime();
		LOGGER.info("관광공사 장소 적재를 시작한다 — 파일={} 수집분={}", file.toAbsolutePath(), this.datasetVersion);

		TourApiPlaceReader.Loaded loaded = TourApiPlaceReader.read(file);
		List<TourApiPlaceRow> rows = loaded.rows();
		int inserted = 0;
		for (int from = 0; from < rows.size(); from += CHUNK) {
			int to = Math.min(from + CHUNK, rows.size());
			inserted += this.loader.saveChunk(rows.subList(from, to), this.datasetVersion, collectedAt);
		}
		long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

		// 🔴 넣은 것과 건너뛴 것을 갈라 남긴다. 건너뛴 것은 "이미 있다" 와 "이 파일 안에서
		//    중복" 두 가지가 섞여 있어 숫자 하나로는 무엇이 일어났는지 알 수 없다.
		LOGGER.info("관광공사 장소 적재를 마쳤다 — {} · 새로 넣은 장소 {}곳 · 건너뛴 {}곳 · {}ms",
				loaded.counts(), inserted, rows.size() - inserted, elapsedMs);
		// 갈래별로 몇 곳이 들어갔는지 남긴다 — 이것이 "바다를 골랐는데 0건" 이 풀렸는지를
		// 배포 뒤에 바로 확인할 수 있는 유일한 자리다.
		LOGGER.info("갈래별 — {}", byCategory(rows));
	}

	/** 갈래가 없는 것은 {@code (없음)} 으로 함께 센다. 그 수가 안 보이면 비운 결정이 잊힌다. */
	private static String byCategory(List<TourApiPlaceRow> rows) {
		Map<String, Integer> counts = new TreeMap<>();
		for (TourApiPlaceRow row : rows) {
			String category = TourApiCategory.of(row.contentId(), row.cat1(), row.cat3());
			counts.merge(category == null ? "(없음)" : category, 1, Integer::sum);
		}
		return counts.toString();
	}
}
