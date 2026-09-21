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

/**
 * 상가정보 CSV 를 덩어리로 읽는다. 한 분기 파일이 수십 MB 라 통째로 들고 있으면 적재 한 번에
 * 힙이 크게 뛴다 — 운영 서버에서 도는 일회성 작업이라 잠깐 느린 쪽이 낫다.
 *
 * <p>칸을 이름이 아니라 번호로 읽는다. 헤더의 칸 이름이 판마다 공백·괄호까지 미묘하게 다르다.
 * 대신 첫 줄의 칸 수로 아는 판인지 확인하고 다르면 멈춘다 — 확인 없이 번호로 읽으면 판이 바뀐
 * 파일에서 상호명 자리에 업종이 들어간 채로 전부 조용히 적재된다.
 */
public final class SbizCsvReader {

	/** 칸 번호. 실제 파일로 확인한 것이고 0부터 센다. */
	static final int COL_STORE_ID = 0;

	static final int COL_NAME = 1;

	static final int COL_BRANCH = 2;

	static final int COL_DAE = 4;

	static final int COL_JUNG = 6;

	/**
	 * 상권업종 소분류명. 중분류(칸 6)는 앱에 없는 낱말이라 채점이 한 건도 안 맞는다.
	 * {@link AppFoodVocabulary} 가 이 값을 앱 코드로 옮긴다.
	 */
	static final int COL_SO = 8;

	static final int COL_JIBUN_ADDR = 24;

	static final int COL_ROAD_ADDR = 31;

	static final int COL_LNG = 37;

	static final int COL_LAT = 38;

	/** 우리가 아는 판의 칸 수. 이보다 적으면 다른 파일이다. */
	static final int MIN_COLUMNS = COL_LAT + 1;

	/** 대분류가 이것인 행만 가져온다. */
	static final String FOOD_CATEGORY = "음식";

	private SbizCsvReader() {
	}

	/**
	 * 파일을 읽어 {@code chunkSize} 개씩 넘긴다. 거른 행은 세어서 돌려준다 — 얼마가 빠졌는지
	 * 모르면 나중에 "왜 그 가게가 없나" 에 답할 수 없다.
	 */
	public static Counts readFoodRows(Path csv, int chunkSize, Consumer<List<SbizRow>> chunkConsumer) {
		Counts counts = new Counts();
		List<SbizRow> chunk = new ArrayList<>(chunkSize);
		try (BufferedReader reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
			String header = reader.readLine();
			if (header == null) {
				throw new IllegalArgumentException("상가정보 CSV 가 비어 있다: " + csv);
			}
			int headerColumns = parseLine(header).size();
			if (headerColumns < MIN_COLUMNS) {
				// 조용히 진행하면 상호명 자리에 다른 값이 들어간 채로 전부 적재된다.
				throw new IllegalArgumentException("상가정보 CSV 의 칸 수가 아는 판과 다르다 — 칸 "
						+ headerColumns + "개, 최소 " + MIN_COLUMNS + "개가 필요하다: " + csv);
			}
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				counts.read++;
				List<String> cols = parseLine(line);
				if (cols.size() < MIN_COLUMNS) {
					counts.malformed++;
					continue;
				}
				if (!FOOD_CATEGORY.equals(cols.get(COL_DAE))) {
					counts.notFood++;
					continue;
				}
				Double lat = parseCoordinate(cols.get(COL_LAT));
				Double lng = parseCoordinate(cols.get(COL_LNG));
				if (lat == null || lng == null) {
					// 좌표가 없으면 후보가 될 수 없다. 0,0 도 좌표가 아니라 "빈 칸" 이다.
					counts.noCoordinate++;
					continue;
				}
				String roadAddress = cols.get(COL_ROAD_ADDR);
				String address = (roadAddress == null || roadAddress.isBlank())
						? cols.get(COL_JIBUN_ADDR) : roadAddress;
				chunk.add(new SbizRow(cols.get(COL_STORE_ID), cols.get(COL_NAME), cols.get(COL_BRANCH),
						cols.get(COL_SO), address, lat, lng));
				counts.food++;
				if (chunk.size() >= chunkSize) {
					chunkConsumer.accept(List.copyOf(chunk));
					chunk.clear();
				}
			}
		}
		catch (IOException exception) {
			throw new UncheckedIOException("상가정보 CSV 를 읽지 못했다: " + csv, exception);
		}
		if (!chunk.isEmpty()) {
			chunkConsumer.accept(List.copyOf(chunk));
		}
		return counts;
	}

	/** 0 과 빈 칸은 좌표가 아니다. */
	private static Double parseCoordinate(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		double value;
		try {
			value = Double.parseDouble(raw.trim());
		}
		catch (NumberFormatException exception) {
			return null;
		}
		if (!Double.isFinite(value) || value == 0.0) {
			return null;
		}
		return value;
	}

	/** 큰따옴표로 감싼 칸 안에 쉼표가 들어 있어도 안 깨지는 최소 CSV 파서 — 상호명에 쉼표가 있다. */
	static List<String> parseLine(String line) {
		List<String> out = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char ch = line.charAt(i);
			if (quoted) {
				if (ch == '"') {
					if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
						current.append('"');
						i++;
					}
					else {
						quoted = false;
					}
				}
				else {
					current.append(ch);
				}
			}
			else if (ch == '"') {
				quoted = true;
			}
			else if (ch == ',') {
				out.add(current.toString());
				current.setLength(0);
			}
			else {
				current.append(ch);
			}
		}
		out.add(current.toString());
		return out;
	}

	/** 읽고 거른 줄 수. 적재가 끝나면 로그에 그대로 남는다. */
	public static final class Counts {

		private long read;

		private long food;

		private long notFood;

		private long noCoordinate;

		private long malformed;

		public long read() {
			return this.read;
		}

		public long food() {
			return this.food;
		}

		public long notFood() {
			return this.notFood;
		}

		public long noCoordinate() {
			return this.noCoordinate;
		}

		public long malformed() {
			return this.malformed;
		}

		@Override
		public String toString() {
			return "읽은 줄 " + this.read + " · 음식 " + this.food + " · 음식 아님 " + this.notFood
					+ " · 좌표 없음 " + this.noCoordinate + " · 칸 모자람 " + this.malformed;
		}
	}
}
