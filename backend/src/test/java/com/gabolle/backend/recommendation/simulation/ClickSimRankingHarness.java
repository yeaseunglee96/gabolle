package com.gabolle.backend.recommendation.simulation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


import com.gabolle.backend.place.api.PlaceCandidateResponse;
import com.gabolle.backend.place.api.PlaceFeatureView;
import com.gabolle.backend.place.domain.MatchKind;
import com.gabolle.backend.place.domain.UserPlaceCodeMap;
import com.gabolle.backend.recommendation.adapter.BaselineCandidateScorer;
import com.gabolle.backend.recommendation.adapter.EngineCandidate;
import com.gabolle.backend.recommendation.application.DiversityReranker;
import com.gabolle.backend.recommendation.config.BaselineEngineProperties;
import com.gabolle.backend.recommendation.config.DiversityProperties;
import com.gabolle.backend.recommendation.config.PreferenceAlignmentWeights;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 클릭 시뮬레이터가 읽을 순위표를 실제 배포되는 채점기로 찍는다.
 *
 * <pre>
 *   CLICKSIM_USERS=../eval/click-sim/data/users.json \
 *   CLICKSIM_OUT=../eval/click-sim/data/rankings.json \
 *   ./gradlew clickSim
 * </pre>
 *
 * 채점기를 옮겨 적지 않고 {@link BaselineCandidateScorer} 와 {@link DiversityReranker} 를
 * 그대로 부른다 — 사본을 두면 "배포될 것을 쟀다" 가 거짓이 된다. 둘 다 DB 를 모르므로 스프링
 * 컨텍스트 없이 직접 부를 수 있다.
 *
 * 검사가 아니라 파일을 쓰는 도구라 {@code @Test} 가 아니다. 건너뛰는 검사를 하나라도 두면
 * 건너뛴 테스트가 있으면 빨갛게 만드는 CI 관문이 무의미해진다.
 *
 * 장소는 합성이지만 실제 적재가 만드는 모양을 그대로 흉내 낸다 — {@code CATEGORY_TAG} 는
 * 모든 장소에 {@code FOOD} 가 붙고 카페에만 {@code CAFE_HEALING} 이 더 붙는다. 취향 낱말이
 * 여러 개여도 실제로 가르는 것은 하나뿐인 현실을 펴서 만들면 시뮬레이터만 행복해진다.
 * 모든 산출에 {@code dataset_version = "synthetic-v1"} 이 붙는다.
 */
class ClickSimRankingHarness {

	private static final String DATASET_VERSION = "synthetic-v1";

	/** 이 하네스가 찍었다는 표시. 03단계가 {@code SMOKE} 로 시작하는 값을 거부한다. */
	private static final String SCORER_VERSION = "baseline-real-scorer";

	private static final int PLACE_COUNT = 200;

	private static final int RADIUS_M = 5000;

	/** 실제 적재의 음식 태그 어휘에서 고른 셋 — {@code AppFoodVocabulary} 가 내는 값들이다. */
	private static final List<String> CUISINES = List.of("MILMYEON", "PORK_SOUP", "SEAFOOD");

	private final ObjectMapper mapper = new ObjectMapper();

	private final BaselineCandidateScorer scorer = new BaselineCandidateScorer(new ObjectMapper());

	private final DiversityReranker reranker = new DiversityReranker(new DiversityProperties(null, null, null));

	public static void main(String[] args) throws Exception {
		new ClickSimRankingHarness().writeRankings();
	}

