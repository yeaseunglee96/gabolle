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
	 * @param storeId 상가업소번호. {@link SbizPlaceLoader#placeIdOf} 가 이것으로 장소를 찾는다
	 * @param featureType 지금은 {@code PRICE_LEVEL} 하나뿐이다
	 * @param value {@code place_feature.value} 에 그대로 들어갈 JSON 문자열
	 */
	public record Fact(String storeId, String featureType, String value) {
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
