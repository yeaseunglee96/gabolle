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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

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
 * 클릭 시뮬레이터가 읽을 순위표를 <b>실제 배포되는 채점기로</b> 찍는다 — S15P21E201-553.
 *
 * <pre>
 *   CLICKSIM=true \
 *   CLICKSIM_USERS=../eval/click-sim/data/users.json \
 *   CLICKSIM_OUT=../eval/click-sim/data/rankings.json \
 *   ./gradlew test --tests "*ClickSimRankingHarness*"
 * </pre>
 *
 * <h2>🔴 왜 채점기를 옮겨 적지 않고 여기서 부르나</h2>
 *
 * {@code eval/food-ranking/lib/baseline-scorer.mjs} 가 이 채점기를 손으로 옮긴 JS 사본이고,
 * 그 파일 머리말이 위험을 직접 적어 뒀다 — <i>"옮겨 적다가 틀리면 「배포될 것을 쟀다」 는
 * 말 자체가 거짓이 된다."</i> 사본을 하나 더 만드는 대신 <b>진짜를 부른다.</b>
 *
 * <p>부를 수 있는 이유는 {@link BaselineCandidateScorer} 가 그렇게 만들어져 있기 때문이다 —
 * 그 클래스 javadoc 이 <i>"이 클래스는 DB 를 모른다 … 순수 함수에 가깝다"</i> 고 적어 뒀다.
 * 그래서 스프링 컨텍스트도 DB 도 없이 직접 부른다. 다양성 재정렬도 진짜
 * {@link DiversityReranker} 를 쓴다 — {@code finalRank} 는 사용자가 실제로 보는 순서다.
 *
 * <h2>🔴 평소에는 안 돈다</h2>
 *
 * {@code CLICKSIM=true} 가 없으면 건너뛴다. 이것은 검사가 아니라 <b>데이터를 만드는
 * 도구</b>라, CI 에서 돌면 빌드 시간만 먹고 아무것도 안 지킨다. 그리고 파일을 쓰는
 * 부작용이 있는 것을 검사 자리에 두면 언젠가 남의 검사를 망가뜨린다.
 *
 * <h2>🔴 장소는 합성이고, 그 사실을 숨기지 않는다</h2>
 *
 * 배포 DB 를 그대로 쓸 수 없어 장소를 여기서 만든다. 대신 <b>실제 적재가 만드는 모양을
 * 그대로 흉내 낸다</b> — {@code CATEGORY_TAG} 는 모든 장소에 {@code FOOD} 가 붙고 카페에만
 * {@code CAFE_HEALING} 이 더 붙는다({@code AppFoodVocabulary.categoryTags} 그대로).
 * 그래서 「취향 낱말은 여섯인데 실제로 가르는 것은 하나뿐」인 현실이 시뮬레이션 안에도
 * 그대로 재현된다 — S15P21E201-1108. 그걸 펴서 만들면 시뮬레이터만 행복해진다.
 *
 * <p>모든 산출에 {@code dataset_version = "synthetic-v1"} 이 붙는다.
 */
