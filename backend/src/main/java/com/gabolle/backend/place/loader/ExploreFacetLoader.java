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
 * 로컬 탐색 여덟 갈래의 표식을 이미 적재된 장소에 붙인다.
 *
 * <p>{@link TourApiPlaceLoader} 는 이미 있는 장소를 건너뛰므로, 운영에 장소가 들어간 뒤에는
 * 표식을 더할 길이 없다. 그래서 표식만 다루는 적재를 따로 둔다.
 *
 * <p>같은 {@code INTEREST_TAG} 자리에 추천이 쓰는 어휘가 이미 들어 있다. 표식 id 를 갈래
 * 코드까지 넣어 계산하므로 두 어휘가 같은 장소에 나란히 남는다 — 서로 다른 질문에 답한다.
 *
 * <p>증거 등급은 {@code ESTIMATED} 다. 원천의 분류 칸을 옮긴 것이지 장소에 직접 확인한 것이
 * 아니다.
 */
@Component
@Profile({ "db", "dev" })
public class ExploreFacetLoader {

	/** 여덟 갈래가 사는 자리. {@code InterestTagCode.FEATURE_TYPE} 과 같은 값이다. */
	public static final String FEATURE_TYPE = "INTEREST_TAG";

	private final PlaceRepository placeRepository;

	private final PlaceFeatureRepository placeFeatureRepository;

	public ExploreFacetLoader(PlaceRepository placeRepository, PlaceFeatureRepository placeFeatureRepository) {
		this.placeRepository = placeRepository;
		this.placeFeatureRepository = placeFeatureRepository;
	}

	@Transactional
	public Result saveChunk(List<TourApiPlaceRow> rows, String datasetVersion, OffsetDateTime collectedAt) {
		Set<UUID> knownPlaces = new HashSet<>();
		this.placeRepository
				.findAllById(rows.stream().map((row) -> TourApiPlaceLoader.placeIdOf(row.contentId())).toList())
				.forEach((place) -> knownPlaces.add(place.getPlaceId()));

		List<UUID> featureIds = new ArrayList<>();
		for (TourApiPlaceRow row : rows) {
			for (String facet : facetsOf(row)) {
				featureIds.add(TourApiPlaceLoader.featureIdOf(row.contentId(), FEATURE_TYPE, facet));
			}
		}
		Set<UUID> existing = new HashSet<>();
		this.placeFeatureRepository.findAllById(featureIds)
				.forEach((feature) -> existing.add(feature.getPlaceFeatureId()));

		List<PlaceFeature> features = new ArrayList<>();
		int noPlace = 0;
		int alreadyThere = 0;
		int noFacet = 0;
		for (TourApiPlaceRow row : rows) {
			List<String> facets = facetsOf(row);
			if (facets.isEmpty()) {
				noFacet++;
				continue;
			}
			UUID placeId = TourApiPlaceLoader.placeIdOf(row.contentId());
			if (!knownPlaces.contains(placeId)) {
				noPlace++;
				continue;
			}
			for (String facet : facets) {
				UUID featureId = TourApiPlaceLoader.featureIdOf(row.contentId(), FEATURE_TYPE, facet);
				if (!existing.add(featureId)) {
					alreadyThere++;
					continue;
				}
				features.add(PlaceFeature.imported(featureId, placeId, FEATURE_TYPE, facet,
						// 태그형의 값은 "이 표식이 있다" 하나뿐이다.
						"true", PlaceEvidenceStatus.ESTIMATED,
						TourApiPlaceLoader.SOURCE_TYPE, row.contentId(),
						null, datasetVersion, collectedAt));
			}
		}
		if (!features.isEmpty()) {
			this.placeFeatureRepository.saveAll(features);
		}
		return new Result(features.size(), noPlace, alreadyThere, noFacet);
	}

	private static List<String> facetsOf(TourApiPlaceRow row) {
		return TourApiExploreFacet.of(row.contentTypeId(), row.cat1(), row.cat3());
	}

	/** {@code noFacet} 은 여덟 갈래 중 어디에도 안 드는 줄이다 — 대부분이 여기로 간다. */
	public record Result(int inserted, int noPlace, int alreadyThere, int noFacet) {

		public Result plus(Result other) {
			return new Result(this.inserted + other.inserted, this.noPlace + other.noPlace,
					this.alreadyThere + other.alreadyThere, this.noFacet + other.noFacet);
		}

		@Override
		public String toString() {
			return "새로 붙인 %d · 붙일 장소가 없어 넘긴 %d · 이미 있어 넘긴 %d · 갈래가 없어 넘긴 %d"
					.formatted(this.inserted, this.noPlace, this.alreadyThere, this.noFacet);
		}
	}
}
