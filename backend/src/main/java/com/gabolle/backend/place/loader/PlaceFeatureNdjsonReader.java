package com.gabolle.backend.place.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * bigData 가 이미 만들어 둔 가격대 산출물을 {@code place_feature} 행으로 옮긴다.
 *
 * <h2>🔴 값은 이미 있었고, 잇는 코드만 없었다</h2>
 *
 * {@code place_feature} 는 19종을 담을 수 있는데 채워지는 것이 둘뿐이었다(관심·음식,
 * {@link SbizPlaceLoader}). 나머지 17종이 0행이라 채점기가 어떤 장소든 최대 0.30점밖에 못 냈다.
 * 그런데 <b>조사된 가격대 967곳은 이미 파일로 만들어져 저장소에 커밋돼 있다</b>
 * ({@code bigData/dev} 의 {@code data/staged/place-priceband.ndjson}).
 * 없던 것은 값이 아니라 옮기는 코드다.
 *
 * <h2>🔴 유명세는 여기서 다루지 않는다 (S15P21E201-861)</h2>
 *
 * 이 파일의 앞선 판은 가격대와 유명세를 함께 다뤘다. 그 사이 <b>유명세는
 * S15P21E201-826 이 다른 방식으로 먼저 넣었다</b>({@code PopularityScoreLoader} ·
 * {@code ListRarity} · {@code TruthSignalReader}). 같은 값을 넣는 경로가 둘이 되면
 * 나중에 어느 쪽이 진짜인지 아무도 모르므로, <b>여기서는 뺐다.</b>
 *
 * <h2>🟢 이름으로 맞추지 않는다 — 산출물이 상가업소번호를 들고 있다</h2>
 *
 * 이 작업에서 가장 위험한 것은 이름 매칭이다("명륜진사갈비" 가 부산에 몇 곳인지 생각해 보라).
 * 그 위험이 여기엔 <b>없다.</b> 산출물의 열쇠가 <b>상가업소번호</b>(상가정보의 가게 고유 번호,
 * {@code MA0101…})이고, 장소 아이디는 그 번호에서 계산된다
 * ({@link SbizPlaceLoader#placeIdOf}). 짝은 정확히 하나이거나 아예 없다.
 *
 * <h2>🔴 읽는 쪽이 값의 모양을 정하는 자리다</h2>
 *
 * {@code value} 는 JSONB 라 아무 모양이나 들어간다. 그래서 <b>모르는 낱말이 오면 멈춘다.</b>
 * 조용히 버리면 개수만 줄고 아무도 못 알아챈다 — {@code priceband-normalize.mjs} 가 같은 이유로
 * 같은 선택을 했다.
 *
 * <h2>🔴 SBIZ 전용이 아니다 (S15P21E201-453·1047)</h2>
 *
 * {@link #readPriceBands} 는 상가업소번호(SBIZ)만 다루던 시절 이름 그대로 남아 있지만,
 * {@link Fact} 에 {@code keySource} 가 생기면서 이 읽개가 내는 사실은 어느 출처의 장소에도 붙을 수
 * 있다. {@link #readVisitorFacts} 가 TourAPI 출처 장소(관광공사 contentid)에 혼밥 안심·
 * 브레이크타임·라스트오더를 붙이는 것, {@link #readPlaceSlopes} 가 같은 장소에 경사를 붙이는 것이
 * 그 사례다.
 */
public final class PlaceFeatureNdjsonReader {

	/** 가격대 등급 — {@code priceband-normalize.mjs} 가 낱말 열하나를 접어 만든 넷. */
	static final Set<String> PRICE_BANDS = Set.of("LOW", "MID", "MID_HIGH", "HIGH");

	/**
	 * {@link #readVisitorFacts} 가 받는 종류 — S15P21E201-453·479. 셋 다 참거짓형·값형이라
	 * {@code featureKey} 가 없다({@code V20260909020000__place_feature_solo_friendly_and_time_facts.sql}).
	 */
	static final Set<String> VISITOR_FEATURE_TYPES = Set.of("SOLO_FRIENDLY", "BREAK_TIME", "LAST_ORDER_TIME");

	/** 지금 이 적재기가 장소를 찾을 수 있는 출처. 새 출처가 생기면 여기부터 늘린다. */
	static final Set<String> NAMESPACES = Set.of("SBIZ", "TOURAPI");

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private PlaceFeatureNdjsonReader() {
	}

	/**
	 * 장소 하나에 붙일 사실 하나.
	 *
	 * @param storeId 장소를 찾는 열쇠. 무엇으로 읽어야 하는지는 {@code keySource} 가 말한다
	 * @param featureType {@code PRICE_LEVEL} · {@code SLOPE_PERCENT} · {@code SOLO_FRIENDLY} ·
	 *     {@code DESIRED_FOOD_TAG} 처럼 무엇에 대한 사실인가
	 * @param value {@code place_feature.value} 에 그대로 들어갈 JSON 문자열
	 * @param keySource 🔴 <b>열쇠가 어느 체계인가</b> — {@link SbizPlaceLoader#SOURCE_TYPE}(상가업소번호)
	 *     이거나 {@link TourApiPlaceLoader#SOURCE_TYPE}({@code contentid})다 (S15P21E201-1047)
	 * @param featureKey 태그형만 값이 있다 — {@code DESIRED_FOOD_TAG}·{@code SOUVENIR_ITEM_TAG} 등
	 *     (S15P21E201-453·448). 참거짓형·값형은 {@code null}
	 */
	public record Fact(String storeId, String featureType, String value, String keySource, String featureKey) {

		/**
		 * 🔴 열쇠 체계는 {@code place_feature.source_type} 과 <b>다른 것이다.</b>(S15P21E201-1047)
		 *
		 * <p>처음에 이 둘을 같은 것으로 보고 {@code source_type} 으로 장소 아이디를 만들려다
		 * DB 통합 시험에 걸렸다. 가격대는 {@code source_type} 이 {@code RESEARCH_PRICEBAND}
		 * (조사에서 왔다)인데 <b>열쇠는 상가업소번호</b>다. 둘은 서로 독립이다 —
		 * 하나는 "값이 어디서 왔나", 하나는 "이 문자열을 무엇으로 읽나" 다.
		 *
		 * <p>그래서 읽는 쪽이 정한다. 파일을 파싱한 쪽이 그 열쇠가 무엇인지 안다.
		 *
		 * <p>이 생성자는 열쇠·태그키를 안 적은 기존 호출자를 위한 것이다 — 상가업소번호로 본다.
		 */
		public Fact(String storeId, String featureType, String value) {
			this(storeId, featureType, value, SbizPlaceLoader.SOURCE_TYPE, null);
		}

		/** 🔴 태그키를 안 적은 호출자를 위한 것이다(S15P21E201-1047 이 만든 4-인자 자리). */
		public Fact(String storeId, String featureType, String value, String keySource) {
			this(storeId, featureType, value, keySource, null);
		}
	}

	/**
	 * 읽은 줄 수와 버린 줄 수.
	 *
	 * <p>🔴 버린 줄을 세어서 돌려주는 이유는 {@link SbizCsvReader} 와 같다 — 몇이 빠졌는지 모르면
	 * 나중에 "왜 그 가게에 값이 없나" 에 답할 수 없다.
	 *
	 * @param total 빈 줄을 뺀 전체 줄 수
	 * @param skippedNoValue 열쇠나 값이 없어서 뺀 줄
	 * @param usable 실제로 사실이 된 줄
	 */
	public record Counts(int total, int skippedNoValue, int usable) {

		@Override
		public String toString() {
			return "줄 " + this.total + " · 값이 없어 뺌 " + this.skippedNoValue + " · 쓸 수 있는 것 " + this.usable;
		}
	}

	/**
	 * 가격대 산출물({@code data/staged/place-priceband.ndjson})을 읽는다.
	 *
	 * <p>한 줄은 {@code {"placeId":"MA0101…","raw":"mid","band":"MID"}} 다.
	 *
	 * <h2>🔴 등급을 숫자로 바꾸지 않는다</h2>
	 *
	 * {@code MID_HIGH} 를 1~4 중 어디에 둘지는 아무도 안 정했고, 정리 프로그램이 <b>일부러 안 정하고
	 * 남겨 두었다</b>. 여기서 숫자를 붙이면 그 결정을 대신 내리는 셈이고, 한 번 들어간 숫자는
	 * 계약처럼 굳는다. 그래서 등급 낱말과 원문 낱말을 그대로 싣는다 —
	 * {@code {"band":"MID","raw":"mid"}}. 접는 것은 쓰는 쪽에서 나중에도 할 수 있지만 펴는 것은 못 한다.
	 */
	public static Counts readPriceBands(Path file, int chunkSize, Consumer<List<Fact>> chunkConsumer) {
		return read(file, chunkSize, chunkConsumer, (node, out) -> {
			String storeId = text(node, "placeId");
			String band = text(node, "band");
			if (storeId == null || band == null) {
				return false;
			}
			if (!PRICE_BANDS.contains(band)) {
				// 🔴 모르는 등급을 조용히 버리면 개수만 줄고 아무도 못 알아챈다.
				throw new IllegalArgumentException("가격대 산출물에 모르는 등급이 있다: " + band
						+ " (아는 것: " + PRICE_BANDS + ")");
			}
			ObjectNode value = MAPPER.createObjectNode();
			value.put("band", band);
			String raw = text(node, "raw");
			if (raw != null) {
				// 조사원이 실제로 쓴 낱말. 접은 것이 틀렸다는 것을 나중에 알아도 되돌릴 수 있게 남긴다.
				value.put("raw", raw);
			}
			out.add(new Fact(storeId, "PRICE_LEVEL", write(value)));
			return true;
		});
	}

	/**
	 * 혼밥 안심·브레이크타임·라스트오더 산출물을 읽는다 — S15P21E201-453·479.
	 *
	 * <p>한 줄은 {@code {"namespace":"TOURAPI","storeId":"129156","featureType":"SOLO_FRIENDLY","value":true}}
	 * 다. {@code value} 는 참거짓형이면 JSON 리터럴 {@code true}/{@code false}, 시각형이면
	 * {@code {"start":"15:00","end":"17:00"}}(BREAK_TIME) · {@code {"time":"21:30"}}(LAST_ORDER_TIME)
	 * 같은 객체다 — 어느 모양이든 그대로 옮긴다({@code place_feature.value} 가 JSONB 라 모양을
	 * 여기서 정하지 않는다, 클래스 문서 "읽는 쪽이 값의 모양을 정하는 자리" 참고).
	 *
	 * <p>🔴 {@code namespace}·{@code featureType} 이 모르는 값이면 멈춘다 — {@link #readPriceBands} 와
	 * 같은 이유다. 특히 {@code namespace} 오타는 조용히 두면 엉뚱한 장소 아이디를 계산해 "장소가
	 * 없어 못 넣음" 으로 세어지고, 그 오타를 알아챌 방법이 없다.
	 */
	public static Counts readVisitorFacts(Path file, int chunkSize, Consumer<List<Fact>> chunkConsumer) {
		return read(file, chunkSize, chunkConsumer, (node, out) -> {
			String namespace = text(node, "namespace");
			String storeId = text(node, "storeId");
			String featureType = text(node, "featureType");
			JsonNode valueNode = node.path("value");
			if (namespace == null || storeId == null || featureType == null || valueNode.isMissingNode()) {
				return false;
			}
			if (!NAMESPACES.contains(namespace)) {
				throw new IllegalArgumentException(
						"모르는 namespace 다: " + namespace + " (아는 것: " + NAMESPACES + ")");
			}
			if (!VISITOR_FEATURE_TYPES.contains(featureType)) {
				throw new IllegalArgumentException(
						"모르는 featureType 이다: " + featureType + " (아는 것: " + VISITOR_FEATURE_TYPES + ")");
			}
			out.add(new Fact(storeId, featureType, valueNode.toString(), namespace));
			return true;
		});
	}

	/**
	 * 장소 경사 산출물({@code data/staged/place-slope.ndjson})을 읽는다 — S15P21E201-1047.
	 *
	 * <p>한 줄은 이렇다.
	 * {@code {"contentid":"126508","featureType":"SLOPE_PERCENT","slopePercent":16.4,
	 * "segments":37,"walkLengthM":4820,"radiusM":200}}
	 *
	 * <h2>🔴 값 모양은 채점기가 정한다</h2>
	 *
	 * 점수형 피처는 {@code value} 가 숫자이거나 {@code {"score": …}} 여야 읽힌다
	 * ({@code BaselineCandidateScorer.extractPlaceScore}). 그래서 {@code score} 에 담는다.
	 * 옆에 붙는 {@code radiusM}·{@code segments}·{@code walkLengthM} 은 채점기가 안 읽지만
	 * <b>이 값이 어떻게 나왔는지</b>를 행 안에 남긴다 — 나중에 반경을 바꿨을 때 어느 행이
	 * 옛 반경으로 만들어졌는지 알 수 있어야 한다.
	 *
	 * <h2>🔴 이 값은 추정이다</h2>
	 *
	 * 실측이 아니라 주변 길에서 유도한 값이다({@code bigData/docs/PLACE-SLOPE.md}).
	 * {@link PlaceFeatureLoader} 가 {@code evidence_status} 를 {@code ESTIMATED} 로 넣는다.
	 * 🔴 경사는 DB 가 추정을 막는 네 종({@code ALLERGEN_TAG}·{@code DIETARY_SUPPORT_TAG}·
	 * {@code ACCESSIBILITY_TAG}·{@code STAIRS_PRESENT})에 <b>들어 있지 않다</b> — 그래서
	 * 저장할 수 있다. 계단을 여기에 섞어 넣으면 안 되는 이유이기도 하다.
	 *
	 * <h2>🔴 범위를 벗어난 값은 버리지 않고 멈춘다</h2>
	 *
	 * 경사는 0~100 퍼센트다. 벗어난 값이 오면 산출물이 이상한 것이고, 조용히 버리면
	 * 개수만 줄고 아무도 못 알아챈다.
	 */
	public static Counts readPlaceSlopes(Path file, int chunkSize, Consumer<List<Fact>> chunkConsumer) {
		return read(file, chunkSize, chunkConsumer, (node, out) -> {
			String contentId = text(node, "contentid");
			JsonNode percent = node.path("slopePercent");
			if (contentId == null || !percent.isNumber()) {
				return false;
			}
			double value = percent.asDouble();
			if (value < 0 || value > 100) {
				throw new IllegalArgumentException(
						"장소 경사 산출물에 범위를 벗어난 값이 있다: " + value + "% (contentid " + contentId + ")");
			}
			ObjectNode payload = MAPPER.createObjectNode();
			payload.put("score", value);
			copyNumber(node, payload, "radiusM");
			copyNumber(node, payload, "segments");
			copyNumber(node, payload, "walkLengthM");
			// 🔴 열쇠가 contentid 다. 안 적으면 상가업소번호로 읽혀 한 곳도 못 찾고,
			//    그때 예외는 안 나고 "장소가 없어 못 넣음" 으로만 세어진다.
			out.add(new Fact(contentId, "SLOPE_PERCENT", write(payload), TourApiPlaceLoader.SOURCE_TYPE));
			return true;
		});
	}

	/**
	 * 0~100 눈금의 점수형 산출물을 읽는다 — S15P21E201-1167.
	 *
	 * <p>조용함({@code place-quietness.ndjson} · {@code -sbiz})과 로컬성({@code place-locality.ndjson} ·
	 * {@code -sbiz})이 같은 모양이라 한 함수가 둘을 다 읽는다. 값 칸 이름만 다르다.
	 *
	 * <pre>
	 * {"contentid":"129156","featureType":"QUIETNESS_SCORE","quietnessScore":90,"noiseP90":0.1,"radiusM":200}
	 * {"sourceType":"SBIZ","sourceId":"MA01…","featureType":"LOCALITY_SCORE","localityScore":66.2,"shops":160}
	 * </pre>
	 *
	 * <h2>🔴 열쇠 모양 둘을 다 읽는다</h2>
	 *
	 * {@code contentid} 가 있으면 관광공사, {@code sourceType}+{@code sourceId} 가 있으면 그 출처다.
	 * <b>경사({@link #readPlaceSlopes})는 관광공사만 읽어서 상가 절반(2,355곳)을 마이그레이션으로
	 * 따로 넣어야 했다.</b> 같은 일을 반복하지 않는다.
	 *
	 * <h2>🔴 100 으로 나눠 저장한다 — 채점기 눈금이 0~1 이다</h2>
	 *
	 * 산출물은 사람이 읽기 좋게 0~100 인데, 채점기는 이 축들을 <b>0~1 로 안다</b>
	 * ({@code BaselineCandidateScorer} 가 {@code SLOPE_PERCENT} 하나만 100 으로 나눈다).
	 * 그대로 넣으면 {@code 1 - |장소값 - 선호값|} 이 음수가 되고 {@code clamp01} 이
	 * <b>전부 0 으로 뭉갠다</b> — {@code PreferenceJson} 클래스 주석이 그 사고를 기록해 두었다.
	 * 산출물을 고치지 않고 <b>여기서</b> 맞추는 이유는, 0~100 이 이미 머지돼 문서에 적혀 있고
	 * 사람이 읽는 값이기 때문이다.
	 *
	 * <h2>🔴 나눈 값을 검산한다 — 두 번 나누는 사고를 막는다</h2>
	 *
	 * 조용함 산출물은 {@code quietnessScore = (1 - noiseP90) × 100} 이 성립한다. 그 관계를 여기서
	 * 확인한다. 나중에 산출물이 0~1 로 바뀌면 이 함수가 <b>또 100 으로 나눠</b> 0.009 같은 값이
	 * 되는데, 그 값은 범위 검사를 통과하고 축을 다시 전부 0 으로 만든다 — 조용히 지나가는 대신
	 * 여기서 멈춘다. {@code noiseP90} 이 없는 산출물(로컬성)은 이 검산을 건너뛴다.
	 *
	 * @param valueField 값이 든 칸 이름 — {@code quietnessScore} · {@code localityScore}
	 * @param featureType 저장할 표식 종류 — {@code QUIETNESS_SCORE} · {@code LOCALITY_SCORE}
	 */
	public static Counts readPlaceScores(Path file, String valueField, String featureType, int chunkSize,
			Consumer<List<Fact>> chunkConsumer) {
		requirePercentScale(file, valueField, featureType);
		return read(file, chunkSize, chunkConsumer, (node, out) -> {
			String contentId = text(node, "contentid");
			String keySource;
			String storeId;
			if (contentId != null) {
				storeId = contentId;
				keySource = TourApiPlaceLoader.SOURCE_TYPE;
			}
			else {
				storeId = text(node, "sourceId");
				keySource = text(node, "sourceType");
				if (storeId == null || keySource == null) {
					return false;
				}
				// 🔴 오타를 조용히 두면 엉뚱한 장소 아이디를 계산해 "장소가 없어 못 넣음" 으로만
				//    세어진다. readVisitorFacts 가 같은 이유로 같은 검사를 한다.
				if (!NAMESPACES.contains(keySource)) {
					throw new IllegalArgumentException(
							"모르는 sourceType 이다: " + keySource + " (아는 것: " + NAMESPACES + ")");
				}
			}

			JsonNode raw = node.path(valueField);
			if (!raw.isNumber()) {
				return false;
			}
			double percent = raw.asDouble();
			if (percent < 0 || percent > 100) {
				throw new IllegalArgumentException("%s 산출물에 범위를 벗어난 값이 있다: %s (열쇠 %s)"
						.formatted(featureType, percent, storeId));
			}
			double score = percent / 100.0;

			JsonNode noise = node.path("noiseP90");
			if (noise.isNumber()) {
				double expected = 1.0 - noise.asDouble();
				if (Math.abs(score - expected) > SCORE_CROSS_CHECK_TOLERANCE) {
					throw new IllegalArgumentException(
							("%s 산출물의 눈금이 안 맞는다: %s/100 = %s 인데 1 - noiseP90 = %s 다 (열쇠 %s). "
									+ "산출물이 이미 0~1 로 바뀐 것은 아닌지 확인하라 — 그렇다면 여기서 "
									+ "또 나누면 안 된다")
									.formatted(featureType, percent, score, expected, storeId));
				}
			}

			ObjectNode payload = MAPPER.createObjectNode();
			payload.put("score", score);
			// 이 값이 어떻게 나왔는지를 행 안에 남긴다 — 나중에 반경을 바꿨을 때 어느 행이 옛
			// 기준으로 만들어졌는지 알 수 있어야 한다. 채점기는 score 만 읽는다.
			copyNumber(node, payload, "radiusM");
			copyNumber(node, payload, "noiseP90");
			copyNumber(node, payload, "roads");
			copyNumber(node, payload, "roadLengthM");
			copyNumber(node, payload, "shops");
			// 그늘 — S15P21E201-1184. 🔴 treeDensity 는 검산용이 아니다(shadeScore 와 일정한
			// 비율이 아니다). 어떻게 나온 값인지를 남기려고 옮길 뿐이다.
			copyNumber(node, payload, "treeDensity");
			copyNumber(node, payload, "sections");
			copyNumber(node, payload, "plantedM");
			out.add(new Fact(storeId, featureType, write(payload), keySource));
			return true;
		});
	}

	/**
	 * 검산 허용 오차. 산출물이 정수로 반올림돼 있어({@code quietnessScore:35} · {@code noiseP90:0.65})
	 * 0.01 보다 작은 차이는 반올림에서 온다.
	 */
	private static final double SCORE_CROSS_CHECK_TOLERANCE = 0.01;

	/**
	 * 이 줄 수를 넘는 파일에서만 눈금을 판정한다 — 아래 {@link #requirePercentScale} 참고.
	 *
	 * <p>줄이 몇 개뿐이면 0~100 눈금이어도 값이 우연히 전부 1 이하일 수 있다. 실제 산출물은
	 * 363줄이 가장 작다.
	 */
	private static final int SCALE_CHECK_MIN_ROWS = 50;

	/**
	 * 🔴 <b>이미 0~1 로 바뀐 산출물을 또 나누려는 것</b>을 한 줄도 넣기 전에 잡는다 —
	 * S15P21E201-1184.
	 *
	 * <h2>왜 한 줄로는 못 가리나</h2>
	 *
	 * {@code 0.5} 한 줄만 보면 0~100 눈금의 작은 값인지 0~1 눈금의 큰 값인지 <b>가를 수 없다.</b>
	 * 그래서 범위 검사({@code 0 이상 100 이하})는 이 사고를 못 막는다 — 두 번 나눈 값
	 * {@code 0.009} 는 그 검사를 통과하고, 채점기에서 축을 통째로 0 으로 만든다.
	 *
	 * <h2>파일 전체를 보면 갈린다</h2>
	 *
	 * 수백 줄짜리 0~100 산출물에서 <b>모든 값이 1 이하일 수는 사실상 없다.</b> 실측으로도 네
	 * 산출물 전부 최댓값이 99 를 넘는다(그늘 99.8·99.9, 로컬 100·99.9).
	 *
	 * <h2>🔴 왜 읽기 <b>전</b>인가</h2>
	 *
	 * 다 읽고 나서 던지면 앞쪽 덩어리는 <b>이미 DB 에 들어간 뒤</b>다 — 저장이 덩어리마다
	 * 일어나기 때문이다. 그러면 잘못된 눈금의 행이 남고, 그 상태는 「일부만 이상한 값」이라
	 * 알아채기가 더 어렵다. 한 번 더 훑는 값이 그것보다 싸다(가장 큰 파일이 2,400줄이다).
	 *
	 * <h2>조용함은 이 검사가 없어도 된다 — 그래도 함께 건다</h2>
	 *
	 * 조용함에는 {@code noiseP90} 이라는 짝이 있어 줄마다 검산할 수 있다. 그늘과 로컬에는
	 * <b>그런 짝이 없다</b>({@code treeDensity} 는 {@code shadeScore} 와 일정한 비율이 아니다 —
	 * 실측 527·687·784배). 축마다 다르게 두지 않는 이유는, 나중에 짝이 있는 축이 하나 더
	 * 생겼을 때 <b>어느 축에 무슨 검사가 걸려 있는지</b>를 다시 세지 않으려는 것이다.
	 */
	private static void requirePercentScale(Path file, String valueField, String featureType) {
		double max = Double.NEGATIVE_INFINITY;
		int seen = 0;
		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				JsonNode value = MAPPER.readTree(line).path(valueField);
				if (value.isNumber()) {
					max = Math.max(max, value.asDouble());
					seen++;
				}
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException("산출물을 읽을 수 없다: " + file.toAbsolutePath(), ex);
		}

		if (seen >= SCALE_CHECK_MIN_ROWS && max <= 1.0) {
			throw new IllegalArgumentException(
					("%s 산출물이 이미 0~1 눈금으로 보인다 — %d줄의 최댓값이 %s 다 (파일 %s). "
							+ "이 적재기는 0~100 을 받아 100 으로 나눠 넣으므로, 이대로 넣으면 값이 "
							+ "100배 작아져 채점기에서 이 축이 통째로 0 이 된다. 산출물 눈금을 확인하라")
									.formatted(featureType, seen, max, file.getFileName()));
		}
	}

	/** 있으면 그대로 옮긴다. 없으면 만들어 넣지 않는다. */
	private static void copyNumber(JsonNode from, ObjectNode to, String field) {
		JsonNode value = from.path(field);
		if (value.isNumber()) {
			to.put(field, value.asDouble());
		}
	}

	/** 한 줄을 사실로 바꾼다. 열쇠나 값이 없으면 {@code false} 를 돌려 그 줄을 버린다. */
	private interface LineMapper {

		boolean map(JsonNode node, List<Fact> out);

	}

	private static Counts read(Path file, int chunkSize, Consumer<List<Fact>> chunkConsumer, LineMapper mapper) {
		int total = 0;
		int skipped = 0;
		int usable = 0;
		List<Fact> chunk = new ArrayList<>(chunkSize);
		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				total++;
				JsonNode node = MAPPER.readTree(line);
				int before = chunk.size();
				if (!mapper.map(node, chunk)) {
					skipped++;
					continue;
				}
				usable += chunk.size() - before;
				if (chunk.size() >= chunkSize) {
					chunkConsumer.accept(List.copyOf(chunk));
					chunk.clear();
				}
			}
		}
		catch (IOException exception) {
			throw new UncheckedIOException("산출물을 읽지 못했다: " + file, exception);
		}
		if (!chunk.isEmpty()) {
			chunkConsumer.accept(List.copyOf(chunk));
		}
		return new Counts(total, skipped, usable);
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (!value.isTextual()) {
			return null;
		}
		String text = value.asText().trim();
		return text.isEmpty() ? null : text;
	}

	private static String write(ObjectNode node) {
		try {
			return MAPPER.writeValueAsString(node);
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

}
