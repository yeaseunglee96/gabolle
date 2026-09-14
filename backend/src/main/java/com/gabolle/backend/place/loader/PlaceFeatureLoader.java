package com.gabolle.backend.place.loader;

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

/**
 * {@link PlaceFeatureNdjsonReader} 가 읽은 사실을 {@code place_feature} 에 넣는다.
 *
 * <h2>🔴 없는 장소에는 안 넣는다 — 세어서 돌려준다</h2>
 *
 * 두 산출물의 열쇠는 상가업소번호이고, {@code place} 에 그 번호로 만든 행이 <b>있어야만</b> 넣을 수
 * 있다({@code fk_place_feature_place}). 상가정보 전체(53,716곳)를 적재하지 않은 환경에서는 값은
 * 있는데 장소가 없는 번호가 나온다. 그때 예외로 멈추면 나머지가 통째로 안 들어가고, 조용히
 * 넘어가면 <b>"967곳을 넣었다" 고 믿는데 실제로는 300곳만 들어간 상태</b>가 된다.
 * 그래서 <b>건너뛰되 세어서 돌려준다</b> — 넣은 수와 못 넣은 수를 부르는 쪽이 함께 남긴다.
 *
 * <h2>🔴 이미 있는 사실은 건너뛴다 — 고치지 않는다</h2>
 *
 * {@link SbizPlaceLoader#saveChunk} 와 같은 규칙이다. 같은 파일을 두 번 돌려도 행이 두 배가 되지
 * 않는 것이 여기서 지키는 전부고, "새 판으로 갱신한다" 는 별개의 결정이라 여기서 미리 정하지 않는다.
 * 표에 {@code (place_id, feature_type)} 유일 색인이 걸려 있어({@code uq_place_feature_unkeyed})
 * 두 번째 실행은 어차피 DB 가 막지만, 막힌 것을 예외로 받는 것과 미리 세어서 건너뛰는 것은 다르다 —
 * 앞의 것은 덩어리 전체를 되돌린다.
 *
 * <h2>🔴 {@code ESTIMATED} 로 넣는다</h2>
 *
 * 가격대는 <b>조사원이 적은 것이지 가게에 확인한 것이 아니다.</b> {@code VERIFIED} 로 적으면
 * 나중에 아무도 이 값을 의심하지 않는다. {@code PRICE_LEVEL} 은 안전 피처가 아니라
 * {@code ESTIMATED} 가 DB 에서 허용된다
 * ({@code ck_place_feature_safety_never_estimated} 는 알레르기·식단·접근성·계단만 막는다).
 */
@Component
@Profile({ "db", "dev" })
public class PlaceFeatureLoader {

	private final PlaceRepository placeRepository;

	private final PlaceFeatureRepository placeFeatureRepository;

	public PlaceFeatureLoader(PlaceRepository placeRepository, PlaceFeatureRepository placeFeatureRepository) {
		this.placeRepository = placeRepository;
		this.placeFeatureRepository = placeFeatureRepository;
	}

	/**
	 * 넣은 것과 못 넣은 것.
	 *
	 * @param inserted 실제로 들어간 행
	 * @param missingPlace 🔴 그 상가업소번호로 만든 장소가 표에 없어서 못 넣은 것
	 * @param alreadyPresent 같은 장소에 같은 종류가 이미 있어서 건너뛴 것
	 */
	public record Saved(int inserted, int missingPlace, int alreadyPresent) {

		public Saved plus(Saved other) {
			return new Saved(this.inserted + other.inserted, this.missingPlace + other.missingPlace,
					this.alreadyPresent + other.alreadyPresent);
		}

		@Override
		public String toString() {
			return "넣음 " + this.inserted + " · 장소가 없어 못 넣음 " + this.missingPlace + " · 이미 있어 건너뜀 "
					+ this.alreadyPresent;
		}
	}

	/**
	 * 한 덩어리를 넣는다.
	 *
	 * @param sourceType {@code place_feature.source_type}. 어느 산출물에서 온 값인지 되짚는 자리다
	 * @param datasetVersion {@code place_feature.source_version}. 🔴 없으면 이 값으로 만든 추천을
	 *     나중에 되짚을 수 없다 — 부르는 쪽이 검사한다
	 */
	@Transactional
	public Saved saveChunk(List<PlaceFeatureNdjsonReader.Fact> facts, String sourceType, String datasetVersion,
			OffsetDateTime collectedAt) {
		List<UUID> placeIds = facts.stream().map(fact -> SbizPlaceLoader.placeIdOf(fact.storeId())).toList();
		Set<UUID> knownPlaces = new HashSet<>();
		this.placeRepository.findAllById(placeIds).forEach(place -> knownPlaces.add(place.getPlaceId()));

		List<UUID> featureIds = facts.stream()
				.map(fact -> SbizPlaceLoader.featureIdOf(fact.storeId(), fact.featureType(), null))
				.toList();
		Set<UUID> existingFeatures = new HashSet<>();
		this.placeFeatureRepository.findAllById(featureIds)
				.forEach(feature -> existingFeatures.add(feature.getPlaceFeatureId()));

		List<PlaceFeature> rows = new ArrayList<>(facts.size());
		int missingPlace = 0;
		int alreadyPresent = 0;
		for (PlaceFeatureNdjsonReader.Fact fact : facts) {
			UUID placeId = SbizPlaceLoader.placeIdOf(fact.storeId());
			if (!knownPlaces.contains(placeId)) {
				missingPlace++;
				continue;
			}
			UUID featureId = SbizPlaceLoader.featureIdOf(fact.storeId(), fact.featureType(), null);
			// existingFeatures 에 더하는 것이 곧 이 덩어리 안의 중복 검사이기도 하다.
			if (!existingFeatures.add(featureId)) {
				alreadyPresent++;
				continue;
			}
			rows.add(PlaceFeature.imported(featureId, placeId, fact.featureType(), null, fact.value(),
					PlaceEvidenceStatus.ESTIMATED, sourceType, fact.storeId(),
					// 🔴 원천에 "이 사실이 언제 관측됐나" 칸이 없다. 지어내지 않고 비운다 —
					//    어느 산출물인지는 sourceVersion 이 말해 준다.
					null, datasetVersion, collectedAt));
		}
		if (!rows.isEmpty()) {
			this.placeFeatureRepository.saveAll(rows);
		}
		return new Saved(rows.size(), missingPlace, alreadyPresent);
	}

}
