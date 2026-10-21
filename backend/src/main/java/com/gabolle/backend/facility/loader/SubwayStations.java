package com.gabolle.backend.facility.loader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 부산 지하철 역의 위치 (S15P21E201-2052). 저장소에 이미 있는 부산교통공사 역 자료({@code transit/busan-subway-network.json}, 길찾기가
 * 쓰는 것과 같은 파일)에서 읽는다.
 *
 * <h2>🔴 왜 필요한가 — 입력 파일의 역 좌표를 믿을 수 없었다</h2>
 * 물품보관함 파일(71곳)의 좌표는 「역 좌표」라고 했는데, 이 역 자료와 대 보니 <b>64곳 가운데 46곳이 300m 넘게 어긋났다</b>(다대포해수욕장은
 * 6km, 장산은 5.5km). 서로 다른 역이 같은 좌표를 나눠 쓰는 곳도 많았다(남포·자갈치, 부산·초량·부산진). 지하철 환승주차장 일부도 같은 좌표를
 * 받았다(모라역 3.7km, 남천역 2.1km). 그대로 넣으면 «내 근처 물품보관함»이 엉뚱한 곳을 가리키고 길 안내가 그리로 간다.
 *
 * <p>그래서 역에 붙은 시설의 위치는 이 역 자료의 좌표로 둔다. 역을 찾지 못하면 위치를 믿을 수 없으니 그 줄은 넣지 않는다.
 */
public final class SubwayStations {

	static final String RESOURCE = "transit/busan-subway-network.json";

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	/** 「(1호선)」 꼴의 환승 표시. */
	private static final Pattern LINE_SUFFIX = Pattern.compile("\\(\\d호선\\)");

	/**
	 * 원천 파일의 역 이름이 역 자료와 다르게 적힌 곳. 키는 {@link #normalize} 한 원천 이름, 값은 역 자료 쪽 이름이다.
	 * 「벡스코」는 2호선 「벡스코(시립미술관)」역이 역 자료에는 「시립미술관역」으로, 「국제금융센터」는 「국제금융센터·부산은행」이
	 * 「국제금융센터부산은행역」으로 적혀 있다.
	 */
	private static final Map<String, String> ALIASES = Map.of(
			"벡스코", "시립미술관",
			"국제금융센터", "국제금융센터부산은행");

	/** 역 하나. {@code line} 은 호선 숫자 글자(「1」~「4」). */
	public record Station(String stopId, String name, String line, double lat, double lng) {
	}

	private final Map<String, Station> byKey = new HashMap<>();

	private final Map<String, Station> byStopId = new HashMap<>();

	private SubwayStations() {
	}

	/** 클래스패스의 역 자료를 읽는다. */
	public static SubwayStations fromClasspath() {
		try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
			return parse(MAPPER.readTree(in));
		}
		catch (IOException failure) {
			throw new UncheckedIOException("부산 지하철 역 자료를 읽을 수 없다: " + RESOURCE, failure);
		}
	}

	/** 시험이 작은 자료로 만들 수 있게 열어 둔다. 모양: {@code {"stops":{"102":[위도,경도,"하단역","1호선",0], …}}}. */
	static SubwayStations parse(JsonNode root) {
		SubwayStations stations = new SubwayStations();
		JsonNode stops = root.path("stops");
		if (!stops.isObject()) {
			throw new IllegalStateException("역 자료에 stops 가 없다: " + RESOURCE);
		}
		for (Map.Entry<String, JsonNode> entry : stops.properties()) {
			JsonNode value = entry.getValue();
			if (!value.isArray() || value.size() < 4) {
				continue;
			}
			String line = lineDigit(value.get(3).asString());
			if (line == null) {
				continue;
			}
			Station station = new Station(entry.getKey(), value.get(2).asString(), line, value.get(0).asDouble(),
					value.get(1).asDouble());
			stations.byStopId.put(station.stopId(), station);
			stations.byKey.put(key(line, normalize(station.name())), station);
		}
		return stations;
	}

	/** 이름과 호선(「1」~「4」)으로 찾는다. 이름은 「역」·「(1호선)」을 붙여도 떼고 비교한다. */
	public Optional<Station> find(String name, String line) {
		if (name == null || line == null) {
			return Optional.empty();
		}
		String normalized = normalize(name);
		String candidate = ALIASES.getOrDefault(normalized, normalized);
		return Optional.ofNullable(this.byKey.get(key(line.trim(), candidate)));
	}

	/** 역 자료의 정류장 번호(「102」)로 찾는다. 환승주차장 번호({@code METRO-102-0})가 이 번호를 담고 있다. */
	public Optional<Station> byStopId(String stopId) {
		return Optional.ofNullable(this.byStopId.get(stopId));
	}

	/** 「서면역(1호선)」 → 「서면」. 끝의 「역」과 「(N호선)」, 공백을 뗀다. */
	static String normalize(String name) {
		String text = LINE_SUFFIX.matcher(name).replaceAll("").replaceAll("\\s+", "");
		return text.endsWith("역") && text.length() > 1 ? text.substring(0, text.length() - 1) : text;
	}

	/** 「1호선」 → 「1」. 모르는 꼴이면 {@code null}. */
	private static String lineDigit(String raw) {
		if (raw == null || raw.isEmpty() || !Character.isDigit(raw.charAt(0))) {
			return null;
		}
		return raw.substring(0, 1);
	}

	private static String key(String line, String normalizedName) {
		return line + "|" + normalizedName;
	}
}
