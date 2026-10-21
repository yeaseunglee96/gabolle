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
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 장소 상세 사실 산출물을 한 번 넣는다 — S15P21E201-1886. 모양은 {@link PlaceDetailExtrasLoader} 머리말.
 *
 * <pre>
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=dev \
 *   --gabolle.place.loader.place-detail-extras=/load/place-detail-extras.ndjson
 * </pre>
 *
 * <p>수집분 버전 인자를 따로 받지 않는다. 줄마다 {@code sourceVersion} 을 들고 오고, 되돌리기도 그 값으로 한다.
 *
 * <p>프로퍼티를 안 주면 이 빈은 만들어지지도 않아 평소 기동에 영향이 없다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnExpression("'${gabolle.place.loader.place-detail-extras:}' != ''")
public class PlaceDetailExtrasLoaderRunner implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(PlaceDetailExtrasLoaderRunner.class);

	private static final int CHUNK = 500;

	private final PlaceDetailExtrasLoader loader;

	private final String path;

	public PlaceDetailExtrasLoaderRunner(PlaceDetailExtrasLoader loader,
			@Value("${gabolle.place.loader.place-detail-extras:}") String path) {
		this.loader = loader;
		this.path = path;
	}

	@Override
	public void run(ApplicationArguments args) {
		Path file = Path.of(this.path);
		if (!Files.isRegularFile(file)) {
			// 경로를 틀리게 준 실행이 「0곳 적재」로 조용히 끝나면 값이 안 들어간 것을 아무도 모른다.
			throw new IllegalStateException("장소 상세 산출물을 찾을 수 없다: " + file.toAbsolutePath());
		}
		long startedAt = System.nanoTime();
		LOGGER.info("장소 상세 사실 적재를 시작한다 — 파일={}", file.toAbsolutePath());

		// 먼저 전부 검사한다. 틀린 줄이 있으면 한 행도 넣지 않고 여기서 멈춘다.
		List<PlaceDetailExtrasLoader.Row> rows = PlaceDetailExtrasLoader.readAll(file);
		OffsetDateTime createdAt = OffsetDateTime.now();
		PlaceDetailExtrasLoader.Result total = new PlaceDetailExtrasLoader.Result(0, 0, 0);
		for (int from = 0; from < rows.size(); from += CHUNK) {
			total = total.plus(this.loader.saveChunk(rows.subList(from, Math.min(rows.size(), from + CHUNK)), createdAt));
		}

		long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
		LOGGER.info("장소 상세 사실 적재를 마쳤다 — 읽은 줄 {} · {} · {}ms", rows.size(), total, elapsedMs);
	}

}
