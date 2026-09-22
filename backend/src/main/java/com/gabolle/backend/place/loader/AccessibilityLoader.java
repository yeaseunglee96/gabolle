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
 * 접근성 표식을 이미 적재된 장소에 붙인다.
 *
 * <p>증거 등급이 {@code VERIFIED} 다 — 다른 적재는 전부 {@code ESTIMATED} 를 쓴다. DB 가 이
 * 갈래에만 {@code ESTIMATED} 를 거부하고({@code ck_place_feature_safety_never_estimated}),
 * 값도 분류 코드를 옮긴 것이 아니라 원천이 "휠체어 접근 가능" 이라고 쓴 문장을 읽은 것이다.
 * 그래서 {@link BarrierFreeAccessibility} 의 판정을 좁게 잡았다.
 *
 * <p>접근성을 말하지 않은 장소에는 행을 만들지 않는다. 행이 없는 것은 "접근 불가" 가 아니라
 * "모른다" 다.
 */
@Component
@Profile({ "db", "dev" })
public class AccessibilityLoader {

	/** 접근성 표식이 사는 자리. 태그형이라 {@code feature_key} 에 코드가 온다. */
	public static final String FEATURE_TYPE = "ACCESSIBILITY_TAG";

	private final PlaceRepository placeRepository;

	private final PlaceFeatureRepository placeFeatureRepository;

	public AccessibilityLoader(PlaceRepository placeRepository, PlaceFeatureRepository placeFeatureRepository) {
		this.placeRepository = placeRepository;
		this.placeFeatureRepository = placeFeatureRepository;
	}

	@Transactional
	public Result saveChunk(List<BarrierFreeRow> rows, String datasetVersion, OffsetDateTime collectedAt) {
		Set<UUID> knownPlaces = new HashSet<>();
		this.placeRepository
				.findAllById(rows.stream().map((row) -> TourApiPlaceLoader.placeIdOf(row.contentId())).toList())
				.forEach((place) -> knownPlaces.add(place.getPlaceId()));

		List<UUID> featureIds = new ArrayList<>();
		for (BarrierFreeRow row : rows) {
			for (String code : row.codes()) {
				featureIds.add(TourApiPlaceLoader.featureIdOf(row.contentId(), FEATURE_TYPE, code));
			}
		}
		Set<UUID> existing = new HashSet<>();
		this.placeFeatureRepository.findAllById(featureIds)
				.forEach((feature) -> existing.add(feature.getPlaceFeatureId()));

		List<PlaceFeature> features = new ArrayList<>();
		int noPlace = 0;
		int alreadyThere = 0;
		for (BarrierFreeRow row : rows) {
			UUID placeId = TourApiPlaceLoader.placeIdOf(row.contentId());
			if (!knownPlaces.contains(placeId)) {
				noPlace++;
				continue;
			}
			for (String code : row.codes()) {
				UUID featureId = TourApiPlaceLoader.featureIdOf(row.contentId(), FEATURE_TYPE, code);
				if (!existing.add(featureId)) {
					alreadyThere++;
					continue;
				}
				features.add(PlaceFeature.imported(featureId, placeId, FEATURE_TYPE, code,
						"true", PlaceEvidenceStatus.VERIFIED,
						TourApiPlaceLoader.SOURCE_TYPE, row.contentId(),
						null, datasetVersion, collectedAt));
			}
		}
		if (!features.isEmpty()) {
			this.placeFeatureRepository.saveAll(features);
		}
		return new Result(features.size(), noPlace, alreadyThere);
	}

	/** {@code noPlace} 는 붙일 장소가 없어 넘긴 줄이다 — 장소 적재를 먼저 안 돌리면 여기가 쌓인다. */
	public record Result(int inserted, int noPlace, int alreadyThere) {

		public Result plus(Result other) {
			return new Result(this.inserted + other.inserted, this.noPlace + other.noPlace,
					this.alreadyThere + other.alreadyThere);
		}

		@Override
		public String toString() {
			return "새로 붙인 %d · 붙일 장소가 없어 넘긴 %d · 이미 있어 넘긴 %d"
					.formatted(this.inserted, this.noPlace, this.alreadyThere);
		}
	}
}
