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
 */
public final class PlaceFeatureNdjsonReader {

	/** 가격대 등급 — {@code priceband-normalize.mjs} 가 낱말 열하나를 접어 만든 넷. */
	static final Set<String> PRICE_BANDS = Set.of("LOW", "MID", "MID_HIGH", "HIGH");

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private PlaceFeatureNdjsonReader() {
	}

	/**
	 * 장소 하나에 붙일 사실 하나.
	 *
	 * @param storeId 장소를 찾는 열쇠. 무엇으로 읽어야 하는지는 {@code keySource} 가 말한다
	 * @param featureType {@code PRICE_LEVEL} · {@code SLOPE_PERCENT} 처럼 무엇에 대한 사실인가
	 * @param value {@code place_feature.value} 에 그대로 들어갈 JSON 문자열
	 * @param keySource 🔴 <b>열쇠가 어느 체계인가</b> — {@link SbizPlaceLoader#SOURCE_TYPE}(상가업소번호)
	 *     이거나 {@link TourApiPlaceLoader#SOURCE_TYPE}({@code contentid})다
	 */
	public record Fact(String storeId, String featureType, String value, String keySource) {

		/**
		 * 🔴 열쇠 체계는 {@code place_feature.source_type} 과 <b>다른 것이다.</b>
		 *
		 * <p>처음에 이 둘을 같은 것으로 보고 {@code source_type} 으로 장소 아이디를 만들려다
		 * DB 통합 시험에 걸렸다. 가격대는 {@code source_type} 이 {@code RESEARCH_PRICEBAND}
		 * (조사에서 왔다)인데 <b>열쇠는 상가업소번호</b>다. 둘은 서로 독립이다 —
		 * 하나는 "값이 어디서 왔나", 하나는 "이 문자열을 무엇으로 읽나" 다.
		 *
		 * <p>그래서 읽는 쪽이 정한다. 파일을 파싱한 쪽이 그 열쇠가 무엇인지 안다.
		 *
		 * <p>이 생성자는 열쇠를 안 적은 기존 호출자를 위한 것이다 — 상가업소번호로 본다.
		 */
		public Fact(String storeId, String featureType, String value) {
			this(storeId, featureType, value, SbizPlaceLoader.SOURCE_TYPE);
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
