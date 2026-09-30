package com.gabolle.backend.place.loader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 레드테이블 식당 NDJSON(<b>한 줄에 JSON 하나</b>인 글 파일)을 읽는다. 한 줄 모양은
 *
 * <pre>
 * {"rstrId":"39929","name":"밀양가산돼지국밥","lat":35.1405587,"lng":129.0625474,
 *  "roadAddress":"부산광역시 동구 자성로133번길 45","jibunAddress":"부산광역시 동구 범일동 830-173",
 *  "businessType":"한식","licenseType":"일반음식점","tel":"051-632-0409","intro":"…","imageUrl":null,
 *  "surveyRecommenders":1}
 * </pre>
 *
 * <p>🔴 <b>모르는 칸이 오면 멈춘다</b> ({@link PlaceDetailExtrasLoader} 와 같은 생각이다). 오타 난 칸은 조용히 버려지기
 * 쉽고, 버려진 줄 모르고 넣으면 빈 값이 사실처럼 남는다. 필수 칸이 비었거나, 좌표가 부산 밖이거나, 같은 번호가 두 번
 * 나와도 멈춘다. <b>파일 전체를 먼저 검사</b>하므로 한 줄이라도 틀리면 한 행도 안 들어간다 — 설문 장소 몇십 곳은
 * 고쳐서 다시 돌리는 편이 반쯤 들어간 표보다 싸다.
 */
public final class RedtablePlaceReader {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	static final Set<String> FIELDS = Set.of("rstrId", "name", "lat", "lng", "roadAddress", "jibunAddress",
			"businessType", "licenseType", "tel", "intro", "imageUrl", "surveyRecommenders");

	/** 부산을 넉넉히 덮는 상자. 위경도를 뒤바꿔 적은 줄을 여기서 잡는다. */
	private static final double LAT_MIN = 34.8;

	private static final double LAT_MAX = 35.5;

	private static final double LNG_MIN = 128.7;

	private static final double LNG_MAX = 129.4;

	private RedtablePlaceReader() {
	}

	public static List<RedtablePlaceRow> read(Path file) {
		List<String> lines;
		try {
			lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
		List<RedtablePlaceRow> rows = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (line.isBlank()) {
				continue;
			}
			RedtablePlaceRow row;
			try {
				row = parse(line);
			}
			catch (RuntimeException exception) {
				throw new IllegalArgumentException((i + 1) + "번째 줄: " + exception.getMessage(), exception);
			}
			if (!seen.add(row.rstrId())) {
				throw new IllegalArgumentException((i + 1) + "번째 줄: 같은 rstrId 가 또 나왔다 — " + row.rstrId());
			}
			rows.add(row);
		}
		return rows;
	}

	static RedtablePlaceRow parse(String line) {
		JsonNode node = MAPPER.readTree(line);
		if (!node.isObject()) {
			throw new IllegalArgumentException("줄이 JSON 객체가 아니다");
		}
		for (Map.Entry<String, JsonNode> entry : node.properties()) {
			if (!FIELDS.contains(entry.getKey())) {
				throw new IllegalArgumentException("모르는 칸이 있다: " + entry.getKey() + " — 받는 것은 " + FIELDS);
			}
		}
		String rstrId = required(node, "rstrId");
		if (!rstrId.matches("[0-9]+")) {
			throw new IllegalArgumentException("rstrId 는 숫자 글자여야 한다: " + rstrId);
		}
		String name = required(node, "name");
		double lat = number(node, "lat");
		double lng = number(node, "lng");
		if (lat < LAT_MIN || lat > LAT_MAX || lng < LNG_MIN || lng > LNG_MAX) {
			throw new IllegalArgumentException("좌표가 부산 밖이다 (위경도를 뒤바꿨나?): " + lat + ", " + lng);
		}
		String road = optional(node, "roadAddress");
		String jibun = optional(node, "jibunAddress");
		if (road == null && jibun == null) {
			throw new IllegalArgumentException("주소가 둘 다 비었다");
		}
		Integer recommenders = null;
		JsonNode rec = node.path("surveyRecommenders");
		if (!rec.isMissingNode() && !rec.isNull()) {
			if (!rec.isIntegralNumber() || rec.asInt() < 0) {
				throw new IllegalArgumentException("surveyRecommenders 는 0 이상 정수거나 null 이어야 한다: " + rec);
			}
			recommenders = rec.asInt();
		}
		return new RedtablePlaceRow(rstrId, name, lat, lng, road, jibun, optional(node, "businessType"),
				optional(node, "licenseType"), optional(node, "tel"), optional(node, "intro"),
				optional(node, "imageUrl"), recommenders);
	}

	private static String required(JsonNode node, String field) {
		String value = optional(node, field);
		if (value == null) {
			throw new IllegalArgumentException(field + " 가 비었다");
		}
		return value;
	}

	private static String optional(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (value.isMissingNode() || value.isNull()) {
			return null;
		}
		if (!value.isString()) {
			throw new IllegalArgumentException(field + " 는 글자거나 null 이어야 한다: " + value);
		}
		String text = value.asString().trim();
		return text.isEmpty() ? null : text;
	}

	private static double number(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (!value.isNumber()) {
			throw new IllegalArgumentException(field + " 는 숫자여야 한다: " + value);
		}
		return value.asDouble();
	}
}
