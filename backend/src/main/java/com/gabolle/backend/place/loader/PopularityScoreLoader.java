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
 * 목록 근거를 인기도 점수로 넣는다 — S15P21E201-826.
 *
 * <h2>왜 필요한가</h2>
 * 2026-09-10 에 운영에서 추천이 처음 성공했는데, 저장된 점수 내역을 열어 보니 여섯 축 중
 * 거리와 관심사 둘만 계산되고 나머지 넷은 값이 없었다. <b>가중치의 60%가 죽어 있어서</b>
 * 추천이 사실상 "가까운 순" 이었다. 그중 인기도는 저장소에 이미 있는 자료로 채울 수 있다.
 *
 * <h2>점수기가 읽는 모양</h2>
 * {@code BaselineCandidateScorer} 는 {@code feature_type = 'POPULARITY_SCORE'} 인 행을 찾아
 * {@code value} 가 숫자면 그대로, 객체면 {@code value.score} 를 쓴다. 여기서는 <b>객체</b>로
 * 넣는다 — 점수만 남기면 왜 그 점수인지 아무도 되짚을 수 없다.
 *
 * <pre>
 * {"score":0.4,"sources":2,"lists":["블루리본","택슐랭"]}
 * </pre>
 *
 * <h2>이미 있으면 건너뛴다</h2>
 * {@link SbizPlaceLoader#saveChunk} 와 같은 태도다. 두 번 돌려도 행이 안 늘고, 값을 고치지
 * 않는다 — 갱신 규칙은 정해진 적이 없고 여기서 정하면 그것이 곧 계약이 된다.
 *
 * <h2>없는 장소는 건너뛴다</h2>
 * 목록 파일에는 아직 적재 안 된 가게가 섞여 있을 수 있다. 장소 없이 피처만 넣으면 외래키가
 * 거절해서 그 덩어리 전체가 롤백된다 — 한 행 때문에 나머지가 사라진다.
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

	/** @return 실제로 넣은 피처 수 */
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
					// 목록에 올랐다는 사실이지 잰 인기도가 아니다. VERIFIED 로 적으면
					// 나중에 아무도 이 값을 의심하지 않는다.
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
