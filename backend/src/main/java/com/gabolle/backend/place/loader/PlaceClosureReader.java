package com.gabolle.backend.place.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 장소↔인허가 이음 파일에서 <b>폐업 여부만</b> 뽑아 읽는다 — S15P21E201-1341.
 *
 * <p>입력은 {@code bigData/process/place-link.mjs} 의 출력이고 한 줄이 <b>이어진 가게 하나</b>다.
 *
 * <pre>
 * {"sbizId":"MA0106…","name":"쿱스토어부산","permit":{"state":"폐업","closedOn":"2016-01-11"},…}
 * </pre>
 *
 * <h2>🔴 안 이어진 가게는 이 파일에 없다</h2>
 * 그래서 여기서 읽은 것만으로 「닫았다·안 닫았다」를 말할 수 있고, <b>이 파일에 없는 장소는
 * 건드리지 않는다.</b> 해수욕장·전망대는 애초에 음식·주류 인허가가 없어 안 이어진다 — 그것을
 * 폐업으로 떨어뜨리면 멀쩡한 곳이 통째로 사라진다.
 *
 * <h2>🔴 「폐업」인데 날짜가 없으면 안 담는다</h2>
 * 상태만 믿고 날짜 없이 적으면 <b>언제 닫았는지 영영 모르는 채로</b> 남는다. 나중에 「석 달 안에
 * 닫은 곳만」 같은 판단을 하려 할 때 그 줄은 쓸 수 없고, 그때는 이미 원본과 대조할 방법이 없다.
 * 실측(2026-09-19)에서 그런 줄은 세어 남긴다 — 많으면 원본이 바뀐 것이다.
 */
public final class PlaceClosureReader {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	/** 인허가 상태 칸이 「닫았다」를 말하는 값. 원본은 「영업/정상」 아니면 이것이다. */
	private static final String CLOSED = "폐업";

	private PlaceClosureReader() {
	}

	/**
	 * @param rows 담은 줄
	 * @param closed 그중 폐업
	 * @param closedWithoutDate 🔴 「폐업」이라는데 날짜가 없어 <b>버린</b> 줄
	 * @param skipped 이음 정보나 가게 번호가 없어 버린 줄
	 */
	public record Loaded(List<PlaceClosureRow> rows, int closed, int closedWithoutDate, int skipped) {

		@Override
		public String toString() {
			return "읽음 " + this.rows.size() + " (폐업 " + this.closed + " · 날짜없는폐업 "
					+ this.closedWithoutDate + " · 버림 " + this.skipped + ")";
		}
	}

	public static Loaded read(Path file) {
		List<PlaceClosureRow> rows = new ArrayList<>();
		int closed = 0;
		int closedWithoutDate = 0;
		int skipped = 0;
		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				JsonNode node = MAPPER.readTree(line);
				String storeId = text(node.get("sbizId"));
				JsonNode permit = node.get("permit");
				if (storeId == null || permit == null || permit.isNull()) {
					skipped++;
					continue;
				}
				boolean isClosed = CLOSED.equals(text(permit.get("state")));
				LocalDate closedOn = isClosed ? date(text(permit.get("closedOn"))) : null;
				if (isClosed && closedOn == null) {
					// 🔴 상태만 믿고 날짜 없이 적지 않는다. 버리고 센다.
					closedWithoutDate++;
					continue;
				}
				if (isClosed) {
					closed++;
				}
				rows.add(new PlaceClosureRow(storeId, closedOn));
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("이음 파일을 읽지 못했다: " + file.toAbsolutePath(), ex);
		}
		return new Loaded(rows, closed, closedWithoutDate, skipped);
	}

	private static String text(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		String value = node.asString().trim();
		return value.isEmpty() ? null : value;
	}

	/** 🔴 못 읽는 날짜는 {@code null} 이다 — 오늘로 채우거나 지어내지 않는다. */
	private static LocalDate date(String value) {
		if (value == null) {
			return null;
		}
		try {
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException notADate) {
			return null;
		}
	}
}
