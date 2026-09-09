package com.gabolle.backend.place.loader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 상가정보 CSV 를 한 번 읽어 {@code place} · {@code place_feature} 에 넣는다 — S15P21E201-636.
 *
 * <h2>쓰는 법</h2>
 *
 * <pre>
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=dev \
 *   --gabolle.place.loader.sbiz-csv=/data/부산_202606.csv \
 *   --gabolle.place.loader.dataset-version=sbiz-busan-202606
 * </pre>
 *
 * <h2>🔴 왜 API 가 아니라 실행 인자인가</h2>
 *
 * 적재는 <b>사람이 한 번 하는 일</b>이지 서비스가 제공하는 기능이 아니다. HTTP 로 열면 누가
 * 부를 수 있는지를 인가 정책으로 다시 정해야 하고, 실수로 두 번 눌리는 길도 생긴다. 그리고
 * 이 빈은 {@code gabolle.place.loader.sbiz-csv} 를 준 실행에서만 <b>존재</b>한다 — 안 주면
 * 클래스가 아예 안 올라가므로 보통의 서버 실행은 이 코드를 지나가지도 않는다.
 *
 * <h2>🔴 실패하면 그 자리에서 멈춘다</h2>
 *
 * 파일이 없거나 판이 다르면 예외를 던져 <b>기동을 세운다.</b> 반쯤 적재된 표는 빈 표보다
 * 나쁘다 — 추천은 나오는데 왜 이상한지 아무도 못 찾는다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnProperty(name = "gabolle.place.loader.sbiz-csv")
public class SbizLoaderRunner implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(SbizLoaderRunner.class);

	/**
	 * 한 트랜잭션에 넣는 장소 수. 🔴 전부를 한 트랜잭션에 넣으면 5만 행이 통째로 롤백될 수
	 * 있고, 한 행씩 넣으면 트랜잭션이 5만 번 열린다.
	 */
	private static final int CHUNK = 1_000;

	private final SbizPlaceLoader loader;

	private final String csvPath;

	private final String datasetVersion;

	public SbizLoaderRunner(SbizPlaceLoader loader,
			@Value("${gabolle.place.loader.sbiz-csv}") String csvPath,
			@Value("${gabolle.place.loader.dataset-version:}") String datasetVersion) {
		this.loader = loader;
		this.csvPath = csvPath;
		this.datasetVersion = datasetVersion;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (this.datasetVersion == null || this.datasetVersion.isBlank()) {
			// 🔴 기본값을 지어내지 않는다. datasetVersion 이 없으면 이 장소로 만든 추천이
			//    나중에 VERSION_UNRESOLVED 로 실패하고, 그때는 왜인지 알 방법이 없다.
			throw new IllegalStateException(
					"gabolle.place.loader.dataset-version 을 함께 줘야 한다 — 어느 수집분인지 적지 않으면 "
							+ "이 장소로 만든 추천을 나중에 되짚을 수 없다");
		}
		Path csv = Path.of(this.csvPath);
		if (!Files.isRegularFile(csv)) {
			throw new IllegalStateException("상가정보 CSV 를 찾을 수 없다: " + csv.toAbsolutePath());
		}

		OffsetDateTime collectedAt = OffsetDateTime.now();
		AtomicInteger inserted = new AtomicInteger();
		long startedAt = System.nanoTime();
		LOGGER.info("상가정보 적재를 시작한다 — 파일={} 수집분={}", csv.toAbsolutePath(), this.datasetVersion);

		SbizCsvReader.Counts counts = SbizCsvReader.readFoodRows(csv, CHUNK,
				chunk -> inserted.addAndGet(this.loader.saveChunk(chunk, this.datasetVersion, collectedAt)));

		long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
		// 🔴 "넣은 것" 과 "이미 있어 건너뛴 것" 을 갈라서 남긴다. 합계만 남기면 두 번째 실행이
		//    성공인지 아무것도 안 한 것인지 구분할 수 없다.
		LOGGER.info("상가정보 적재를 마쳤다 — {} · 새로 넣은 장소 {}곳 · 이미 있어 건너뛴 {}곳 · {}ms",
				counts, inserted.get(), counts.food() - inserted.get(), elapsedMs);
	}
}
