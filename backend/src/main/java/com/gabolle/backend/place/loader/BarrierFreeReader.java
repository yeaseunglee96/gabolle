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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 무장애 여행 정보 수집본을 읽는다.
 *
 * <p>한 줄이 한 번의 API 응답이고 {@code stage} 로 목록과 상세가 갈린다. 접근성 칸은 상세에만
 * 있으므로 목록 줄은 읽지 않는다 — 장소 적재가 목록만 읽는 것과 반대다.
 *
 * <p>{@code raw} 는 객체가 아니라 문자열이다. 그 사실을 모르면 조용히 0 건이 된다.
 *
 * <p>붙일 코드가 없는 줄은 넘긴다. 넘기는 수가 대부분이 되는 것이 정상이다.
 */
public final class BarrierFreeReader {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	private BarrierFreeReader() {
	}

	public record Loaded(List<BarrierFreeRow> rows, Counts counts) {
	}

	/**
	 * {@code noCode} 는 접근성을 말하지 않아 넘긴 장소, {@code broken} 은 JSON 이 깨졌거나
	 * 식별자가 없어 넘긴 줄, {@code duplicate} 는 같은 식별자가 두 번 나와 넘긴 것이다.
	 */
	public record Counts(int totalLines, int detailLines, int noCode, int broken, int duplicate,
			Map<String, Integer> byCode) {

		@Override
		public String toString() {
			return "읽은 줄 %d (상세 %d) · 접근성을 안 말해 넘긴 %d · 깨진 %d · 중복 %d · 코드별 %s"
					.formatted(this.totalLines, this.detailLines, this.noCode, this.broken,
							this.duplicate, this.byCode);
		}
	}

	public static Loaded read(Path file) {
		List<BarrierFreeRow> rows = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		Map<String, Integer> byCode = new TreeMap<>();
		int totalLines = 0;
		int detailLines = 0;
		int noCode = 0;
		int broken = 0;
		int duplicate = 0;

		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				totalLines++;
				JsonNode envelope;
				try {
					envelope = MAPPER.readTree(line);
				}
				catch (RuntimeException ex) {
					broken++;
					continue;
				}
				if (!"detail".equals(envelope.path("stage").asString())) {
					continue;
				}
				detailLines++;

				JsonNode raw;
				try {
					raw = MAPPER.readTree(envelope.path("raw").asString());
				}
				catch (RuntimeException ex) {
					broken++;
					continue;
				}
				JsonNode item = raw.path("response").path("body").path("items").path("item");
				if (item.isArray()) {
					item = item.isEmpty() ? null : item.get(0);
				}
				if (item == null || item.isMissingNode()) {
					broken++;
					continue;
				}

				String contentId = item.path("contentid").asString();
				if (contentId == null || contentId.isBlank()) {
					broken++;
					continue;
				}
				if (!seen.add(contentId)) {
					duplicate++;
					continue;
				}
				List<String> codes = BarrierFreeAccessibility.of(
						item.path("exit").asString(), item.path("route").asString(),
						item.path("stroller").asString());
				if (codes.isEmpty()) {
					noCode++;
					continue;
				}
				rows.add(new BarrierFreeRow(contentId, codes));
				codes.forEach((code) -> byCode.merge(code, 1, Integer::sum));
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("무장애 수집본을 읽을 수 없다: " + file, ex);
		}

		return new Loaded(List.copyOf(rows),
				new Counts(totalLines, detailLines, noCode, broken, duplicate, Map.copyOf(byCode)));
	}
}
