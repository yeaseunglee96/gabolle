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

import tools.jackson.databind.ObjectMapper;

/**
 * 지하철 출구 안내 산출물을 읽어 이미 있는 장소에 붙인다 — S15P21E201-479.
 *
 * <p>{@link PlacePhotoLoaderRunner} 와 같은 모양이다. 프로퍼티를 안 주면 이 빈이 만들어지지도
 * 않아 평소 기동에 아무 영향이 없다.
 *
 * <pre>
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=db \
 *   --gabolle.place.loader.subway-exit=/data/subway-exit-busan.ndjson
 * </pre>
 *
 * <p>🔴 <b>장소 적재를 먼저 돌려야 한다.</b> 순서가 뒤집히면 <b>실패하지 않고</b> "붙일 장소
 * 없어 넘김" 숫자만 남는다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnProperty(name = "gabolle.place.loader.subway-exit")
public class SubwayExitLoaderRunner implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(SubwayExitLoaderRunner.class);

	private final SubwayExitLoader loader;

	private final ObjectMapper objectMapper;

	private final String filePath;

	public SubwayExitLoaderRunner(SubwayExitLoader loader, ObjectMapper objectMapper,
			@Value("${gabolle.place.loader.subway-exit}") String filePath) {
		this.loader = loader;
		this.objectMapper = objectMapper;
		this.filePath = filePath;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		Path file = Path.of(this.filePath);
		if (!Files.isRegularFile(file)) {
			throw new IllegalStateException("지하철 출구 산출물을 찾을 수 없다: " + file.toAbsolutePath());
		}

		LOGGER.info("지하철 출구 적재를 시작한다 — 파일={}", file.toAbsolutePath());
		List<SubwayExitRow> rows = new SubwayExitReader(this.objectMapper).read(file);

		SubwayExitLoader.Result result = this.loader.load(rows);

		LOGGER.info("지하철 출구 적재 끝 — 읽은 줄 {} · {}", rows.size(), result);
		if (result.noPlace() > 0) {
			LOGGER.warn("붙일 장소가 없어 넘긴 줄이 {} 개다 — 장소 적재를 먼저 돌렸는지 확인한다", result.noPlace());
		}
	}
}
