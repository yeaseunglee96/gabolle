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
import java.util.TreeMap;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 정규화한 영업시간 파일을 읽는다 — S15P21E201-852.
 *
 * <p>입력은 {@code bigData/process/opening-hours.mjs} 의 출력이고 한 줄이 <b>장소 하나</b>다
 * (수집본은 한 줄이 응답 하나였다 — 그쪽과 다르다).
 *
 * <pre>
 * {"contentid":"129156","contentTypeId":12,"status":"ALWAYS_OPEN","byDay":null,
 *  "seasonal":null,"closedDays":[],"notes":[],"raw":{…}}
 * </pre>
 *
 * <h2>네 가지 {@code status} 를 셋으로 접는다</h2>
 * <table border="1">
 * <caption>정규화기가 내는 상태와 이 판독기의 처리</caption>
 * <tr><th>status</th><th>뜻</th><th>넣나</th></tr>
 * <tr><td>{@code PARSED}</td><td>요일별 구간을 읽었다</td><td>🟢 {@code OPENING_HOURS}</td></tr>
 * <tr><td>{@code ALWAYS_OPEN}</td><td>"상시 개방"·"연중무휴"</td><td>🟢 {@code OPENING_HOURS}</td></tr>
 * <tr><td>{@code LODGING}</td><td>숙박 — 체크인·체크아웃</td><td>🟢 {@code CHECK_IN_OUT}</td></tr>
 * <tr><td>{@code UNKNOWN}</td><td>"점포별 상이"·"전화문의 요망" 처럼 원문에 시각이 없다</td>
 *     <td>🔴 <b>안 넣는다</b></td></tr>
 * </table>
 *
 * <h2>🔴 모름에는 행을 만들지 않는다</h2>
 * 이 저장소의 규칙이고 DB 도 강제한다({@code ck_place_feature_unknown_has_no_value}). 행이
 * 없으면 판정기가 "수집 안 했다" 로 답하고 화면에는 <b>위반 없음이 아니라 "확인 못 했음"</b> 이
 * 올라간다 — 그 구분을 지키는 것이 이 값의 요점이다. 값 없는 행을 만들어 두면 그 구분이 사라진다.
 *
 * <p>실측(2026-09-11)에서 {@code UNKNOWN} 은 33곳이었다. 시각을 지어내 채우면 33곳이 "확인했고
 * 문제 없음" 으로 바뀐다.
 *
 * <h2>숙박은 시각이 둘 다 없으면 넘긴다</h2>
 * {@code LODGING} 인데 체크인·체크아웃이 둘 다 비어 있으면 담을 값이 없다. 껍데기만 든 행을
 * 넣으면 판정기가 값이 있는 줄 알고 읽는다.
 */
public final class OpeningHoursReader {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	/** 영업시간. DB 의 {@code ck_place_feature_type} 이 이미 허용한다. */
	public static final String TYPE_OPENING_HOURS = "OPENING_HOURS";

	/** 숙박의 체크인·체크아웃. {@code V20260911000000} 이 허용 목록에 더했다. */
	public static final String TYPE_CHECK_IN_OUT = "CHECK_IN_OUT";

	private static final String STATUS_UNKNOWN = "UNKNOWN";

	private static final String STATUS_LODGING = "LODGING";

	private OpeningHoursReader() {
	}

	/**
	 * @param rows   넣을 것
	 * @param counts 무엇을 몇 개 버렸나
	 */
	public record Loaded(List<OpeningHoursRow> rows, Counts counts) {
	}

