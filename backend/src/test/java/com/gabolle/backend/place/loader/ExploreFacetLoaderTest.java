package com.gabolle.backend.place.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlaceEvidenceStatus;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;

/** DB 를 쓰지 않는다. 저장소를 흉내 내고 어떤 행을 저장하려 했는지만 본다. */
class ExploreFacetLoaderTest {

	private static final String CONTENT_ID = "129156";

	private static final String DATASET = "tourapi-busan-20260911";

	private PlaceRepository placeRepository;

	private PlaceFeatureRepository placeFeatureRepository;

	private ExploreFacetLoader loader;

	@BeforeEach
	void setUp() {
		this.placeRepository = mock(PlaceRepository.class);
		this.placeFeatureRepository = mock(PlaceFeatureRepository.class);
		this.loader = new ExploreFacetLoader(this.placeRepository, this.placeFeatureRepository);
		given(this.placeFeatureRepository.findAllById(anyIterable())).willReturn(List.of());
	}

	@Test
	@DisplayName("적재된 장소에 갈래 표식을 붙인다")
	void attachesFacetToKnownPlace() {
		UUID placeId = TourApiPlaceLoader.placeIdOf(CONTENT_ID);
		givenPlaceExists(placeId);

		ExploreFacetLoader.Result result = this.loader.saveChunk(
				List.of(row(CONTENT_ID, "12", "A01", "A01010400")), DATASET, OffsetDateTime.now());

		assertThat(result.inserted()).isEqualTo(1);
		assertThat(saved()).singleElement().satisfies((feature) -> {
			assertThat(feature.getPlaceId()).isEqualTo(placeId);
			assertThat(feature.getFeatureType()).isEqualTo("INTEREST_TAG");
			assertThat(feature.getFeatureKey()).isEqualTo("NATURE");
			assertThat(feature.getEvidenceStatus()).isEqualTo(PlaceEvidenceStatus.ESTIMATED);
			assertThat(feature.getSourceId()).isEqualTo(CONTENT_ID);
		});
	}

	@Test
	@DisplayName("장소가 없으면 붙이지 않고 세어 올린다 — 장소 적재를 먼저 안 돌린 경우다")
	void rowWithoutPlaceIsCounted() {
		given(this.placeRepository.findAllById(anyIterable())).willReturn(List.of());

		ExploreFacetLoader.Result result = this.loader.saveChunk(
				List.of(row(CONTENT_ID, "12", "A01", "A01010400")), DATASET, OffsetDateTime.now());

		assertThat(result.inserted()).isZero();
		assertThat(result.noPlace()).isEqualTo(1);
		verify(this.placeFeatureRepository, never()).saveAll(anyIterable());
	}

	@Test
	@DisplayName("여덟 갈래에 안 드는 줄은 장소를 찾아보지도 않고 넘긴다")
	void rowWithoutFacetIsCounted() {
		givenPlaceExists(TourApiPlaceLoader.placeIdOf(CONTENT_ID));

		ExploreFacetLoader.Result result = this.loader.saveChunk(
				List.of(row(CONTENT_ID, "38", "A04", "A04010300")), DATASET, OffsetDateTime.now());

		assertThat(result.inserted()).isZero();
		assertThat(result.noFacet()).isEqualTo(1);
	}

	@Test
	@DisplayName("두 번 돌려도 표식이 안 는다")
	void sameFacetTwiceIsInsertedOnce() {
		givenPlaceExists(TourApiPlaceLoader.placeIdOf(CONTENT_ID));

		ExploreFacetLoader.Result result = this.loader.saveChunk(
				List.of(row(CONTENT_ID, "12", "A01", "A01010400"), row(CONTENT_ID, "12", "A01", "A01010400")),
				DATASET, OffsetDateTime.now());

		assertThat(result.inserted()).isEqualTo(1);
		assertThat(result.alreadyThere()).isEqualTo(1);
	}

	@Test
	@DisplayName("추천이 쓰는 갈래 표식과 겹치지 않는다 — 한 장소가 두 어휘를 나란히 갖는다")
	void doesNotCollideWithRecommendationTag() {
		assertThat(TourApiPlaceLoader.featureIdOf(CONTENT_ID, "INTEREST_TAG", "NATURE"))
				.isNotEqualTo(TourApiPlaceLoader.featureIdOf(CONTENT_ID, "INTEREST_TAG", "NATURE_WALK"));
	}

	private void givenPlaceExists(UUID placeId) {
		Place place = Place.imported(placeId, "금정산", "NATURE_WALK", "부산광역시 금정구",
				35.2, 129.0, TourApiPlaceLoader.SOURCE_TYPE, CONTENT_ID, OffsetDateTime.now(), null, DATASET);
		given(this.placeRepository.findAllById(anyIterable())).willReturn(List.of(place));
	}

	@SuppressWarnings("unchecked")
	private List<PlaceFeature> saved() {
		ArgumentCaptor<Iterable<PlaceFeature>> captor = ArgumentCaptor.forClass(Iterable.class);
		verify(this.placeFeatureRepository).saveAll(captor.capture());
		return List.copyOf((List<PlaceFeature>) captor.getValue());
	}

	private static TourApiPlaceRow row(String contentId, String contentTypeId, String cat1, String cat3) {
		return new TourApiPlaceRow(contentId, contentTypeId, cat1, cat3, "금정산", "부산 금정구", 35.2, 129.0,
				null, null);
	}
}
