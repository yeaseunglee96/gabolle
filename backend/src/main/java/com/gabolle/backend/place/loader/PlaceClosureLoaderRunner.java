package com.gabolle.backend.place.loader;

import java.nio.file.Files;
import java.nio.file.Path;
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
 * 폐업 여부를 적재한다. 프로퍼티를 안 주면 이 빈이 만들어지지 않아 평소 기동에 영향이 없다.
 *
 * <pre>
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=dev \
 *   --gabolle.place.loader.closures=/data/place-link.ndjson
 * </pre>
 *
 * <p>입력 파일은 {@code bigData/process/place-link.mjs} 가
 * {@code data/staged/place-link.ndjson} 으로 쓴다.
 *
 * <p>장소 적재를 먼저 돌려야 한다. 순서가 뒤집혀도 실패하지 않고 「장소없음」만 조용히
 * 늘어나므로, 그 수를 따로 찍고 비율이 크면 경고한다.
 *
 * <p>수집분(dataset-version)은 받지 않는다. 폐업은 장소 자체의 칸 하나라 어느 수집분에서 온
 * 값인지를 적을 자리가 없다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnProperty(name = "gabolle.place.loader.closures")
public class PlaceClosureLoaderRunner implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(PlaceClosureLoaderRunner.class);

	private static final int CHUNK = 500;

	/** 붙일 장소가 이 비율을 넘게 없으면 순서가 뒤집힌 것으로 본다. */
	private static final double NO_PLACE_WARN_RATIO = 0.5;

	private final PlaceClosureLoader loader;

	private final String filePath;

	public PlaceClosureLoaderRunner(PlaceClosureLoader loader,
			@Value("${gabolle.place.loader.closures}") String filePath) {
		this.loader = loader;
		this.filePath = filePath;
	}

	@Override
	public void run(ApplicationArguments args) {
		Path file = Path.of(this.filePath);
		if (!Files.isRegularFile(file)) {
			throw new IllegalStateException("장소↔인허가 이음 파일을 찾을 수 없다: " + file.toAbsolutePath());
		}

		long startedAt = System.nanoTime();
		LOGGER.info("폐업 적재를 시작한다 — 파일={}", file.toAbsolutePath());

		PlaceClosureReader.Loaded loaded = PlaceClosureReader.read(file);
		List<PlaceClosureRow> rows = loaded.rows();
		PlaceClosureLoader.Result result = new PlaceClosureLoader.Result(0, 0, 0, 0);
		for (int from = 0; from < rows.size(); from += CHUNK) {
			int to = Math.min(from + CHUNK, rows.size());
			result = result.plus(this.loader.saveChunk(rows.subList(from, to)));
		}
		long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

		LOGGER.info("폐업 적재를 마쳤다 — {} · {} · {}ms", loaded, result, elapsedMs);
		if (!rows.isEmpty() && result.noPlace() > rows.size() * NO_PLACE_WARN_RATIO) {
			LOGGER.warn("붙일 장소가 없어 넘긴 줄이 {}개다 (읽은 줄 {}개). 장소 적재를 먼저 돌렸는지, "
					+ "같은 수집분인지 확인하라 — 이 상태로는 폐업이 거의 안 반영된다",
					result.noPlace(), rows.size());
		}
		if (loaded.closedWithoutDate() > 0) {
			// 상태만 「폐업」이고 날짜가 없는 줄이다. 원본 규격이 바뀌면 여기가 커진다.
			LOGGER.warn("「폐업」인데 날짜가 없어 버린 줄이 {}개다. 원본 규격이 바뀌었는지 확인하라",
					loaded.closedWithoutDate());
		}
	}
}
