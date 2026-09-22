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
 * OSM 부산 장소 파일을 한 번 읽어 {@code place} 에 넣는다.
 *
 * <pre>
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=dev \
 *   --gabolle.place.loader.osm-poi=/data/pbf/poi.ndjson \
 *   --gabolle.place.loader.dataset-version=osm-busan-2026-09
 * </pre>
 *
 * <p>API 가 아니라 실행 인자인 것은 적재가 사람이 한 번 하는 일이어서다 —
 * {@code SbizLoaderRunner} 와 같은 이유다. 프로퍼티를 안 주면 이 빈은 아예 안 올라간다.
 *
 * <p>🔴 <b>끝나면 무엇이 몇 곳 들어갔는지를 찍는다.</b> 「새로 넣은 N · 이미 있어 넘긴 M ·
 * 이름 없어 버린 K · 갈래 몰라 버린 L」. 합계만 남기면 두 번째 실행이 성공인지 아무것도 안 한
 * 것인지 구분할 수 없고, 「생각보다 적게 들어갔다」의 이유도 못 찾는다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnProperty(name = "gabolle.place.loader.osm-poi")
public class OsmPlaceLoaderRunner implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(OsmPlaceLoaderRunner.class);

	/**
	 * 한 트랜잭션에 넣는 장소 수. 전부를 한 트랜잭션에 넣으면 파일 전체가 통째로 롤백될 수 있고,
	 * 한 행씩 넣으면 트랜잭션이 행 수만큼 열린다.
	 */
	private static final int CHUNK = 1_000;

	private final OsmPlaceLoader loader;

	private final String poiPath;

	private final String datasetVersion;

	public OsmPlaceLoaderRunner(OsmPlaceLoader loader,
			@Value("${gabolle.place.loader.osm-poi}") String poiPath,
			@Value("${gabolle.place.loader.dataset-version:}") String datasetVersion) {
		this.loader = loader;
		this.poiPath = poiPath;
		this.datasetVersion = datasetVersion;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (this.datasetVersion == null || this.datasetVersion.isBlank()) {
			// 기본값을 지어내지 않는다. 이 값이 없으면 이 장소로 만든 추천이 나중에
			// VERSION_UNRESOLVED 로 실패하고, 그때는 왜인지 알 방법이 없다.
			throw new IllegalStateException(
					"gabolle.place.loader.dataset-version 을 함께 줘야 한다 — 어느 수집분인지 적지 않으면 "
							+ "이 장소로 만든 추천을 나중에 되짚을 수 없다");
		}
		Path poi = Path.of(this.poiPath);
		if (!Files.isRegularFile(poi)) {
			throw new IllegalStateException("OSM 장소 파일을 찾을 수 없다: " + poi.toAbsolutePath());
		}

		OffsetDateTime collectedAt = OffsetDateTime.now();
		AtomicInteger inserted = new AtomicInteger();
		long startedAt = System.nanoTime();
		LOGGER.info("OSM 장소 적재를 시작한다 — 파일={} 수집분={}", poi.toAbsolutePath(), this.datasetVersion);

		OsmPoiReader.Counts counts = OsmPoiReader.read(poi, CHUNK,
				(chunk) -> inserted.addAndGet(this.loader.saveChunk(chunk, this.datasetVersion, collectedAt)));

		long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
		LOGGER.info("OSM 장소 적재를 마쳤다 — {} · 새로 넣은 장소 {}곳 · 이미 있어 건너뛴 {}곳 · {}ms",
				counts, inserted.get(), counts.taken() - inserted.get(), elapsedMs);
		// ODbL 은 출처 표기가 조건이다. 적재 로그에 남겨 두면 나중에 "이 행들이 어디서 왔나" 를
		// 로그만 보고도 안다 — 화면 표기는 이것으로 대신할 수 없다.
		LOGGER.info("이 장소들의 출처는 © OpenStreetMap contributors (ODbL) 다 — 화면에 표기가 필요하다");
	}
}
