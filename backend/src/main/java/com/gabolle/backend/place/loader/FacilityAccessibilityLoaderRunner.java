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
 * 실태조사 가공본을 읽어 접근성 표식을 붙인다 — S15P21E201-1365.
 *
 * <pre>
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=dev \
 *   --gabolle.place.loader.bf-facility=/load/place-accessibility.ndjson \
 *   --gabolle.place.loader.dataset-version=bf-facility-busan-20260921
 * </pre>
 *
 * <p>장소 적재를 먼저 돌려야 한다. 다만 무장애 적재와 달리 <b>"붙일 장소가 없어 넘긴" 수가
 * 0 이 아닌 것이 정상이다.</b> 실태조사에서 이름이 맞은 곳 중 관광공사분은 대부분 목록에만
 * 있고 장소로 안 들어갔다 (실측 62곳 중 13곳만 DB 에 있었다). 상가분은 거의 다 들어가 있다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnProperty(name = "gabolle.place.loader.bf-facility")
public class FacilityAccessibilityLoaderRunner implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(FacilityAccessibilityLoaderRunner.class);

	private static final int CHUNK = 200;

	private final FacilityAccessibilityLoader loader;

	private final String filePath;

	private final String datasetVersion;

	public FacilityAccessibilityLoaderRunner(FacilityAccessibilityLoader loader,
			@Value("${gabolle.place.loader.bf-facility}") String filePath,
			@Value("${gabolle.place.loader.dataset-version:}") String datasetVersion) {
		this.loader = loader;
		this.filePath = filePath;
		this.datasetVersion = datasetVersion;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (this.datasetVersion == null || this.datasetVersion.isBlank()) {
			throw new IllegalStateException(
					"gabolle.place.loader.dataset-version 을 함께 줘야 한다 — 어느 조사분에서 확인한 "
							+ "접근성인지 적지 않으면 나중에 되짚을 수 없다");
		}
		Path file = Path.of(this.filePath);
		if (!Files.isRegularFile(file)) {
			throw new IllegalStateException("실태조사 가공본을 찾을 수 없다: " + file.toAbsolutePath());
		}

		OffsetDateTime collectedAt = OffsetDateTime.now();
		long startedAt = System.nanoTime();
		LOGGER.info("실태조사 접근성 표식 적재를 시작한다 — 파일={} 조사분={}", file.toAbsolutePath(),
				this.datasetVersion);

		FacilityAccessibilityReader.Loaded loaded = FacilityAccessibilityReader.read(file);
		List<FacilityAccessibilityRow> rows = loaded.rows();
		FacilityAccessibilityLoader.Result result = new FacilityAccessibilityLoader.Result(0, 0, 0);
		for (int from = 0; from < rows.size(); from += CHUNK) {
			int to = Math.min(from + CHUNK, rows.size());
			result = result.plus(this.loader.saveChunk(rows.subList(from, to), this.datasetVersion, collectedAt));
		}
		long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

		LOGGER.info("실태조사 접근성 표식 적재를 마쳤다 — {} · {} · {}ms", loaded.counts(), result, elapsedMs);

		// 🔴 이 셋은 조용히 지나가면 안 되는 것들이라 따로 띄운다.
		if (loaded.counts().idMismatch() > 0) {
			LOGGER.error("장소 id 가 우리 계산과 다른 줄이 {}개다. 0 이 정상이다 — 데이터 파트의 계산과 "
					+ "이쪽 계산이 갈렸다는 뜻이고, 어느 쪽이 틀렸든 엉뚱한 장소에 붙을 수 있다",
					loaded.counts().idMismatch());
		}
		if (loaded.counts().unknownSource() > 0) {
			LOGGER.error("모르는 출처라 넘긴 줄이 {}개다. 아는 것은 TOURAPI 와 SBIZ 둘뿐이다",
					loaded.counts().unknownSource());
		}
		if (result.inserted() == 0) {
			LOGGER.warn("새로 붙인 표식이 0개다. 장소 적재를 안 돌렸거나, 이미 다 붙어 있거나, "
					+ "가공본이 비었다 — 위의 세어 둔 수로 어느 쪽인지 갈린다");
		}
	}
}
