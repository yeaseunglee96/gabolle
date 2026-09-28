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
import java.util.UUID;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * bigData 가 만들어 둔 산출물을 {@code place_feature} 행으로 옮긴다.
 *
 * <p>이름으로 짝을 맞추지 않는다. 산출물이 장소 고유 번호를 들고 있고 장소 아이디가 그 번호에서
 * 계산되므로, 짝은 정확히 하나이거나 아예 없다.
 *
 * <p>{@code value} 가 JSONB 라 아무 모양이나 들어간다. 그래서 값의 모양을 정하는 자리가 여기고,
 * 모르는 낱말이 오면 멈춘다 — 조용히 버리면 개수만 줄고 아무도 못 알아챈다.
 *
 * <p>{@link Fact} 가 {@code keySource} 를 들고 다니므로 여기서 내는 사실은 어느 출처의 장소에도
 * 붙을 수 있다.
 *
 * <p>유명세는 여기서 다루지 않는다. {@code PopularityScoreLoader} 가 다른 방식으로 넣으므로,
 * 경로가 둘이면 어느 쪽이 진짜인지 알 수 없다.
 */
public final class PlaceFeatureNdjsonReader {

	/** 가격대 등급 — {@code priceband-normalize.mjs} 가 낱말 열하나를 접어 만든 넷. */
	static final Set<String> PRICE_BANDS = Set.of("LOW", "MID", "MID_HIGH", "HIGH");

	/** {@link #readVisitorFacts} 가 받는 종류. 셋 다 참거짓형·값형이라 {@code featureKey} 가 없다. */
	static final Set<String> VISITOR_FEATURE_TYPES = Set.of("SOLO_FRIENDLY", "BREAK_TIME", "LAST_ORDER_TIME");

	/** 지금 이 적재기가 장소를 찾을 수 있는 출처. 새 출처가 생기면 여기부터 늘린다. */
	static final Set<String> NAMESPACES = Set.of("SBIZ", "TOURAPI");

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private PlaceFeatureNdjsonReader() {
	}

	/**
	 * 장소 하나에 붙일 사실 하나. {@code keySource} 는 {@code storeId} 를 무엇으로 읽어야 하는지를
	 * 말하고({@link SbizPlaceLoader#SOURCE_TYPE} 또는 {@link TourApiPlaceLoader#SOURCE_TYPE}),
	 * {@code place_feature.source_type} 과는 다른 것이다 — 하나는 "값이 어디서 왔나", 하나는
	 * "이 문자열을 무엇으로 읽나" 라 서로 독립이다. 그래서 파일을 파싱한 쪽이 정한다.
	 * {@code featureKey} 는 태그형만 값이 있고 참거짓형·값형은 {@code null} 이다.
	 */
	public record Fact(String storeId, String featureType, String value, String keySource, String featureKey) {

		/** 열쇠를 안 적은 호출자를 위한 것 — 상가업소번호로 본다. */
		public Fact(String storeId, String featureType, String value) {
			this(storeId, featureType, value, SbizPlaceLoader.SOURCE_TYPE, null);
		}

		public Fact(String storeId, String featureType, String value, String keySource) {
			this(storeId, featureType, value, keySource, null);
		}
	}

	/**
	 * 버린 줄을 세어서 돌려준다 — 몇이 빠졌는지 모르면 나중에 "왜 그 가게에 값이 없나" 에 답할
	 * 수 없다. {@code total} 은 빈 줄을 뺀 전체다.
	 */
	public record Counts(int total, int skippedNoValue, int usable) {

		@Override
		public String toString() {
			return "줄 " + this.total + " · 값이 없어 뺌 " + this.skippedNoValue + " · 쓸 수 있는 것 " + this.usable;
		}
	}

