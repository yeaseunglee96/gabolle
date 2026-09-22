package com.gabolle.backend.place.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 축제 수집본을 읽는다. 한 줄이 축제 하나다.
 *
 * <p>기간 칸은 빈 문자열로 오는 일이 있다. 그때 오늘 날짜나 전년도 기간으로 채우지 않고
 * 넘긴다 — 날짜를 지어내면 사람이 안 열리는 축제를 보러 간다. 종료일이 시작일보다 빠른 줄도
 * 같은 이유로 넘긴다. 어느 쪽이 틀렸는지 여기서 알 수 없다.
 *
 * <p>버린 것을 갈라 세는 것은 수집본이 낡은 것인지 읽는 규칙이 틀린 것인지 가리기 위해서다.
 */
public final class FestivalPeriodReader {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	/** 관광공사가 주는 날짜 모양. {@code 20251205} 처럼 구분자가 없다. */
	private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("uuuuMMdd");

	private FestivalPeriodReader() {
	}

	public record Loaded(List<FestivalPeriodRow> rows, Counts counts) {
	}

	/**
	 * {@code skippedBroken} 은 JSON 이 깨졌거나 식별자·날짜 모양이 틀린 것,
	 * {@code skippedDuplicate} 는 같은 축제·같은 기간이 두 번 나온 것이다.
	 */
	public record Counts(int totalLines, int skippedNoPeriod, int skippedBroken, int skippedDuplicate) {

		@Override
		public String toString() {
			return "읽은 줄 %d · 넣을 %d · 기간이 없어 버린 %d · 깨진 %d · 중복이라 버린 %d".formatted(
					this.totalLines,
					this.totalLines - this.skippedNoPeriod - this.skippedBroken - this.skippedDuplicate,
					this.skippedNoPeriod, this.skippedBroken, this.skippedDuplicate);
		}
	}

	public static Loaded read(Path file) {
		List<FestivalPeriodRow> rows = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		int totalLines = 0;
		int skippedNoPeriod = 0;
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
					skippedBroken++;
					continue;
				}

				String contentId = text(node, "contentid");
				if (contentId == null) {
					skippedBroken++;
					continue;
				}

				String rawStart = text(node, "eventstartdate");
				String rawEnd = text(node, "eventenddate");
				if (rawStart == null || rawEnd == null) {
					skippedNoPeriod++;
					continue;
				}

				LocalDate startDate;
				LocalDate endDate;
				try {
					startDate = LocalDate.parse(rawStart, YYYYMMDD);
					endDate = LocalDate.parse(rawEnd, YYYYMMDD);
				}
				catch (DateTimeParseException ex) {
					skippedBroken++;
					continue;
				}
				// 어느 쪽이 틀렸는지 여기서 알 수 없다. 뒤집어 고치면 틀린 채로 화면에 뜬다.
				if (endDate.isBefore(startDate)) {
					skippedBroken++;
					continue;
				}

				if (!seen.add(contentId + "|" + startDate + "|" + endDate)) {
					skippedDuplicate++;
					continue;
				}

				rows.add(new FestivalPeriodRow(contentId, text(node, "title"), startDate, endDate));
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("축제 수집본을 읽지 못했습니다: " + file, ex);
		}

		return new Loaded(List.copyOf(rows),
				new Counts(totalLines, skippedNoPeriod, skippedBroken, skippedDuplicate));
	}

	/** 빈 문자열은 "값이 없다" 로 읽는다 — 관광공사는 모르는 칸을 {@code ""} 로 준다. */
	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			return null;
		}
		String text = value.asString().trim();
		return text.isEmpty() ? null : text;
	}
}
