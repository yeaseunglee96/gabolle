package com.gabolle.backend.place.loader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
 * 조사 대기열의 상호명에서 {@link DesiredFoodVocabulary}로 찾은 것을 {@code place_feature}에
 * 넣는다 — S15P21E201-448.
 *
 * <p>{@link ResearchQueueReader}가 이미 있는 대기열 파일을 그대로 다시 읽는다 — 새 산출물
 * 형식을 만들지 않는다. 이 대기열의 2,355곳은 이미 {@link SbizPlaceLoader}로 운영에
 * 실려 있으므로(S15P21E201-804), 이 실행기는 <b>장소를 새로 만들지 않고</b>
 * {@link PlaceFeatureLoader}로 사실만 덧붙인다 — {@link SbizPlaceLoader}가 "이미 있는
 * 장소는 건너뛴다" 는 규칙이라 재적재로는 새 표식이 못 붙기 때문이다.
 *
 * <pre>
 * git show origin/bigData/dev:bigData/research/data/queue.ndjson &gt; /tmp/queue.ndjson
 *
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=dev \
 *   --gabolle.place.loader.desired-food-queue=/tmp/queue.ndjson \
 *   --gabolle.place.loader.dataset-version=research-busan-2355-202609
 * </pre>
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnExpression("'${gabolle.place.loader.desired-food-queue:}' != ''")
public class DesiredFoodTagLoaderRunner implements ApplicationRunner {

	/** {@code place_feature.source_type}. */
	public static final String SOURCE_TYPE = "RESEARCH_DESIRED_FOOD";

	private static final Logger LOGGER = LoggerFactory.getLogger(DesiredFoodTagLoaderRunner.class);

	private static final int CHUNK = 500;

	private final PlaceFeatureLoader featureLoader;

	private final String queuePath;

	private final String datasetVersion;

	public DesiredFoodTagLoaderRunner(PlaceFeatureLoader featureLoader,
			@Value("${gabolle.place.loader.desired-food-queue:}") String queuePath,
			@Value("${gabolle.place.loader.dataset-version:}") String datasetVersion) {
		this.featureLoader = featureLoader;
		this.queuePath = queuePath;
		this.datasetVersion = datasetVersion;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (this.datasetVersion == null || this.datasetVersion.isBlank()) {
			throw new IllegalStateException(
					"gabolle.place.loader.dataset-version 을 함께 줘야 한다 — 어느 산출물인지 적지 않으면 "
							+ "이 값으로 만든 추천을 나중에 되짚을 수 없다");
		}
		Path file = Path.of(this.queuePath);
		if (!Files.isRegularFile(file)) {
			throw new IllegalStateException("조사 대기열을 찾을 수 없다: " + file.toAbsolutePath());
		}

		OffsetDateTime collectedAt = OffsetDateTime.now();
		long startedAt = System.nanoTime();
		LOGGER.info("먹고 싶은 음식 표식 적재를 시작한다 — 파일={} 수집분={}", file.toAbsolutePath(), this.datasetVersion);

		PlaceFeatureLoader.Saved[] saved = { new PlaceFeatureLoader.Saved(0, 0, 0) };
		int[] matched = { 0 };
		ResearchQueueReader.read(file, CHUNK, rows -> {
			List<PlaceFeatureNdjsonReader.Fact> facts = new ArrayList<>();
			for (SbizRow row : rows) {
				for (String code : DesiredFoodVocabulary.desiredFoodTags(row.name())) {
					facts.add(new PlaceFeatureNdjsonReader.Fact(row.storeId(),
							DesiredFoodVocabulary.FEATURE_TYPE, "true", "SBIZ", code));
				}
			}
			if (!facts.isEmpty()) {
				matched[0] += facts.size();
				saved[0] = saved[0]
						.plus(this.featureLoader.saveChunk(facts, SOURCE_TYPE, this.datasetVersion, collectedAt));
			}
		});

		long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
		LOGGER.info("먹고 싶은 음식 표식 적재를 마쳤다 — 이름 매칭 {}건 · {} · {}ms", matched[0], saved[0], elapsedMs);
	}
}