	/**
	 * 가격대 산출물을 읽는다. 한 줄은 {@code {"placeId":"MA0101…","raw":"mid","band":"MID"}} 다.
	 *
	 * <p>등급을 숫자로 바꾸지 않는다. {@code MID_HIGH} 를 1~4 중 어디에 둘지는 아직 아무도 안
	 * 정했고, 한 번 들어간 숫자는 계약처럼 굳는다. 접는 것은 쓰는 쪽에서 나중에도 할 수 있지만
	 * 펴는 것은 못 한다.
	 */
	public static Counts readPriceBands(Path file, int chunkSize, Consumer<List<Fact>> chunkConsumer) {
		return read(file, chunkSize, chunkConsumer, (node, out) -> {
			String storeId = text(node, "placeId");
			String band = text(node, "band");
			if (storeId == null || band == null) {
				return false;
			}
			if (!PRICE_BANDS.contains(band)) {
				// 모르는 등급을 조용히 버리면 개수만 줄고 아무도 못 알아챈다.
				throw new IllegalArgumentException("가격대 산출물에 모르는 등급이 있다: " + band
						+ " (아는 것: " + PRICE_BANDS + ")");
			}
			ObjectNode value = MAPPER.createObjectNode();
			value.put("band", band);
			String raw = text(node, "raw");
			if (raw != null) {
				// 조사원이 실제로 쓴 낱말. 접은 것이 틀렸다는 것을 알아도 되돌릴 수 있게 남긴다.
				value.put("raw", raw);
			}
			out.add(new Fact(storeId, "PRICE_LEVEL", write(value)));
			return true;
		});
	}

	/**
	 * 혼밥 안심·브레이크타임·라스트오더 산출물을 읽는다. 한 줄은
	 * {@code {"namespace":"TOURAPI","storeId":"129156","featureType":"SOLO_FRIENDLY","value":true}}
	 * 다. {@code value} 는 참거짓형이면 JSON 리터럴, 시각형이면
	 * {@code {"start":"15:00","end":"17:00"}} 같은 객체이고 어느 모양이든 그대로 옮긴다.
	 *
	 * <p>{@code namespace} 오타를 조용히 두면 엉뚱한 장소 아이디를 계산해 "장소가 없어 못 넣음"
	 * 으로 세어지고 알아챌 방법이 없다. 그래서 모르는 값이면 멈춘다.
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
	 * agy 헤드리스 가격+narrative 통합 조사 산출물(price-queue.mjs, S15P21E201-1414)을 읽는다.
	 * 한 줄은
	 * {@code {"placeId":"MA0101…","price":{"found":true,"priceWon":8500,"priceMenu":"…"},
	 * "whyPeopleGo":[{"type":"오랜 역사","note":"…"}],"sources":["https://…"]}} 같은 모양이고,
	 * 한 줄에서 최대 둘까지 사실이 나온다 — 가격({@code MENU_PRICE_WON})과 "왜 가는지"
	 * ({@code WHY_VISIT}).
	 *
	 * <p>가격은 {@code price.found} 가 참이고 {@code priceWon} 이 숫자일 때만 낸다. 몰라서 못
	 * 찾은 곳을 0원으로 적지 않는다 — 이 저장소가 어디서든 지키는 규칙 그대로다.
	 * "왜 가는지" 는 {@code whyPeopleGo} 가 비어 있지 않을 때만 내고, 근거 주소(sources)를
	 * 같이 싣는다 — 나중에 사람이 원문을 다시 확인할 수 있게.
	 *
	 * <p>🔴 {@code descriptors.priceBand}(cheap/mid/high)는 여기서 안 옮긴다. 이미 있는
	 * {@code PRICE_LEVEL}(사람이 손으로 찾은 LOW/MID/MID_HIGH/HIGH, {@link #readPriceBands})과
	 * 낱말 목록이 달라서, 같은 종류에 섞으면 읽는 쪽이 두 값 목록을 다 알아야 한다.
	 *
	 * <p>🔴 영업시간·혼잡도·현지인 비중·메뉴 다양성은 이번엔 안 옮긴다. 아직 아무 화면도 이
	 * 값을 읽지 않는다 — 먼저 가격·narrative 둘만 넣고, 나머지는 실제로 쓸 곳이 정해지면 그때
	 * 새 종류로 연다.
	 */
	public static Counts readPriceNarrative(Path file, int chunkSize, Consumer<List<Fact>> chunkConsumer) {
		return read(file, chunkSize, chunkConsumer, (node, out) -> {
			String storeId = text(node, "placeId");
			if (storeId == null) {
				return false;
			}
			boolean any = false;

			JsonNode price = node.path("price");
			JsonNode priceWon = price.path("priceWon");
			if (price.path("found").asBoolean(false) && priceWon.isNumber()) {
				ObjectNode value = MAPPER.createObjectNode();
				value.put("priceWon", priceWon.asInt());
				String menu = text(price, "priceMenu");
				if (menu != null) {
					value.put("menu", menu);
				}
				out.add(new Fact(storeId, "MENU_PRICE_WON", write(value)));
				any = true;
			}

			JsonNode reasons = node.path("whyPeopleGo");
			if (reasons.isArray() && !reasons.isEmpty()) {
				ObjectNode value = MAPPER.createObjectNode();
				ArrayNode reasonsOut = value.putArray("reasons");
				for (JsonNode reason : reasons) {
					String type = text(reason, "type");
					String note = text(reason, "note");
					if (type == null && note == null) {
						continue;
					}
					ObjectNode reasonOut = reasonsOut.addObject();
					if (type != null) {
						reasonOut.put("type", type);
					}
					if (note != null) {
						reasonOut.put("note", note);
					}
				}
				if (!reasonsOut.isEmpty()) {
					JsonNode sources = node.path("sources");
					if (sources.isArray() && !sources.isEmpty()) {
						ArrayNode sourcesOut = value.putArray("sources");
						for (JsonNode source : sources) {
							if (source.isTextual()) {
								sourcesOut.add(source.asText());
							}
						}
					}
					out.add(new Fact(storeId, "WHY_VISIT", write(value)));
					any = true;
				}
			}

			return any;
		});
	}