	void writeRankings() throws Exception {
		// 시스템 속성이 아니라 환경변수다. Gradle 은 -D 를 포크한 시험 JVM 에 안 넘기고,
		// 환경변수는 포크된 JVM 이 그대로 물려받는다.
		Path usersPath = Path.of(env("CLICKSIM_USERS", "../eval/click-sim/data/users.json"));
		Path outPath = Path.of(env("CLICKSIM_OUT", "../eval/click-sim/data/rankings.json"));

		JsonNode users = this.mapper.readTree(Files.readString(usersPath));
		String datasetVersion = users.path("datasetVersion").asString();
		if (!DATASET_VERSION.equals(datasetVersion)) {
			// 표시가 어긋나면 멈춘다. 합성이 진짜와 섞이는 사고를 이 한 줄이 막는다.
			throw new IllegalStateException(
					"users.json 의 datasetVersion 이 " + datasetVersion + " 다. " + DATASET_VERSION + " 이어야 한다");
		}

		List<PlaceCandidateResponse.Candidate> places = syntheticPlaces();
		List<UserPlaceCodeMap> preferenceCodeMap = productionPreferenceCodeMap();

		List<Map<String, Object>> rankings = new ArrayList<>();
		for (JsonNode user : users.path("users")) {
			rankings.add(rankFor(user, places, preferenceCodeMap));
		}

		Map<String, Object> out = new LinkedHashMap<>();
		out.put("datasetVersion", DATASET_VERSION);
		out.put("scorerVersion", SCORER_VERSION);
		out.put("weights", weightsAsMap());
		out.put("placeCount", places.size());
		out.put("generatedAt", OffsetDateTime.now().toString());
		// 장소가 합성이라는 사실을 산출물 안에 적는다. 파일만 받아 보는 사람이
		// 배포 장소로 잰 것으로 읽으면 안 된다.
		out.put("placesAreSynthetic", true);
		out.put("placeShapeNote", "CATEGORY_TAG 는 전부 FOOD, 10곳 중 1곳만 CAFE_HEALING — 실제 적재(AppFoodVocabulary)와 같은 모양");
		out.put("rankings", rankings);

		Files.createDirectories(outPath.toAbsolutePath().getParent());
		Files.writeString(outPath, this.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out) + "\n");

