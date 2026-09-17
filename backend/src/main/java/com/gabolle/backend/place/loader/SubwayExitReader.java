package com.gabolle.backend.place.loader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 지하철 출구 안내 산출물(NDJSON)을 읽는다 — S15P21E201-479.
 *
 * <p>한 줄은 {@code {"namespace":"TOURAPI","storeId":"129156","subwayExit":"2호선 강남역 3번 출구"}}
 * 다. {@link PlacePhotoReader} 와 같은 이유로, 칸이 빠진 줄을 조용히 건너뛰지 않고
 * <b>멈춘다</b> — 출구 안내 하나가 빠진 것을 아무도 모르는 상태가 "이 장소는 원래 안내가
 * 없다" 와 구분되지 않는다.
 */
final class SubwayExitReader {

	/** 지금 이 적재기가 장소를 찾을 수 있는 출처. {@link PlaceFeatureNdjsonReader#NAMESPACES} 와 같다. */
	private static final Set<String> NAMESPACES = Set.of("SBIZ", "TOURAPI");

	private final ObjectMapper objectMapper;

	SubwayExitReader(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	List<SubwayExitRow> read(Path file) throws IOException {
		List<SubwayExitRow> rows = new ArrayList<>();
		int lineNumber = 0;
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			lineNumber++;
			if (line.isBlank()) {
				continue;
			}
			JsonNode node = this.objectMapper.readTree(line);

			String namespace = text(node, "namespace");
			String storeId = text(node, "storeId");
			String subwayExit = text(node, "subwayExit");
			if (namespace == null || storeId == null || subwayExit == null) {
				throw new IllegalStateException(
						"지하철 출구 산출물 %d 번째 줄에 namespace·storeId·subwayExit 중 빠진 것이 있다: %s"
								.formatted(lineNumber, line));
			}
			if (!NAMESPACES.contains(namespace)) {
				throw new IllegalStateException(
						"%d 번째 줄에 모르는 namespace 가 있다: %s (아는 것: %s)".formatted(lineNumber, namespace, NAMESPACES));
			}

			rows.add(new SubwayExitRow(namespace, storeId, subwayExit));
		}
		return rows;
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			return null;
		}
		String text = value.asString().trim();
		return text.isEmpty() ? null : text;
	}
}
