package com.gabolle.backend.recommendation.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;

import com.gabolle.backend.place.repository.UserPlaceCodeMapRepository;
import com.gabolle.backend.place.service.PlaceCandidateQueryService;
import com.gabolle.backend.recommendation.config.BaselineEngineProperties;
import com.gabolle.backend.recommendation.config.PreferenceAlignmentWeights;
import com.gabolle.backend.trip.domain.TripRepository;
import com.gabolle.backend.trip.domain.TripSeedPlaceRepository;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 수집분이 늘어도 일정이 만들어지는가. 이어 붙인 {@code datasetVersion} 이 칸 폭을 넘으면
 * 예전에는 요청이 죽었다. 자료를 더 넣는다고 앱이 멈추지 않으면서도, 조합이 다르면 값은
 * 달라야 한다.
 */
class BaselineRecommendationEngineDatasetVersionTest {

	/** 실제로 운영에서 터진 그 여섯 개다. 로그에서 그대로 가져왔다. */
	private static final List<String> SIX_REAL_DATASETS = List.of(
			"KAKAO_LOCAL_2026-09-15",
			"research-busan-2355-202609",
			"tourapi-busan-20260911",
			"tourapi-curated-20260916",
			"tourapi-curated-city-20260916",
			"tourapi-curated-nature-20260916");

	private final BaselineRecommendationEngine engine = engine();

	@Test
	@DisplayName("🔴 수집분 여섯 개(159자)여도 값이 나온다 — 여기서 앱이 멈췄었다")
	void sixDatasetsNoLongerKillTheRequest() {
		String joined = String.join(",", SIX_REAL_DATASETS);
		assertThat(joined.length()).as("운영에서 터진 그 길이").isEqualTo(159);

		String resolved = this.engine.resolveDatasetVersion(SIX_REAL_DATASETS);

		assertThat(resolved).isNotNull();
		assertThat(resolved).startsWith("sha256:");
		assertThat(resolved.length())
				.as("dataset_version 칸이 VARCHAR(100) 이다")
				.isLessThanOrEqualTo(100);
	}

	@Test
	@DisplayName("짧으면 예전 그대로 사람이 읽는 값이 들어간다 — 이미 쌓인 값의 뜻이 안 바뀐다")
	void shortValuesAreUnchanged() {
		assertThat(this.engine.resolveDatasetVersion(List.of("tourapi-busan-20260911")))
				.isEqualTo("tourapi-busan-20260911");
		// 정렬·중복 제거도 예전 그대로다.
		assertThat(this.engine.resolveDatasetVersion(List.of("b", "a", "b"))).isEqualTo("a,b");
	}

	@Test
	@DisplayName("🔴 순서가 달라도 같은 조합이면 같은 값 — 같은 자료로 만든 일정은 같은 이름을 가져야 한다")
	void orderDoesNotChangeTheDigest() {
		List<String> shuffled = List.of(
				"tourapi-curated-nature-20260916",
				"KAKAO_LOCAL_2026-09-15",
				"tourapi-curated-city-20260916",
				"research-busan-2355-202609",
				"tourapi-curated-20260916",
				"tourapi-busan-20260911");

		assertThat(this.engine.resolveDatasetVersion(shuffled))
				.isEqualTo(this.engine.resolveDatasetVersion(SIX_REAL_DATASETS));
	}

	@Test
	@DisplayName("🔴 조합이 다르면 값도 다르다 — 예전 예외가 막으려던 것이 이것이다")
	void differentCombinationsGetDifferentValues() {
		List<String> withOneMore = new java.util.ArrayList<>(SIX_REAL_DATASETS);
		withOneMore.add("tourapi-curated-sea-20260918");

		assertThat(this.engine.resolveDatasetVersion(withOneMore))
				.isNotEqualTo(this.engine.resolveDatasetVersion(SIX_REAL_DATASETS));
	}

	@Test
	@DisplayName("비어 있으면 null 을 그대로 돌려준다 — \"unknown\" 을 지어내지 않는다")
	void emptyStaysNull() {
		assertThat(this.engine.resolveDatasetVersion(null)).isNull();
		assertThat(this.engine.resolveDatasetVersion(List.of())).isNull();
	}

	/**
	 * 보는 것이 {@code resolveDatasetVersion} 하나라 협력자는 전부 목으로 채운다 — 이
	 * 메서드는 DB 도 채점기도 안 본다.
	 */
	private static BaselineRecommendationEngine engine() {
		BaselineEngineProperties properties = new BaselineEngineProperties(
				"rule-v1", "feature-v1", "ontology-v1", "policy-v1", 3000, 20_000, 10, null, null);
		UserPlaceCodeMapRepository codeMapRepository = mock(UserPlaceCodeMapRepository.class);
		ObjectMapper objectMapper = new ObjectMapper();
		return new BaselineRecommendationEngine(mock(TripRepository.class),
				mock(PlaceCandidateQueryService.class),
				new BaselineCandidateTranslator(properties, codeMapRepository, objectMapper),
				new BaselineCandidateScorer(objectMapper), properties,
				new PreferenceAlignmentWeights(null, null, null, null, null),
				codeMapRepository, mock(TripSeedPlaceRepository.class), Optional.empty(),
				emptyProvider(), emptyProvider());
	}

	private static <T> ObjectProvider<T> emptyProvider() {
		@SuppressWarnings("unchecked")
		ObjectProvider<T> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(null);
		return provider;
	}
}