		System.out.println("순위표 " + rankings.size() + "명 × " + places.size() + "곳 → " + outPath.toAbsolutePath());
	}

	private static String env(String name, String fallback) {
		String value = System.getenv(name);
		return (value == null || value.isBlank()) ? fallback : value;
	}

	/** 한 사람 몫의 순위 — 진짜 채점기로 점수를 내고 진짜 재정렬기로 순서를 정한다. */
	private Map<String, Object> rankFor(JsonNode user, List<PlaceCandidateResponse.Candidate> places,
			List<UserPlaceCodeMap> preferenceCodeMap) {

		PreferenceSnapshot snapshot = snapshotOf(user);

		List<EngineCandidate> scored = new ArrayList<>(places.size());
		for (PlaceCandidateResponse.Candidate place : places) {
			scored.add(this.scorer.score(place, snapshot, List.of(), RADIUS_M,
					new BaselineEngineProperties.Weights(null, null, null, null, null, null),
					new PreferenceAlignmentWeights(null, null, null, null, null),
					preferenceCodeMap, List.of(),
					// 접힌 벡터는 안 넘긴다. 가상 사용자에게는 접기 배치가 돈 적이 없고,
					// 없는 것을 지어내면 벡터 덧점수까지 같이 재는 셈이 된다.
					List.of(), 0.0));
		}
		scored.sort(Comparator.comparingDouble(
				(EngineCandidate c) -> c.preRankScore() == null ? 0.0 : c.preRankScore()).reversed());

		DiversityReranker.Reranked reranked = this.reranker.rerank(scored);

		Map<UUID, Integer> originalRank = new LinkedHashMap<>();
		for (int i = 0; i < scored.size(); i++) {
			originalRank.put(scored.get(i).placeId(), i + 1);
		}

		Map<UUID, PlaceCandidateResponse.Candidate> byId = new LinkedHashMap<>();
		for (PlaceCandidateResponse.Candidate p : places) {
			byId.put(p.placeId(), p);
		}

		List<Map<String, Object>> candidates = new ArrayList<>();
		List<EngineCandidate> ordered = reranked.ordered();
		for (int i = 0; i < ordered.size(); i++) {
			EngineCandidate c = ordered.get(i);
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("placeId", c.placeId().toString());
			row.put("originalRank", originalRank.get(c.placeId()));
			row.put("finalRank", i + 1);
			row.put("preRankScore", c.preRankScore());
			row.put("featureValues", c.featureValues());
			// 태그 원본을 함께 싣는다. featureValues 에는 겹침 비율만 있어서 어떤 낱말이
			// 겹쳤는지를 알 수 없다.
			row.put("tags", tagsOf(byId.get(c.placeId())));
			candidates.add(row);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("userId", user.path("userId").asString());
		result.put("requestId", UUID.randomUUID().toString());
		result.put("diversityApplied", reranked.applied());
		result.put("candidates", candidates);
		return result;
	}

	/**
	 * 심어 둔 취향을 설문 답 모양으로 옮긴다. 앱이 보내는 모양은 맨 배열
	 * {@code ["MILMYEON"]} 이다.
	 *
	 * 가중치가 양수인 낱말만 고른 것으로 본다 — 온보딩은 좋아하는 것을 고르는 화면이라
	 * 음수 취향은 순위에 들어가지 않는다.
	 */
	private PreferenceSnapshot snapshotOf(JsonNode user) {
		List<PreferenceSnapshot.PreferenceAnswer> answers = new ArrayList<>();
		JsonNode tag = user.path("taste").path("tag");

		answers.add(answer("FOOD_PREFERENCE", selectedCodes(tag.path("CUISINE_TAG"))));
		answers.add(answer("CATEGORY", selectedCodes(tag.path("CATEGORY_TAG"))));

		// 점수형은 앱이 {"score": 0.9} 모양으로 보낸다.
		JsonNode score = user.path("taste").path("score");
		if (score.has("slopePercent")) {
			answers.add(new PreferenceSnapshot.PreferenceAnswer("SLOPE_PREFERENCE",
					"{\"score\": " + score.path("slopePercent").asDouble() + "}",
					PreferenceSnapshot.AnswerStatus.SELECTED));
		}

		return new PreferenceSnapshot(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 1,
				answers, PersonalizationScope.TRIP, List.of(), java.time.Instant.now());
	}

	/**
	 * 고른 것이 없으면 값을 비운다 — {@code "[]"} 가 아니라 {@code null} 이다.
	 * {@code PreferenceAnswer} 는 {@code valueJson} 이 있는 것과 {@code status == SELECTED}
	 * 가 맞아떨어지는지를 생성자에서 검사한다. 가상 사용자의 취향이 전부 음수면 고른 것이
	 * 없는 사람이 되는데, 실제로 있을 수 있는 상태라 그대로 표현한다.
	 */
	private static PreferenceSnapshot.PreferenceAnswer answer(String dimension, List<String> codes) {
		if (codes.isEmpty()) {
			return new PreferenceSnapshot.PreferenceAnswer(dimension, null,
					PreferenceSnapshot.AnswerStatus.SKIPPED);
		}
		StringBuilder json = new StringBuilder("[");
		for (int i = 0; i < codes.size(); i++) {
			if (i > 0) {
				json.append(',');
			}
			json.append('"').append(codes.get(i)).append('"');
		}
		json.append(']');
		return new PreferenceSnapshot.PreferenceAnswer(dimension, json.toString(),
				PreferenceSnapshot.AnswerStatus.SELECTED);
	}

	private static List<String> selectedCodes(JsonNode weights) {
		List<String> codes = new ArrayList<>();
		weights.propertyStream().forEach((entry) -> {
			if (entry.getValue().asDouble() > 0) {
				codes.add(entry.getKey());
			}
		});
		return codes;
	}

	/**
	 * 합성 장소 — 실제 적재가 만드는 모양 그대로. {@code CATEGORY_TAG} 에 {@code FOOD} 를
	 * 전부 붙이는 것이 핵심이다. 실제가 그래서 그 축은 변별력이 없고, 여기서 낱말을 골고루
	 * 뿌리면 시뮬레이터만 잘 도는 가짜 세계가 된다.
	 */
	private List<PlaceCandidateResponse.Candidate> syntheticPlaces() {
		List<PlaceCandidateResponse.Candidate> places = new ArrayList<>(PLACE_COUNT);
		for (int i = 0; i < PLACE_COUNT; i++) {
			List<PlaceFeatureView> features = new ArrayList<>();
			features.add(tagFeature("CATEGORY_TAG", "FOOD"));
			if (i % 10 == 0) {
				features.add(tagFeature("CATEGORY_TAG", "CAFE_HEALING"));
			}
			features.add(tagFeature("CUISINE_TAG", CUISINES.get(i % CUISINES.size())));

			// 경사는 넣는다 — 배포 place_feature 에 실제로 붙어 있다. facets 엔드포인트는
			// feature_key 가 NULL 인 점수형을 못 세서 0 으로 보이지만 행은 있다.
			// 값 모양도 배포와 같게 맞춘다 — {"score": 26.6} 이고 단위는 퍼센트다.
			features.add(scoreFeature("SLOPE_PERCENT", slopePercentFor(i)));

			// 나머지 점수형 넷(로컬성·조용함·그늘·관광객비율)은 안 넣는다. place_feature 에
			// 행이 0 이라, 넣으면 없는 신호를 있는 것처럼 재게 된다.

			long distanceM = 200L + (i * 23L) % (RADIUS_M - 200L);
			places.add(new PlaceCandidateResponse.Candidate(
					UUID.randomUUID(), "합성 장소 " + i, i % 10 == 0 ? "CAFE_HEALING" : "FOOD",
					35.16 + (i % 20) * 0.001, 129.06 + (i % 17) * 0.001, distanceM, features));
		}
		return places;
	}

	private Map<String, List<String>> tagsOf(PlaceCandidateResponse.Candidate place) {
		Map<String, List<String>> tags = new LinkedHashMap<>();
		for (PlaceFeatureView feature : place.features()) {
			if (feature.featureKey() == null) {
				continue;
			}
			tags.computeIfAbsent(feature.featureType(), (k) -> new ArrayList<>()).add(feature.featureKey());
		}
		return tags;
	}

	/** 배포의 경사 분포를 대충 흉내 낸다 — 부산은 평지와 급경사가 같이 있다. */
	private static double slopePercentFor(int i) {
		return Math.round((3.0 + (i * 37) % 40) * 10.0) / 10.0;
	}

	/**
	 * 점수형 피처 — {@code feature_key} 가 없고 값은 JSON 의 {@code score} 에 있다. 배포와
	 * 같은 모양이어야 채점기가 같게 읽는다.
	 */
	private PlaceFeatureView scoreFeature(String featureType, double score) {
		return new PlaceFeatureView(featureType, null, "ESTIMATED",
				this.mapper.readTree("{\"score\": " + score + "}"), null, "CLICK_SIM");
	}

	private PlaceFeatureView tagFeature(String featureType, String featureKey) {
		return new PlaceFeatureView(featureType, featureKey, "VERIFIED", this.mapper.readTree("true"), null,
				"CLICK_SIM");
	}

	/**
	 * 운영 대조표와 같은 짝을 쓴다. {@code CATEGORY} 는 {@code CATEGORY_TAG} 에 붙는다 —
	 * 옛 검사 픽스처에 남아 있는 {@code INTEREST_TAG} 는 예전 이름이라 따라 하면 배포와 다른
	 * 짝을 재게 된다.
	 */
	private List<UserPlaceCodeMap> productionPreferenceCodeMap() {
		return List.of(
				codeMap("CATEGORY", "CATEGORY_TAG", MatchKind.TAG_OVERLAP),
				codeMap("FOOD_PREFERENCE", "CUISINE_TAG", MatchKind.TAG_OVERLAP),
				codeMap("SLOPE_PREFERENCE", "SLOPE_PERCENT", MatchKind.SCORE_COMPARE));
	}

	private static UserPlaceCodeMap codeMap(String userInputCode, String placeFeatureType, MatchKind matchKind) {
		UserPlaceCodeMap row = mock(UserPlaceCodeMap.class);
		when(row.getUserInputCode()).thenReturn(userInputCode);
		when(row.getPlaceFeatureType()).thenReturn(placeFeatureType);
		when(row.getMatchKind()).thenReturn(matchKind);
		return row;
	}

	private static Map<String, Object> weightsAsMap() {
		BaselineEngineProperties.Weights w = new BaselineEngineProperties.Weights(null, null, null, null, null, null);
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("distance", w.distance());
		map.put("interest", w.interest());
		map.put("atmosphere", w.atmosphere());
		map.put("cuisine", w.cuisine());
		map.put("preferenceAlignment", w.preferenceAlignment());
		map.put("popularity", w.popularity());
		return map;
	}
}
