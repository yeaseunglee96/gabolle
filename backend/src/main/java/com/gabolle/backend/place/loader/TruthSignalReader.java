package com.gabolle.backend.place.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 목록 근거 파일을 읽는다 — S15P21E201-826.
 *
 * <p>{@code bigData/data/staged/truth-linked.ndjson} 은 조사 대상 가게에 "미쉐린·블루리본·
 * 백년가게·택슐랭·블로그100 중 어디에 올랐는가" 를 이어 붙인 것이다. 한 줄이 가게 하나다.
 *
 * <h2>무엇을 근거로 보는가</h2>
 * <b>오른 목록이 하나라도 있으면 근거가 있는 것이다.</b> 처음에는 {@code sourceCount} 를
 * 봤는데 그 칸은 「공개글448」에만 붙어서(몇 편의 글이 지목했나) 블루리본·택슐랭·백년가게·
 * 블로그100 에만 오른 145곳이 통째로 빠졌다. 장효준이 실측으로 짚어 줬다(2026-09-11).
 *
 * <p>목록이 하나도 없는 줄은 <b>0 점을 매기지 않고 아예 안 넘긴다.</b> 0 은 "안 유명하다" 는
 * 관측이고 없는 것은 "안 세어 봤다" 는 무지인데, 점수기는 값이 없으면 그 축을 아예 빼고
 * 값이 0 이면 0 을 곱한다 — 둘은 다른 결과를 낸다.
 *
 * <p>읽는 방식은 {@link ResearchQueueReader} 와 같다. 한 줄이 깨져도 나머지는 넣는다.
 */
public final class TruthSignalReader {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	private TruthSignalReader() {
	}

	/** @param total 읽은 줄 · @param usable 오른 목록이 있어 넘긴 줄 · @param skippedNoSignal 목록이 없어 버린 줄 · @param skippedBroken 형식이 깨져 버린 줄 */
	public record Counts(int total, int usable, int skippedNoSignal, int skippedBroken) {

		@Override
		public String toString() {
			return "읽은 줄 %d · 목록에 오른 %d · 목록이 없어 버린 %d · 형식이 깨져 버린 %d"
					.formatted(this.total, this.usable, this.skippedNoSignal, this.skippedBroken);
		}
	}

	/** 읽은 것 전부. 무게가 전체 분포에서 나오므로 흘려보내지 않고 한 번에 돌려준다(417줄). */
	public record Loaded(Counts counts, List<TruthSignalRow> rows) {
	}

	public static Loaded read(Path file) {
		int total = 0;
		int usable = 0;
		int noSignal = 0;
		int broken = 0;
		List<TruthSignalRow> rows = new ArrayList<>();

		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				total++;
				TruthSignalRow row;
				try {
					row = parse(line);
				}
				catch (RuntimeException exception) {
					broken++;
					continue;
				}
				if (row == null) {
					noSignal++;
					continue;
				}
				usable++;
				rows.add(row);
			}
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
		return new Loaded(new Counts(total, usable, noSignal, broken), List.copyOf(rows));
	}

	/** 근거가 없으면 {@code null} 을 낸다. 형식이 깨졌으면 예외가 나간다 — 부르는 쪽이 둘을 가른다. */
	private static TruthSignalRow parse(String line) {
		JsonNode node = MAPPER.readTree(line);
		String storeId = text(node, "id");
		if (storeId == null) {
			throw new IllegalArgumentException("상가업소번호가 없다");
		}
		JsonNode truth = node.path("truth");
		List<String> lists = new ArrayList<>();
		for (JsonNode name : truth.path("lists")) {
			if (name.isString() && !name.asString().isBlank()) {
				lists.add(name.asString());
			}
		}
		if (lists.isEmpty()) {
			return null;
		}
		// 「공개글448」에만 붙는 칸이라 없을 수 있다. 없으면 0 으로 본다 — 여기서는
		// "글이 지목한 적 없다" 가 맞는 해석이고, 목록에 오른 사실은 위에서 이미 잡았다.
		JsonNode mentions = truth.path("sourceCount");
		return new TruthSignalRow(storeId, List.copyOf(lists), mentions.isNumber() ? mentions.asInt() : 0);
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (!value.isString()) {
			return null;
		}
		String text = value.asString().trim();
		return text.isEmpty() ? null : text;
	}
}
