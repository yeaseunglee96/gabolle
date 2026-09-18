package com.gabolle.backend.place.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관광공사 수집본을 읽는다 — S15P21E201-854.
 *
 * <p>{@code bigData/data/raw/tourapi/tourapi-busan.ndjson} 은 한 줄이 <b>한 번의 API 응답</b>이다
 * (가게 하나가 아니다). {@code stage} 가 {@code list} 인 줄에 항목 여러 개가 들어 있고,
 * {@code detail} 인 줄은 영업시간·휴무일이라 이 적재가 안 읽는다.
 *
 * <p>🔴 {@code raw} 는 <b>객체가 아니라 문자열</b>이다. 원문을 손대지 않고 그대로 보관하는 쪽을
 * 수집기가 골랐기 때문이다. 그래서 한 번 더 파싱한다 — 그 사실을 모르면 조용히 0 건이 된다.
 *
 * <h2>🔴 음식(A05) 328곳은 안 넘긴다</h2>
 * 이 자료에는 <b>상가업소번호가 없다.</b> 이미 적재된 음식점 2,355곳과 같은 가게인지 판정할
 * 방법이 없어서, 넘기면 같은 가게가 두 행이 된다. 그러면 후보 수가 부풀어 백분위가 좋아
 * <b>보이고</b>, 정답이 두 행 중 하나에만 붙어 나머지가 오답으로 학습된다 —
 * {@code SbizPlaceLoader.placeIdOf} 주석이 같은 이유로 중복을 막는다.
 *
 * <p>이으려면 이름과 자리를 함께 봐야 하고({@code bigData/process/place-link.mjs}) 그 입력이
 * 저장소에 없다. 그것이 풀리면 별도 티켓으로 넣는다.
 *
 * <h2>한 줄이 깨져도 나머지는 넣는다</h2>
 * {@link TruthSignalReader}·{@link ResearchQueueReader} 와 같은 방식이다. 다만 <b>몇 줄을
 * 버렸는지 세어 올린다</b> — 조용히 줄어들면 적재가 성공한 것으로 보인다.
 */
public final class TourApiPlaceReader {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	/** 음식. 적재 대상이 아니다 — 위 javadoc 참고. */
	private static final String CAT1_FOOD = "A05";

	private TourApiPlaceReader() {
	}

	/**
	 * @param totalLines 읽은 줄 (한 줄이 한 응답이다)
	 * @param listLines 그중 목록 단계 줄
	 * @param items 목록에 들어 있던 항목
	 * @param usable 넘긴 것
	 * @param skippedFood 음식이라 버린 것
	 * @param skippedBroken 칸이 모자라거나 형식이 깨져 버린 것
	 * @param skippedDuplicate 같은 {@code contentid} 가 두 번 나와 버린 것
	 */
	public record Counts(int totalLines, int listLines, int items, int usable, int skippedFood,
			int skippedBroken, int skippedDuplicate) {

		@Override
		public String toString() {
			return ("읽은 줄 %d (목록 %d) · 항목 %d · 넘긴 %d · 음식이라 버린 %d · "
					+ "형식이 깨져 버린 %d · 중복이라 버린 %d")
					.formatted(this.totalLines, this.listLines, this.items, this.usable,
							this.skippedFood, this.skippedBroken, this.skippedDuplicate);
		}
	}

	public record Loaded(Counts counts, List<TourApiPlaceRow> rows) {
	}

	public static Loaded read(Path file) {
		int totalLines = 0;
		int listLines = 0;
		int items = 0;
		int food = 0;
		int broken = 0;
		int duplicate = 0;
		List<TourApiPlaceRow> rows = new ArrayList<>();
		// 같은 contentid 가 쪽 경계에서 두 번 올 수 있다. 먼저 본 것을 남긴다.
		Set<String> seen = new LinkedHashSet<>();

		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				totalLines++;
				List<JsonNode> parsed;
				try {
					parsed = itemsOf(line);
				}
				catch (RuntimeException exception) {
					broken++;
					continue;
				}
				if (parsed == null) {
					// 목록 단계가 아니다(상세). 버린 것이 아니므로 세지 않는다.
					continue;
				}
				listLines++;
				for (JsonNode item : parsed) {
					items++;
					if (CAT1_FOOD.equals(text(item, "cat1"))) {
						food++;
						continue;
					}
					TourApiPlaceRow row;
					try {
						row = toRow(item);
					}
					catch (RuntimeException exception) {
						broken++;
						continue;
					}
					if (row == null) {
						broken++;
						continue;
					}
					if (!seen.add(row.contentId())) {
						duplicate++;
						continue;
					}
					rows.add(row);
				}
			}
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
		return new Loaded(new Counts(totalLines, listLines, items, rows.size(), food, broken, duplicate),
				rows);
	}

	/** 목록 단계면 항목들을, 상세 단계면 {@code null} 을 돌려준다. */
	private static List<JsonNode> itemsOf(String line) {
		JsonNode envelope = MAPPER.readTree(line);
		if (!"list".equals(text(envelope, "stage"))) {
			return null;
		}
		// 🔴 raw 는 문자열이다. 한 번 더 파싱한다.
		JsonNode raw = MAPPER.readTree(envelope.path("raw").asString());
		JsonNode item = raw.path("response").path("body").path("items").path("item");
		List<JsonNode> out = new ArrayList<>();
		if (item.isArray()) {
			item.forEach(out::add);
		}
		else if (item.isObject()) {
			// 항목이 하나면 배열이 아니라 객체로 온다 — 공공데이터 응답의 흔한 모양이다.
			out.add(item);
		}
		return out;
	}

	private static TourApiPlaceRow toRow(JsonNode item) {
		String contentId = text(item, "contentid");
		String title = text(item, "title");
		String mapx = text(item, "mapx");
		String mapy = text(item, "mapy");
		if (contentId == null || title == null || mapx == null || mapy == null) {
			// 🔴 좌표가 없으면 장소로 쓸 수 없다. 거리 점수가 이 값을 쓰고, 0 으로 채우면
			//    아프리카 앞바다에 있는 장소가 된다.
			return null;
		}
		double lng = Double.parseDouble(mapx);
		double lat = Double.parseDouble(mapy);
		return new TourApiPlaceRow(contentId, text(item, "contenttypeid"), text(item, "cat1"),
				text(item, "cat3"), title, text(item, "addr1"), lat, lng,
				text(item, "firstimage"), text(item, "cpyrhtDivCd"));
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (value.isMissingNode() || value.isNull()) {
			return null;
		}
		String text = value.asString();
		return (text == null || text.isBlank()) ? null : text.trim();
	}
}
