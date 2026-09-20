package com.gabolle.backend.place.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

/**
 * 영업시간 출력의 열쇠({@code contentid})와 장소 id 의 재료가 같은 값이라는 것을 못 박는다.
 * 그것이 틀리면 적재는 성공하고 로그도 초록인데 붙은 행이 0개가 된다.
 *
 * <p>DB 는 쓰지 않는다. 저장소를 흉내 내고 어떤 id 로 찾았는지와 무엇을 저장하려 했는지만 본다.
 */
class OpeningHoursLoaderTest {

	private static final String CONTENT_ID = "129156";

	private static final String DATASET = "tourapi-busan-20260911";

	private PlaceRepository placeRepository;

	private PlaceFeatureRepository placeFeatureRepository;

	private OpeningHoursLoader loader;

	@BeforeEach
	void setUp() {
		this.placeRepository = mock(PlaceRepository.class);
		this.placeFeatureRepository = mock(PlaceFeatureRepository.class);
		this.loader = new OpeningHoursLoader(this.placeRepository, this.placeFeatureRepository);
		given(this.placeFeatureRepository.findAllById(anyIterable())).willReturn(List.of());
	}

	@Test
	@DisplayName("🔴 관광공사 식별자로 계산한 장소에 붙는다 — 이 계산이 이 티켓의 전부다")
	void featureIsAttachedToThePlaceComputedFromContentId() {
		UUID expected = TourApiPlaceLoader.placeIdOf(CONTENT_ID);
		givenPlaceExists(expected);

		OpeningHoursLoader.Result result = this.loader.saveChunk(
				List.of(row(CONTENT_ID, "OPENING_HOURS")), DATASET, OffsetDateTime.now());

		assertThat(result.inserted()).isEqualTo(1);
		assertThat(saved()).singleElement()
				.satisfies((feature) -> {
					assertThat(feature.getPlaceId()).isEqualTo(expected);
					assertThat(feature.getSourceId())
							.as("어느 관광공사 레코드에서 왔는지 행에 남아야 되짚을 수 있다")
							.isEqualTo(CONTENT_ID);
					assertThat(feature.getSourceVersion()).isEqualTo(DATASET);
					// 업소의 자기 보고를 모은 값이고 언제 확인됐는지 원천이 말해 주지 않는다.
					assertThat(feature.getEvidenceStatus()).isEqualTo(PlaceEvidenceStatus.ESTIMATED);
					// 태그형이 아니라 키가 없어야 한다 — ck_place_feature_key_shape 가 DB 에서 막는다.
					assertThat(feature.getFeatureKey()).isNull();
				});
	}

	@Test
	@DisplayName("🔴 장소가 없으면 넣지 않고 세어 올린다 — 음식 328곳이 여기로 온다")
	void rowWithoutAPlaceIsCountedNotInserted() {
		given(this.placeRepository.findAllById(anyIterable())).willReturn(List.of());

		OpeningHoursLoader.Result result = this.loader.saveChunk(
				List.of(row("200001", "OPENING_HOURS")), DATASET, OffsetDateTime.now());

		assertThat(result.inserted()).isZero();
		assertThat(result.noPlace()).isEqualTo(1);
		verify(this.placeFeatureRepository, never()).saveAll(anyIterable());
	}

	@Test
	@DisplayName("같은 덩어리에 같은 장소·같은 갈래가 두 번 오면 한 번만 넣는다")
	void sameFeatureTwiceInOneChunkIsInsertedOnce() {
		givenPlaceExists(TourApiPlaceLoader.placeIdOf(CONTENT_ID));

		OpeningHoursLoader.Result result = this.loader.saveChunk(
				List.of(row(CONTENT_ID, "OPENING_HOURS"), row(CONTENT_ID, "OPENING_HOURS")),
				DATASET, OffsetDateTime.now());

		assertThat(result.inserted()).isEqualTo(1);
		assertThat(result.alreadyThere()).isEqualTo(1);
	}

	@Test
	@DisplayName("영업시간과 체크인·체크아웃은 서로 다른 행이다 — 한 장소에 둘이 붙어도 안 덮는다")
	void openingHoursAndCheckInOutDoNotOverwriteEachOther() {
		assertThat(OpeningHoursLoader.featureIdOf(CONTENT_ID, "OPENING_HOURS"))
				.isNotEqualTo(OpeningHoursLoader.featureIdOf(CONTENT_ID, "CHECK_IN_OUT"));
	}

	@Test
	@DisplayName("🔴 갈래 표식 행과도 겹치지 않는다 — 겹치면 바다·문화 표식이 영업시간으로 덮인다")
	void openingHoursIdNeverCollidesWithInterestTagId() {
		Set<UUID> ids = new HashSet<>();
		int count = 0;
		for (int i = 100000; i < 104000; i++) {
			String contentId = String.valueOf(i);
			assertThat(ids.add(OpeningHoursLoader.featureIdOf(contentId, "OPENING_HOURS"))).isTrue();
			assertThat(ids.add(OpeningHoursLoader.featureIdOf(contentId, "CHECK_IN_OUT"))).isTrue();
			assertThat(ids.add(TourApiPlaceLoader.featureIdOf(contentId, "INTEREST_TAG", "SEA_BEACH")))
					.as("갈래 표식과 겹쳤다: %s", contentId)
					.isTrue();
			count += 3;
		}
		assertThat(ids).hasSize(count);
	}

	@Test
	@DisplayName("같은 식별자로 두 번 계산하면 같은 값이다 — 두 번 돌려도 행이 안 느는 근거다")
	void featureIdIsStable() {
		assertThat(OpeningHoursLoader.featureIdOf(CONTENT_ID, "OPENING_HOURS"))
				.isEqualTo(OpeningHoursLoader.featureIdOf(CONTENT_ID, "OPENING_HOURS"));
	}

	private void givenPlaceExists(UUID placeId) {
		Place place = Place.imported(placeId, "금정산", "NATURE_WALK", "부산광역시 금정구",
				35.2, 129.0, TourApiPlaceLoader.SOURCE_TYPE, CONTENT_ID, OffsetDateTime.now(),
				null, DATASET);
		given(this.placeRepository.findAllById(anyIterable())).willReturn(List.of(place));
	}

	@SuppressWarnings("unchecked")
	private List<PlaceFeature> saved() {
		ArgumentCaptor<Iterable<PlaceFeature>> captor = ArgumentCaptor.forClass(Iterable.class);
		verify(this.placeFeatureRepository).saveAll(captor.capture());
		return List.copyOf((List<PlaceFeature>) captor.getValue());
	}

	private static OpeningHoursRow row(String contentId, String featureType) {
		return new OpeningHoursRow(contentId, featureType,
				"{\"status\":\"PARSED\",\"byDay\":{\"mon\":[[\"10:00\",\"18:00\"]]}}");
	}
}
