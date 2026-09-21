package com.gabolle.backend.place.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * bigData 가 받아 둔 OSM 부산 추출본({@code data/raw/pbf/poi.ndjson})을 읽는다.
 *
 * <p>원본을 그대로 읽는 것은 {@code TourApiPlaceReader} 와 같다. 중간 가공본을 따로 두면 그
 * 파일이 낡았는지를 아무도 모르고, 여기서 하는 일은 「태그를 갈래로 옮기는 것」 하나뿐이라
 * 가공할 것이 없다.
 *
 * <p>🔴 <b>버리는 줄이 많고, 왜 버렸는지를 센다.</b> {@link Counts} 가 그 숫자다. 합계만 남기면
 * 「10,266곳이 들어갈 줄 알았는데 3,000곳만 들어갔다」를 볼 때 이름이 없어서인지 갈래를 몰라서인지
 * 알 수가 없다.
 *
 * <p>한 줄은 이렇게 생겼다.
 * <pre>
 * {"type":"node","id":368601281,"lat":35.159148,"lon":129.107055,
 *  "tags":{"name":"부산게스트하우스","tourism":"guest_house", …}}
 * </pre>
 */
public final class OsmPoiReader {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * 주소를 이어 붙일 때 쓰는 태그와 차례. 한국 주소는 큰 단위부터다.
	 * <p>
	 * 🔴 <b>없는 조각을 채우지 않는다.</b> 있는 것만 이어 붙이고, 하나도 없으면 주소는
	 * {@code null} 이다. 13,003곳 중 주소 태그가 있는 것은 1,189곳뿐이라 이 경우가 보통이다.
	 */
	private static final List<String> ADDRESS_PARTS =
			List.of("addr:province", "addr:city", "addr:district", "addr:street", "addr:housenumber");

	private OsmPoiReader() {
	}

	/**
	 * 읽은 줄의 내역.
	 *
	 * @param total 파일의 줄 수
	 * @param noName 이름이 없어 버린 줄. 검색도 표시도 안 되는 장소라 넣지 않는다
	 * @param noCategory 아는 갈래가 없어 버린 줄 — 은행·주유소·유치원 같은 것들
	 * @param noCoordinates 좌표가 없어 버린 줄. {@code 0} 으로 채우지 않는다
	 * @param taken 실제로 넘긴 줄
	 */
	public record Counts(int total, int noName, int noCategory, int noCoordinates, int taken) {

		@Override
		public String toString() {
			return "읽은 줄 %d · 넘긴 곳 %d (이름 없음 %d · 갈래 모름 %d · 좌표 없음 %d)"
					.formatted(this.total, this.taken, this.noName, this.noCategory, this.noCoordinates);
		}
	}

	/**
	 * 파일을 한 줄씩 읽어 덩어리로 넘긴다. 전부를 메모리에 올리지 않는다.
	 *
	 * @param chunkSize 한 번에 넘길 줄 수
	 * @param chunkConsumer 덩어리를 받아 저장하는 쪽
	 */
	public static Counts read(Path file, int chunkSize, Consumer<List<OsmPoiRow>> chunkConsumer) {
		int total = 0;
		int noName = 0;
		int noCategory = 0;
		int noCoordinates = 0;
		int taken = 0;
		List<OsmPoiRow> chunk = new ArrayList<>(chunkSize);

		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				total++;
				JsonNode node = MAPPER.readTree(line);
				Map<String, String> tags = tagsOf(node.get("tags"));

				String name = tags.get("name");
				if (name == null || name.isBlank()) {
					noName++;
					continue;
				}
				String category = OsmPlaceCategory.of(tags);
				if (category == null) {
					noCategory++;
					continue;
				}
				JsonNode lat = node.get("lat");
				JsonNode lon = node.get("lon");
				if (lat == null || lon == null || !lat.isNumber() || !lon.isNumber()) {
					noCoordinates++;
					continue;
				}

				chunk.add(new OsmPoiRow(node.path("id").asLong(), name.strip(), category, addressOf(tags),
						lat.asDouble(), lon.asDouble()));
				taken++;
				if (chunk.size() >= chunkSize) {
					chunkConsumer.accept(List.copyOf(chunk));
					chunk.clear();
				}
			}
		}
		catch (IOException failure) {
			throw new UncheckedIOException("OSM 장소 파일을 읽지 못했다: " + file, failure);
		}
		if (!chunk.isEmpty()) {
			chunkConsumer.accept(List.copyOf(chunk));
		}
		return new Counts(total, noName, noCategory, noCoordinates, taken);
	}

	private static Map<String, String> tagsOf(JsonNode tags) {
		if (tags == null || !tags.isObject()) {
			return Map.of();
		}
		Map<String, String> read = new LinkedHashMap<>();
		tags.fields().forEachRemaining((entry) -> {
			if (entry.getValue() != null && entry.getValue().isTextual()) {
				read.put(entry.getKey(), entry.getValue().asText());
			}
		});
		return read;
	}

	/** 있는 조각만 이어 붙인다. 하나도 없으면 {@code null} — 「주소 모름」이다. */
	private static String addressOf(Map<String, String> tags) {
		StringBuilder address = new StringBuilder();
		for (String part : ADDRESS_PARTS) {
			String value = tags.get(part);
			if (value == null || value.isBlank()) {
				continue;
			}
			if (!address.isEmpty()) {
				address.append(' ');
			}
			address.append(value.strip());
		}
		return address.isEmpty() ? null : address.toString();
	}
}
