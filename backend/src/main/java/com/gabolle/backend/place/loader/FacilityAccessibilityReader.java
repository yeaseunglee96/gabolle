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
 * 장애인편의시설 실태조사 가공본을 읽는다 — S15P21E201-1365.
 *
 * <p>입력은 {@code bigData/data/staged/place-accessibility.ndjson} 이다. 한 줄이 한 시설이고,
 * <b>파일 이름은 데이터 파트가 정본</b>이라 여기서는 경로를 받기만 한다.
 *
 * <pre>
 * {"sourceType":"SBIZ","sourceId":"MA0101...","placeId":"817670a9-...",
 *  "wfcltId":"2614010100-1-02320002","evalRaw":"주출입구(문), 주출입구 접근로"}
 * </pre>
 *
 * <h2>🔴 붙일지 말지는 여기서 정한다. 보내온 판정을 안 쓴다</h2>
 * 데이터 파트도 {@code featureKey} 를 같이 보내지만 읽지 않는다. 규칙이 두 곳에 있으면
 * 한쪽만 고쳐졌을 때 <b>아무 오류 없이</b> 두 판정이 갈린다. 판정은 {@link FacilityAccessibility}
 * 한 곳에만 둔다. 그래서 <b>거른 줄까지 전부 받아</b> 여기서 거른다 — 왜 걸렀는지가
 * 산출물에서 사라지지 않게.
 *
 * <h2>장소 id 를 양쪽이 따로 계산해 맞춰 본다</h2>
 * 데이터 파트는 파이썬으로, 우리는 자바로 같은 식(MD5 기반 이름 UUID)을 구현했다. 보내온
 * {@code placeId} 와 우리 계산이 다르면 <b>그 줄을 버리고 센다.</b> 둘 중 어느 쪽이 틀렸든
 * 그 줄은 엉뚱한 장소에 붙을 수 있고, 그건 조용히 지나간다.
 */
public final class FacilityAccessibilityReader {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	private FacilityAccessibilityReader() {
	}

	/**
	 * @param rows   붙일 것
	 * @param counts 무엇을 몇 개 넘겼나
	 */
	public record Loaded(List<FacilityAccessibilityRow> rows, Counts counts) {
	}

	/**
	 * @param totalLines   읽은 줄
	 * @param noCode       접근 근거가 없어 넘긴 시설. 「주출입구(문)」만 있는 곳이 여기 든다
	 * @param broken       JSON 이 깨졌거나 칸이 비어 넘긴 줄
	 * @param unknownSource 모르는 출처라 넘긴 줄
	 * @param idMismatch   보내온 장소 id 가 우리 계산과 달라 넘긴 줄. <b>0 이어야 정상이다</b>
	 * @param duplicate    같은 장소가 두 번 나와 넘긴 것
	 */
	public record Counts(int totalLines, int noCode, int broken, int unknownSource, int idMismatch,
			int duplicate) {

		@Override
		public String toString() {
			return "읽은 줄 %d · 근거 없어 넘긴 %d · 깨진 %d · 모르는 출처 %d · 장소 id 불일치 %d · 중복 %d"
					.formatted(this.totalLines, this.noCode, this.broken, this.unknownSource,
							this.idMismatch, this.duplicate);
		}
	}

	public static Loaded read(Path file) {
		List<FacilityAccessibilityRow> rows = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		int totalLines = 0;
		int noCode = 0;
		int broken = 0;
		int unknownSource = 0;
		int idMismatch = 0;
		int duplicate = 0;

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
					broken++;
					continue;
				}

				String sourceType = node.path("sourceType").asString();
				String sourceId = node.path("sourceId").asString();
				String facilityId = node.path("wfcltId").asString();
				String evalRaw = node.path("evalRaw").asString();

				if (sourceId == null || sourceId.isBlank() || facilityId == null || facilityId.isBlank()) {
					broken++;
					continue;
				}
				if (!FacilityAccessibilityRow.knownSource(sourceType)) {
					unknownSource++;
					continue;
				}

				List<String> codes = FacilityAccessibility.of(evalRaw);
				if (codes.isEmpty()) {
					noCode++;
					continue;
				}

				FacilityAccessibilityRow row = new FacilityAccessibilityRow(sourceType, sourceId,
						facilityId, evalRaw, codes);
				if (!row.agreesWith(node.path("placeId").asString())) {
					idMismatch++;
					continue;
				}
				if (!seen.add(sourceType + ":" + sourceId)) {
					duplicate++;
					continue;
				}
				rows.add(row);
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("실태조사 가공본을 읽을 수 없다: " + file, ex);
		}

		return new Loaded(List.copyOf(rows),
				new Counts(totalLines, noCode, broken, unknownSource, idMismatch, duplicate));
	}
}