	/**
	 * 장소 경사 산출물을 읽는다. 한 줄은
	 * {@code {"contentid":"126508","slopePercent":16.4,"segments":37,"radiusM":200}} 같은 모양이다.
	 *
	 * <p>점수형 피처는 {@code value} 가 숫자이거나 {@code {"score": …}} 여야 채점기가 읽으므로
	 * {@code score} 에 담는다. 옆에 붙는 칸들은 채점기가 안 읽지만, 나중에 반경을 바꿨을 때 어느
	 * 행이 옛 기준으로 만들어졌는지 알 수 있게 남긴다.
	 *
	 * <p>이 값은 실측이 아니라 주변 길에서 유도한 추정이다. 경사는 DB 가 추정을 막는 네 종에
	 * 들어 있지 않아 저장할 수 있다 — 계단을 여기에 섞어 넣으면 안 되는 이유이기도 하다.
	 *
	 * <p>0~100 퍼센트를 벗어난 값은 버리지 않고 멈춘다. 조용히 버리면 개수만 줄고 아무도 못
	 * 알아챈다.
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
			// 열쇠가 contentid 다. 안 적으면 상가업소번호로 읽혀 한 곳도 못 찾고, 예외 없이
			// "장소가 없어 못 넣음" 으로만 세어진다.
			out.add(new Fact(contentId, "SLOPE_PERCENT", write(payload), TourApiPlaceLoader.SOURCE_TYPE));
			return true;
		});
	}

	/**
	 * 장소 경사 — 장소 번호판을 읽는다 (S15P21E201-1625). {@code bigData/process/place-slope-by-id.mjs} 가 낸
	 * 줄은 {@code {"placeId":"…","slopePercent":3.2,"segments":38,"walkLengthM":6930,"radiusM":200,"stat":"p50"}}.
	 *
	 * <p>{@link #readPlaceSlopes} 와 달리 열쇠가 <b>우리 장소 번호 그대로</b>다. 관광공사·상가 번호로만 붙이던
	 * 때는 오픈스트리트맵·카카오 장소에 값이 없었다(6,933곳 중 2,682곳). 값을 {@code score} 에 담고 만든 방법을
	 * 옆에 남기는 것, 0~100 을 벗어나면 멈추는 것은 같다. 장소 번호가 깨져 있어도 멈춘다 — 적재기가 번호를 읽다
	 * 실패하면 덩어리 전체가 못 들어간다.
	 */
	public static Counts readPlaceSlopesById(Path file, int chunkSize, Consumer<List<Fact>> chunkConsumer) {
		return read(file, chunkSize, chunkConsumer, (node, out) -> {
			String placeId = text(node, "placeId");
			JsonNode percent = node.path("slopePercent");
			if (placeId == null || !percent.isNumber()) {
				return false;
			}
			try {
				UUID.fromString(placeId);
			}
			catch (IllegalArgumentException ex) {
				throw new IllegalArgumentException("장소 경사 산출물에 장소 번호가 아닌 열쇠가 있다: " + placeId, ex);
			}
			double value = percent.asDouble();
			if (value < 0 || value > 100) {
				throw new IllegalArgumentException(
						"장소 경사 산출물에 범위를 벗어난 값이 있다: " + value + "% (placeId " + placeId + ")");
			}
			ObjectNode payload = MAPPER.createObjectNode();
			payload.put("score", value);
			copyNumber(node, payload, "radiusM");
			copyNumber(node, payload, "segments");
			copyNumber(node, payload, "walkLengthM");
			String stat = text(node, "stat");
			if (stat != null) {
				payload.put("stat", stat);
			}
			out.add(new Fact(placeId, "SLOPE_PERCENT", write(payload), PlaceFeatureLoader.PLACE_ID_KEY));
			return true;
		});
	}

