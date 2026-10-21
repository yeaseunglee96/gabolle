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
 * 공공기관 인증 식당 NDJSON 을 한 번 읽어 {@code place}·{@code place_feature} 에 넣는다 (S15P21E201-2051).
 *
 * <pre>
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=dev \
 *   --gabolle.place.loader.certified-restaurants=/load/certified-restaurants-20261013.ndjson \
 *   --gabolle.place.loader.dataset-version=certified-restaurants-20261013
 * </pre>
 *
 * <p>프로퍼티를 안 주면 이 빈은 안 올라간다. 파일이 없거나, 수집분 버전이 없거나, 한 줄이라도 틀리면 기동을 세운다.
 * 읽고도 안 넣는 줄(구·동을 모르는 줄·같은 번호의 남는 줄·주소와 구가 안 맞는 줄)은 멈추지 않고 <b>로그에 한 줄씩</b> 남긴다.
 * 마침 줄에 「적재를 마쳤다」가 들어 있어야 운영 절차의 {@code run} 함수(백엔드 {@code docs/PLACE-DATA-LOAD.md} 3절)가 컨테이너를
 * 스스로 내린다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnProperty(name = "gabolle.place.loader.certified-restaurants")
public class CertifiedRestaurantLoaderRunner implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(CertifiedRestaurantLoaderRunner.class);

	private static final int CHUNK = 500;

	private final CertifiedRestaurantLoader loader;

	private final String filePath;

	private final String datasetVersion;

	public CertifiedRestaurantLoaderRunner(CertifiedRestaurantLoader loader,
			@Value("${gabolle.place.loader.certified-restaurants}") String filePath,
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
							+ "이 장소와 표식을 나중에 되돌릴 수 없다");
		}
		Path file = Path.of(this.filePath);
		if (!Files.isRegularFile(file)) {
			throw new IllegalStateException("인증 식당 NDJSON 을 찾을 수 없다: " + file.toAbsolutePath());
		}
		OffsetDateTime collectedAt = OffsetDateTime.now();
		long startedAt = System.nanoTime();
		LOGGER.info("인증 식당 적재를 시작한다 — 파일={} 수집분={}", file.toAbsolutePath(), this.datasetVersion);

		// 파일 전체를 먼저 검사한다 — 틀린 줄이 있으면 여기서 멈추고 한 행도 안 들어간다.
		CertifiedRestaurantReader.Result read = CertifiedRestaurantReader.read(file);
		logUnused(read);

		List<CertifiedRestaurantRow> rows = read.rows();
		SamePlaceReport samePlaces = new SamePlaceReport();
		CertifiedRestaurantLoader.Result total = CertifiedRestaurantLoader.Result.empty();
		for (int from = 0; from < rows.size(); from += CHUNK) {
			total = total.plus(this.loader.saveChunk(rows.subList(from, Math.min(from + CHUNK, rows.size())),
					this.datasetVersion, collectedAt, samePlaces));
		}
		long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

		if (!total.missingExistingSourceIds().isEmpty()) {
			// 크면 운영이 아닌 DB 의 장소 번호로 만든 파일이다.
			LOGGER.warn("existingPlaceId 의 장소가 이 DB 에 없어 표식을 못 단 줄 {}개 — 번호: {}", total.missingExisting(),
					total.missingExistingSourceIds());
		}
		if (!total.sameExamples().isEmpty()) {
			LOGGER.info("같은 곳이 이미 있어 새로 안 만들고 표식만 단 줄 {}개 — 예: {}", total.markedSamePlace(),
					String.join(" · ", total.sameExamples()));
		}
		if (total.areaConflicts() > 0) {
			LOGGER.warn("이미 단 구·동과 이번 줄의 구·동이 달라 먼저 있던 것을 둔 장소 {}곳", total.areaConflicts());
		}
		// 넣은 것 · 이미 있던 것 · 같은 곳이라 막힌 것을 갈라 남긴다. 마침 줄에 「적재를 마쳤다」 — 운영 절차의 run() 이 이 말에서 끊는다.
		LOGGER.info("인증 식당 적재를 마쳤다 — 읽은 줄 {} · 넣을 곳 {} · 안 넣고 건너뜀 {}(구·동 모름 {} · 주소의 구와 안 맞음 {}) "
				+ "· 같은 번호의 남는 줄 {} · {} · 구·동별 {} · {}ms", read.linesRead(), rows.size(), read.skipped().size(),
				read.skippedCount(CertifiedRestaurantReader.Reason.UNKNOWN_AREA),
				read.skippedCount(CertifiedRestaurantReader.Reason.ADDRESS_GU_MISMATCH),
				read.droppedDuplicates().size(), total, byGu(rows), elapsedMs);
		samePlaces.finish(file, CertifiedRestaurantLoader.SOURCE_TYPE, this.datasetVersion, LOGGER);
	}

	/** 읽고도 안 넣은 줄을 한 줄씩 남긴다 — 무엇을 왜 뺐는지 로그만 보고 알 수 있어야 한다. */
	private static void logUnused(CertifiedRestaurantReader.Result read) {
		for (CertifiedRestaurantReader.Skipped skipped : read.skipped()) {
			LOGGER.warn("안 넣고 건너뜀({}) — {}", skipped.reason(), skipped);
		}
		for (CertifiedRestaurantReader.DroppedDuplicate dropped : read.droppedDuplicates()) {
			LOGGER.warn("같은 번호의 남는 줄이라 안 넣음 — {}", dropped);
		}
	}

	/** 구별 줄 수 — 로그에서 한눈에 치우친 곳을 본다. */
	private static Map<String, Integer> byGu(List<CertifiedRestaurantRow> rows) {
		Map<String, Integer> counts = new TreeMap<>();
		rows.forEach(row -> counts.merge(row.gu(), 1, Integer::sum));
		return counts;
	}
}