@EnabledIfEnvironmentVariable(named = "CLICKSIM", matches = "true")
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

	@Test
	void writeRankings() throws Exception {
		// 🔴 시스템 속성이 아니라 환경변수다. Gradle 은 -D 를 포크한 시험 JVM 에 안 넘긴다 —
		//    그래서 처음에 이 하네스가 조용히 건너뛰어졌고 빌드는 BUILD SUCCESSFUL 이었다.
		//    build.gradle 에 넘기는 설정을 더할 수도 있지만, 도구 하나 때문에 모두가 쓰는
		//    빌드 파일을 건드리지 않는다. 환경변수는 포크된 JVM 이 그대로 물려받는다.
		Path usersPath = Path.of(env("CLICKSIM_USERS", "../eval/click-sim/data/users.json"));
		Path outPath = Path.of(env("CLICKSIM_OUT", "../eval/click-sim/data/rankings.json"));

		JsonNode users = this.mapper.readTree(Files.readString(usersPath));
		String datasetVersion = users.path("datasetVersion").asString();
		if (!DATASET_VERSION.equals(datasetVersion)) {
			// 🔴 표시가 어긋나면 멈춘다. 합성이 진짜와 섞이는 사고를 이 한 줄이 막는다.
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
		// 🔴 장소가 합성이라는 사실을 산출물 안에 적는다. 파일만 받아 보는 사람이
		//    "배포 장소로 잰 것" 으로 읽으면 안 된다.
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
					// 🔴 접힌 벡터는 안 넘긴다. 가상 사용자에게는 접기 배치가 돈 적이 없고,
					//    없는 것을 지어내면 -943 의 덧점수까지 같이 재는 셈이 된다.
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
			// 🔴 태그 원본을 함께 싣는다. featureValues 에는 겹침 "비율" 만 있어서
			//    어떤 낱말이 겹쳤는지를 알 수 없다 (data/rankings.schema.md).
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
	 * 심어 둔 취향을 <b>설문 답</b> 모양으로 옮긴다.
	 *
	 * <p>🔴 실제 사용자의 답이 지나는 길과 같아야 "배포될 것을 쟀다" 가 참이 된다. 앱이
	 * 보내는 모양은 맨 배열 {@code ["MILMYEON"]} 이다 ({@code PreferenceJson} 참고).
	 *
	 * <p>가중치가 <b>양수인 낱말만</b> 고른 것으로 본다 — 온보딩은 "좋아하는 것을 고르는"
	 * 화면이지 싫어하는 것을 표시하는 화면이 아니다. 음수 취향은 순위에는 안 들어가고
	 * 02단계의 클릭 확률에만 쓰인다. 그 비대칭이 실제와 같다.
	 */
	private PreferenceSnapshot snapshotOf(JsonNode user) {
		List<PreferenceSnapshot.PreferenceAnswer> answers = new ArrayList<>();
		JsonNode tag = user.path("taste").path("tag");

		answers.add(answer("FOOD_PREFERENCE", selectedCodes(tag.path("CUISINE_TAG"))));
		answers.add(answer("CATEGORY", selectedCodes(tag.path("CATEGORY_TAG"))));

		// 🔴 점수형은 앱이 {"score": 0.9} 모양으로 보낸다 (PreferenceJson).
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
	 * 🔴 고른 것이 없으면 값을 비운다 — {@code "[]"} 가 아니라 {@code null} 이다.
	 *
	 * <p>{@code PreferenceAnswer} 가 <b>값의 유무와 상태가 맞는지</b>를 생성자에서 검사한다
	 * ({@code valueJson} 이 있다 ⟺ {@code status == SELECTED}). DB 의
	 * {@code ck_preference_answer_value_matches_status} 를 그대로 옮긴 규칙이다.
	 *
	 * <p>처음에 빈 목록도 {@code "[]"} 로 적었다가 이 검사에 걸렸다. <b>걸린 것이 맞다</b> —
	 * 진짜 도메인을 쓰고 있다는 뜻이고, 가짜로 발라 놓았으면 시뮬레이터만 도는 값이 됐다.
	 * 가상 사용자의 취향이 전부 음수면 고른 것이 없는 사람이 되는데, 그건 실제로 있을 수
	 * 있는 상태라 여기서 막지 않고 그대로 표현한다.
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
	 * 합성 장소 — 실제 적재가 만드는 모양 그대로.
	 *
	 * <p>🔴 {@code CATEGORY_TAG} 에 {@code FOOD} 를 <b>전부</b> 붙이는 것이 핵심이다.
	 * 실제가 그렇고({@code AppFoodVocabulary.categoryTags}), 그래서 그 축은 변별력이 없다.
	 * 여기서 낱말을 골고루 뿌리면 시뮬레이터만 잘 도는 가짜 세계가 된다.
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

			// 🔴 경사는 넣는다 — 배포에 2682곳 붙어 있다(2026-09-17, place_feature 직접 집계).
			//    처음에는 "점수형은 전부 0곳" 이라고 보고 안 넣었는데, 그건 facets 엔드포인트가
			//    점수형을 못 세서 나온 0 이었다. feature_key 가 NULL 이라 featureKey 로 묶는
			//    그 응답에는 안 잡힌다.
			//
			//    값 모양도 배포와 같게 맞춘다 — {"score": 26.6, ...} 이고 단위는 퍼센트다.
			features.add(scoreFeature("SLOPE_PERCENT", slopePercentFor(i)));

			// 🔴 나머지 점수형 넷(로컬성·조용함·그늘·관광객비율)은 여전히 안 넣는다.
			//    place_feature 에 행이 정말로 0 이다. 넣으면 없는 신호를 있는 것처럼 재게 된다.

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
	 * 점수형 피처 — {@code feature_key} 가 <b>없다</b>. 값은 JSON 의 {@code score} 에 있다.
	 *
	 * <p>🔴 이 모양이 facets 엔드포인트가 점수형 축을 0 으로 내는 이유다. 여기서도 같은
	 * 모양으로 만들어야 채점기가 배포에서와 같게 읽는다.
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
	 * 운영 대조표와 <b>같은 짝</b>을 쓴다.
	 *
	 * <p>🔴 {@code CATEGORY → CATEGORY_TAG} 다. 옛 검사 픽스처가 {@code INTEREST_TAG} 로
	 * 적어 둔 곳이 있는데 그건 S15P21E201-904 이전 이름이다 — 여기서 그걸 따라 하면
	 * 배포와 다른 짝을 재게 된다.
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