	/**
	 * 0~100 눈금의 점수형 산출물을 읽는다. 조용함·로컬성·그늘이 같은 모양이라 한 함수가 다 읽고
	 * 값 칸 이름({@code valueField})과 표식 종류({@code featureType})만 다르다.
	 *
	 * <pre>
	 * {"contentid":"129156","quietnessScore":90,"noiseP90":0.1,"radiusM":200}
	 * {"sourceType":"SBIZ","sourceId":"MA01…","localityScore":66.2,"shops":160}
	 * </pre>
	 *
	 * <p>열쇠 모양 둘을 다 읽는다 — {@code contentid} 가 있으면 관광공사,
	 * {@code sourceType}+{@code sourceId} 가 있으면 그 출처다. 한쪽만 읽으면 나머지 절반을
	 * 마이그레이션으로 따로 넣어야 한다.
	 *
	 * <p>100 으로 나눠 저장한다. 산출물은 사람이 읽기 좋게 0~100 인데 채점기는 이 축들을 0~1 로
	 * 알고, 그대로 넣으면 {@code 1 - |장소값 - 선호값|} 이 음수가 되어 축이 전부 0 으로 뭉개진다.
	 * 산출물이 아니라 여기서 맞추는 것은 0~100 이 이미 문서에 적힌 사람이 읽는 값이어서다.
	 *
	 * <p>{@code noiseP90} 이 있으면 {@code 점수 = 1 - noiseP90} 으로 검산한다. 산출물이 나중에
	 * 0~1 로 바뀌면 여기서 또 나눠 0.009 같은 값이 되는데, 그 값은 범위 검사를 통과하고 축을 다시
	 * 0 으로 만든다. {@code noiseP90} 이 없는 산출물은 이 검산을 건너뛴다.
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
				// 오타를 조용히 두면 엉뚱한 장소 아이디를 계산해 "장소가 없어 못 넣음" 으로만
				// 세어진다.
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
			// 채점기는 score 만 읽는다. 나머지는 이 값이 어떻게 나왔는지를 행 안에 남기는 것이다.
			copyNumber(node, payload, "radiusM");
			copyNumber(node, payload, "noiseP90");
			copyNumber(node, payload, "roads");
			copyNumber(node, payload, "roadLengthM");
			copyNumber(node, payload, "shops");
			// treeDensity 는 검산용이 아니다 — shadeScore 와 일정한 비율이 아니다.
			copyNumber(node, payload, "treeDensity");
			copyNumber(node, payload, "sections");
			copyNumber(node, payload, "plantedM");
			out.add(new Fact(storeId, featureType, write(payload), keySource));
			return true;
		});
	}

	/** 검산 허용 오차. 산출물이 정수로 반올림돼 있어 이보다 작은 차이는 반올림에서 온다. */
	private static final double SCORE_CROSS_CHECK_TOLERANCE = 0.01;

	/**
	 * 이 줄 수를 넘는 파일에서만 눈금을 판정한다. 줄이 몇 개뿐이면 0~100 눈금이어도 값이 우연히
	 * 전부 1 이하일 수 있다.
	 */
	private static final int SCALE_CHECK_MIN_ROWS = 50;

	/**
	 * 이미 0~1 로 바뀐 산출물을 또 나누려는 것을 한 줄도 넣기 전에 잡는다.
	 *
	 * <p>한 줄만 보면 {@code 0.5} 가 0~100 의 작은 값인지 0~1 의 큰 값인지 가를 수 없어, 범위
	 * 검사로는 이 사고를 못 막는다 — 두 번 나눈 {@code 0.009} 는 그 검사를 통과하고 채점기에서
	 * 축을 통째로 0 으로 만든다. 파일 전체를 보면 갈린다. 수백 줄짜리 0~100 산출물에서 모든
	 * 값이 1 이하일 수는 사실상 없다.
	 *
	 * <p>읽기 전에 하는 것은 저장이 덩어리마다 일어나기 때문이다. 다 읽고 던지면 앞쪽 덩어리는
	 * 이미 들어간 뒤라 「일부만 이상한 값」이 남는다. 한 번 더 훑는 값이 그것보다 싸다.
	 *
	 * <p>{@code noiseP90} 으로 줄마다 검산할 수 있는 축에도 이 검사를 함께 건다 — 축마다 다르게
	 * 두면 어느 축에 무슨 검사가 걸려 있는지를 매번 다시 세야 한다.
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
