package com.gabolle.backend.place.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 목록 근거 파일을 읽는다 — S15P21E201-826.
 *
 * <p>{@code bigData/data/staged/truth-linked.ndjson} 은 조사 대상 가게에 "미쉐린·블루리본·
 * 백년가게·택슐랭·블로그100 중 어디에 올랐는가" 를 이어 붙인 것이다. 한 줄이 가게 하나다.
 *
 * <h2>근거가 없는 줄은 버린다</h2>
 * 파일에는 목록에 하나도 안 오른 가게도 들어 있다({@code truth} 가 없거나 {@code sourceCount}
 * 가 비어 있다). 그런 줄에 0 점을 매기지 않고 <b>아예 안 넘긴다.</b> 0 은 "안 유명하다" 는
 * 관측이고 없는 것은 "안 세어 봤다" 는 무지인데, 점수기는 값이 없으면 그 축을 아예 빼고
 * 값이 0 이면 0 을 곱한다 — 둘은 다른 결과를 낸다.
 *
 * <p>읽는 방식은 {@link ResearchQueueReader} 와 같다. 한 줄이 깨져도 나머지는 넣는다.
 */
public final class TruthSignalReader {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	private TruthSignalReader() {
	}

	/** @param total 읽은 줄 · @param usable 근거가 있어 넘긴 줄 · @param skippedNoSignal 근거가 없어 버린 줄 · @param skippedBroken 형식이 깨져 버린 줄 */
	public record Counts(int total, int usable, int skippedNoSignal, int skippedBroken) {

		@Override
		public String toString() {
			return "읽은 줄 %d · 근거 있는 %d · 근거 없어 버린 %d · 형식이 깨져 버린 %d"
					.formatted(this.total, this.usable, this.skippedNoSignal, this.skippedBroken);
		}
	}

	public static Counts read(Path file, int chunkSize, Consumer<List<TruthSignalRow>> sink) {
		int total = 0;
		int usable = 0;
		int noSignal = 0;
		int broken = 0;
		List<TruthSignalRow> chunk = new ArrayList<>(chunkSize);

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
				chunk.add(row);
				if (chunk.size() >= chunkSize) {
					sink.accept(List.copyOf(chunk));
					chunk.clear();
				}
			}
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
		if (!chunk.isEmpty()) {
			sink.accept(List.copyOf(chunk));
		}
		return new Counts(total, usable, noSignal, broken);
	}

	/** 근거가 없으면 {@code null} 을 낸다. 형식이 깨졌으면 예외가 나간다 — 부르는 쪽이 둘을 가른다. */
	private static TruthSignalRow parse(String line) {
		JsonNode node = MAPPER.readTree(line);
		String storeId = text(node, "id");
		if (storeId == null) {
			throw new IllegalArgumentException("상가업소번호가 없다");
		}
		JsonNode truth = node.path("truth");
		JsonNode count = truth.path("sourceCount");
		if (!count.isNumber() || count.asInt() <= 0) {
			return null;
		}
		List<String> lists = new ArrayList<>();
		for (JsonNode name : truth.path("lists")) {
			if (name.isString() && !name.asString().isBlank()) {
				lists.add(name.asString());
			}
		}
		return new TruthSignalRow(storeId, count.asInt(), List.copyOf(lists));
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