	/**
	 * 🔴 버린 것을 갈라 센다. 숫자 하나로 합치면 파일이 낡은 것인지 규칙이 틀린 것인지
	 * 구분할 수 없다.
	 *
	 * @param totalLines       읽은 줄
	 * @param skippedUnknown   원문에 시각이 없어 넘긴 것
	 * @param skippedBroken    JSON 이 깨졌거나 식별자가 없어 넘긴 것
	 * @param skippedDuplicate 같은 식별자가 두 번 나와 넘긴 것
	 * @param byType           넣을 것을 갈래별로
	 */
	public record Counts(int totalLines, int skippedUnknown, int skippedBroken,
			int skippedDuplicate, Map<String, Integer> byType) {

		@Override
		public String toString() {
			return "읽은 줄 %d · 넘길 %d · 시각이 없어 버린 %d · 깨진 %d · 중복이라 버린 %d · 갈래별 %s"
					.formatted(this.totalLines,
							this.byType.values().stream().mapToInt(Integer::intValue).sum(),
							this.skippedUnknown, this.skippedBroken, this.skippedDuplicate,
							this.byType);
		}
	}

	public static Loaded read(Path file) {
		List<OpeningHoursRow> rows = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		Map<String, Integer> byType = new TreeMap<>();
		int totalLines = 0;
		int skippedUnknown = 0;
		int skippedBroken = 0;
		int skippedDuplicate = 0;

		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				totalLines++;
				JsonNode node;
				try {
					node = MAPPER.readTree(line);
				}
				catch (RuntimeException ex) {
					// 한 줄이 깨져도 나머지는 넣는다. 다만 세어 올린다 — 조용히 줄어들면
					// 적재가 성공한 것으로 보인다.
					skippedBroken++;
					continue;
				}
				String contentId = node.path("contentid").asString();
				if (contentId == null || contentId.isBlank()) {
					skippedBroken++;
					continue;
				}
				String status = node.path("status").asString();
				if (STATUS_UNKNOWN.equals(status)) {
					skippedUnknown++;
					continue;
				}
				if (!seen.add(contentId)) {
					skippedDuplicate++;
					continue;
				}
				OpeningHoursRow row = rowOf(contentId, status, node);
				if (row == null) {
					skippedUnknown++;
					continue;
				}
				rows.add(row);
				byType.merge(row.featureType(), 1, Integer::sum);
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("정규화한 영업시간 파일을 읽을 수 없다: " + file, ex);
		}

		return new Loaded(List.copyOf(rows),
				new Counts(totalLines, skippedUnknown, skippedBroken, skippedDuplicate, Map.copyOf(byType)));
	}

	/** 담을 값이 없으면 {@code null} — 부르는 쪽이 넘긴 것으로 센다. */
	private static OpeningHoursRow rowOf(String contentId, String status, JsonNode node) {
		if (STATUS_LODGING.equals(status)) {
			String checkIn = text(node, "checkIn");
			String checkOut = text(node, "checkOut");
			if (checkIn == null && checkOut == null) {
				return null;
			}
			ObjectNode value = MAPPER.createObjectNode();
			value.put("status", status);
			value.put("checkIn", checkIn);
			value.put("checkOut", checkOut);
			copyIfPresent(node, value, "notes");
			copyIfPresent(node, value, "raw");
			return new OpeningHoursRow(contentId, TYPE_CHECK_IN_OUT, value.toString());
		}

		ObjectNode value = MAPPER.createObjectNode();
		value.put("status", status);
		// 🔴 정규화기가 낸 칸을 그대로 옮긴다. byDay 가 비어 있고 seasonal 만 온 4곳도 넣는다 —
		//    오늘을 판정할 수는 없지만 값을 버리면 절기 경계가 정해진 뒤에 되찾을 수 없다.
		for (String field : new String[] { "byDay", "seasonal", "closedDays", "notes", "raw" }) {
			copyIfPresent(node, value, field);
		}
		return new OpeningHoursRow(contentId, TYPE_OPENING_HOURS, value.toString());
	}

	private static void copyIfPresent(JsonNode from, ObjectNode to, String field) {
		JsonNode node = from.get(field);
		if (node != null && !node.isNull()) {
			to.set(field, node);
		}
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			return null;
		}
		String text = value.asString();
		return (text == null || text.isBlank()) ? null : text.trim();
	}
}
