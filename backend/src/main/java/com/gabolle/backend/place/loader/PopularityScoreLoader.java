package com.gabolle.backend.place.loader;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.domain.PlaceEvidenceStatus;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 목록 근거를 인기도 점수로 넣는다.
 *
 * <p>점수기는 {@code value} 가 숫자면 그대로, 객체면 {@code value.score} 를 쓴다. 여기서는
 * 객체로 넣는다 — 점수만 남기면 왜 그 점수인지 되짚을 수 없다.
 *
 * <pre>
 * {"score":0.4,"sources":2,"lists":["블루리본","택슐랭"]}
 * </pre>
 *
 * <p>이미 있으면 건너뛰고 값을 고치지 않는다. 갱신 규칙은 정해진 적이 없고, 여기서 정하면
 * 그것이 곧 계약이 된다.
 *
 * <p>없는 장소도 건너뛴다. 장소 없이 피처만 넣으면 외래키가 거절해 그 덩어리 전체가 롤백된다 —
 * 한 행 때문에 나머지가 사라진다.
 */
@Component
@Profile({ "db", "dev" })
public class PopularityScoreLoader {

	static final String FEATURE_TYPE = "POPULARITY_SCORE";

	private static final String SOURCE_TYPE = "TRUTH_LIST";

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	private final PlaceRepository placeRepository;

	private final PlaceFeatureRepository placeFeatureRepository;

	public PopularityScoreLoader(PlaceRepository placeRepository, PlaceFeatureRepository placeFeatureRepository) {
		this.placeRepository = placeRepository;
		this.placeFeatureRepository = placeFeatureRepository;
	}

	/** 실제로 넣은 피처 수를 돌려준다. */
	@Transactional
	public int saveChunk(List<TruthSignalRow> rows, ListRarity rarity, String datasetVersion,
			OffsetDateTime collectedAt) {
		Set<UUID> knownPlaces = new HashSet<>();
		this.placeRepository.findAllById(rows.stream().map(row -> SbizPlaceLoader.placeIdOf(row.storeId())).toList())
				.forEach(place -> knownPlaces.add(place.getPlaceId()));

		Set<UUID> existingFeatures = new HashSet<>();
		this.placeFeatureRepository.findAllById(rows.stream().map(row -> featureIdOf(row.storeId())).toList())
				.forEach(feature -> existingFeatures.add(feature.getPlaceFeatureId()));

		List<PlaceFeature> features = new ArrayList<>(rows.size());
		for (TruthSignalRow row : rows) {
			UUID placeId = SbizPlaceLoader.placeIdOf(row.storeId());
			UUID featureId = featureIdOf(row.storeId());
			if (!knownPlaces.contains(placeId) || existingFeatures.contains(featureId)) {
				continue;
			}
			features.add(PlaceFeature.imported(featureId, placeId, FEATURE_TYPE, null, value(row, rarity),
					// 목록에 올랐다는 사실이지 잰 인기도가 아니다.
					PlaceEvidenceStatus.ESTIMATED,
					SOURCE_TYPE, row.storeId(), null, datasetVersion, collectedAt));
		}
		this.placeFeatureRepository.saveAll(features);
		return features.size();
	}

	static String value(TruthSignalRow row, ListRarity rarity) {
		ObjectNode node = MAPPER.createObjectNode();
		node.put("score", rarity.scoreOf(row));
		node.put("lists_count", row.lists().size());
		node.put("mentions", row.mentions());
		ArrayNode lists = node.putArray("lists");
		row.lists().forEach(lists::add);
		return node.toString();
	}

	/**
	 * 상가업소번호에서 언제나 같은 피처 아이디를 만든다. 무작위였다면 같은 파일을 두 번
	 * 돌릴 때 같은 가게에 인기도 행이 둘 생기고, 그러면 점수기가 어느 쪽을 읽을지 모른다.
	 */
	static UUID featureIdOf(String storeId) {
		return UUID.nameUUIDFromBytes(
				("gabolle:place_feature:TRUTH_LIST:" + storeId + ":" + FEATURE_TYPE)
						.getBytes(StandardCharsets.UTF_8));
	}
}
