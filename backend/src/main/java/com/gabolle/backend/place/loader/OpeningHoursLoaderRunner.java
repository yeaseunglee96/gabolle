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
 * 정규화한 영업시간을 읽어 적재한다.
 *
 * <p>프로퍼티를 안 주면 이 빈이 만들어지지도 않아 평소 기동에 아무 영향이 없다.
 *
 * <pre>
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=dev \
 *   --gabolle.place.loader.opening-hours=/data/opening-hours.ndjson \
 *   --gabolle.place.loader.dataset-version=tourapi-busan-20260911
 * </pre>
 *
 * <p>파일 이름은 여기서 정한 것이 아니다. 정규화 스크립트의 출력이 데이터 파트와 이 적재기
 * 사이의 계약이라 이름도 그쪽이 정본이다.
 *
 * <p>장소 적재를 먼저 돌려야 한다({@code --gabolle.place.loader.tourapi}). 순서가 뒤집히면
 * 실패하지 않고 "붙일 장소가 없어 넘긴" 숫자만 남아, 그 수를 로그에 따로 찍는다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnProperty(name = "gabolle.place.loader.opening-hours")
public class OpeningHoursLoaderRunner implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(OpeningHoursLoaderRunner.class);

	private static final int CHUNK = 200;

	private final OpeningHoursLoader loader;

	private final String filePath;

	private final String datasetVersion;

	public OpeningHoursLoaderRunner(OpeningHoursLoader loader,
			@Value("${gabolle.place.loader.opening-hours}") String filePath,
			@Value("${gabolle.place.loader.dataset-version:}") String datasetVersion) {
		this.loader = loader;
		this.filePath = filePath;
		this.datasetVersion = datasetVersion;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (this.datasetVersion == null || this.datasetVersion.isBlank()) {
			throw new IllegalStateException(
					"gabolle.place.loader.dataset-version 을 함께 줘야 한다 — 어느 수집분의 영업시간인지 "
							+ "적지 않으면 값이 낡았는지 판단할 근거가 남지 않는다");
		}
		Path file = Path.of(this.filePath);
		if (!Files.isRegularFile(file)) {
			throw new IllegalStateException("정규화한 영업시간 파일을 찾을 수 없다: " + file.toAbsolutePath());
		}

		OffsetDateTime collectedAt = OffsetDateTime.now();
		long startedAt = System.nanoTime();
		LOGGER.info("영업시간 적재를 시작한다 — 파일={} 수집분={}", file.toAbsolutePath(), this.datasetVersion);

		OpeningHoursReader.Loaded loaded = OpeningHoursReader.read(file);
		List<OpeningHoursRow> rows = loaded.rows();
		OpeningHoursLoader.Result result = new OpeningHoursLoader.Result(0, 0, 0);
		for (int from = 0; from < rows.size(); from += CHUNK) {
			int to = Math.min(from + CHUNK, rows.size());
			result = result.plus(this.loader.saveChunk(rows.subList(from, to), this.datasetVersion, collectedAt));
		}
		long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

		LOGGER.info("영업시간 적재를 마쳤다 — {} · {} · {}ms", loaded.counts(), result, elapsedMs);
		if (result.noPlace() > 0) {
			// 일부러 안 넣은 갈래의 몫보다 크게 나오면 장소 적재를 안 돌렸거나 수집분이 어긋난
			// 것이다 — 로그를 나눠 그 구분이 되게 한다.
			LOGGER.warn("붙일 장소가 없어 넘긴 줄이 {}개다. 음식 장소를 일부러 안 넣은 몫(실측 328)보다 "
					+ "크면 장소 적재(--gabolle.place.loader.tourapi)를 먼저 돌렸는지 확인해야 한다",
					result.noPlace());
		}
	}
}
