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
 * 사진 수집본을 읽어 이미 있는 장소에 붙인다.
 *
 * <p>프로퍼티를 안 주면 이 빈이 만들어지지도 않아 평소 기동에 아무 영향이 없다.
 *
 * <pre>
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=db \
 *   --gabolle.place.loader.photos=/data/festival-photos-busan.ndjson
 * </pre>
 *
 * <p>파일 이름은 여기서 정한 것이 아니다. 데이터 파트 수집기의 출력이 두 파트 사이의 계약이라
 * 이름도 그쪽이 정본이다.
 *
 * <p>장소 적재를 먼저 돌려야 한다. 순서가 뒤집히면 실패하지 않고 "붙일 장소 없어 넘김" 숫자만
 * 남아, 그 수를 로그에 따로 찍는다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnProperty(name = "gabolle.place.loader.photos")
public class PlacePhotoLoaderRunner implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(PlacePhotoLoaderRunner.class);

	private final PlacePhotoLoader loader;

	private final ObjectMapper objectMapper;

	private final String filePath;

	public PlacePhotoLoaderRunner(PlacePhotoLoader loader, ObjectMapper objectMapper,
			@Value("${gabolle.place.loader.photos}") String filePath) {
		this.loader = loader;
		this.objectMapper = objectMapper;
		this.filePath = filePath;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		Path file = Path.of(this.filePath);
		if (!Files.isRegularFile(file)) {
			throw new IllegalStateException("사진 수집본을 찾을 수 없다: " + file.toAbsolutePath());
		}

		LOGGER.info("사진 적재를 시작한다 — 파일={}", file.toAbsolutePath());
		List<PlacePhotoRow> rows = new PlacePhotoReader(this.objectMapper).read(file);

		PlacePhotoLoader.Result result = this.loader.load(rows);

		LOGGER.info("사진 적재 끝 — 읽은 줄 {} · {}", rows.size(), result);
		if (result.noPlace() > 0) {
			// 조용히 넘어가면 "이 축제는 원래 사진이 없다" 로 오해하게 된다.
			LOGGER.warn("붙일 장소가 없어 넘긴 줄이 {} 개다 — 그 출처의 장소 적재를 먼저 돌렸는지, "
					+ "열쇠(contentid 또는 sourceType+sourceId)가 맞는지 확인한다", result.noPlace());
		}
	}
}
