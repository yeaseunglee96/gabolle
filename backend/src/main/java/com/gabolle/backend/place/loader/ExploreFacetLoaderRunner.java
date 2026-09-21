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
 * 관광공사 수집본을 읽어 로컬 탐색 표식을 붙인다.
 *
 * <p>프로퍼티를 안 주면 이 빈이 만들어지지도 않아 평소 기동에 영향이 없다.
 *
 * <pre>
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=dev \
 *   --gabolle.place.loader.explore-facets=/data/tourapi-busan.ndjson \
 *   --gabolle.place.loader.dataset-version=tourapi-busan-20260911
 * </pre>
 *
 * <p>장소 적재를 먼저 돌려야 한다. 순서가 뒤집히면 실패하지 않고 "붙일 장소가 없어 넘긴"
 * 수만 남는다.
 *
 * <p>입력 파일과 판독기가 장소 적재와 같으므로 식당은 여기서도 넘어간다 — 탐색 여덟 갈래
 * 어디에도 안 든다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnProperty(name = "gabolle.place.loader.explore-facets")
public class ExploreFacetLoaderRunner implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(ExploreFacetLoaderRunner.class);

	private static final int CHUNK = 200;

	private final ExploreFacetLoader loader;

	private final String filePath;

	private final String datasetVersion;

	public ExploreFacetLoaderRunner(ExploreFacetLoader loader,
			@Value("${gabolle.place.loader.explore-facets}") String filePath,
			@Value("${gabolle.place.loader.dataset-version:}") String datasetVersion) {
		this.loader = loader;
		this.filePath = filePath;
		this.datasetVersion = datasetVersion;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (this.datasetVersion == null || this.datasetVersion.isBlank()) {
			throw new IllegalStateException(
					"gabolle.place.loader.dataset-version 을 함께 줘야 한다 — 어느 수집분에서 붙인 표식인지 "
							+ "적지 않으면 나중에 되짚을 수 없다");
		}
		Path file = Path.of(this.filePath);
		if (!Files.isRegularFile(file)) {
			throw new IllegalStateException("관광공사 수집본을 찾을 수 없다: " + file.toAbsolutePath());
		}

		OffsetDateTime collectedAt = OffsetDateTime.now();
		long startedAt = System.nanoTime();
		LOGGER.info("로컬 탐색 표식 적재를 시작한다 — 파일={} 수집분={}", file.toAbsolutePath(), this.datasetVersion);

		TourApiPlaceReader.Loaded loaded = TourApiPlaceReader.read(file);
		List<TourApiPlaceRow> rows = loaded.rows();
		ExploreFacetLoader.Result result = new ExploreFacetLoader.Result(0, 0, 0, 0);
		for (int from = 0; from < rows.size(); from += CHUNK) {
			int to = Math.min(from + CHUNK, rows.size());
			result = result.plus(this.loader.saveChunk(rows.subList(from, to), this.datasetVersion, collectedAt));
		}
		long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

		LOGGER.info("로컬 탐색 표식 적재를 마쳤다 — {} · {} · {}ms", loaded.counts(), result, elapsedMs);
		// 갈래별 수가 화면의 여덟 줄에 그대로 나타난다 — 어느 갈래가 비어 있는지는 이 줄로 본다.
		LOGGER.info("갈래별 — {}", byFacet(rows));
	}

	private static String byFacet(List<TourApiPlaceRow> rows) {
		Map<String, Integer> counts = new TreeMap<>();
		for (TourApiPlaceRow row : rows) {
			for (String facet : TourApiExploreFacet.of(row.contentTypeId(), row.cat1(), row.cat3())) {
				counts.merge(facet, 1, Integer::sum);
			}
		}
		return counts.toString();
	}
}
